package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.CreateTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.OutboxPublisherPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnEventPublisherPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserQuotaCachePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserQuotaRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.strategy.AiInferenceStrategy;
import edu.dosw.styleradar.virtualtryon.domain.validation.BiometricConsentValidator;
import edu.dosw.styleradar.virtualtryon.domain.validation.GarmentCategoryValidator;
import edu.dosw.styleradar.virtualtryon.domain.validation.MonthlyUserQuotaValidator;
import edu.dosw.styleradar.virtualtryon.domain.validation.TryOnValidationContext;
import edu.dosw.styleradar.virtualtryon.domain.validation.TryOnValidatorHandler;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Servicio de aplicación para la creación y orquestación inicial de trabajos de Virtual Try-On (SR-RF-25).
 * Valida la cadena V01..V03, descuenta la cuota con bloqueo pesimista en PostgreSQL, persiste atómicamente
 * el evento mediante Transactional Outbox y despacha a RabbitMQ.
 */
@Service
public class CreateTryOnJobServiceImpl implements CreateTryOnJobService {

    private static final Logger log = LoggerFactory.getLogger(CreateTryOnJobServiceImpl.class);

    private final TryOnJobRepositoryPort jobRepositoryPort;
    private final UserQuotaRepositoryPort quotaRepositoryPort;
    private final UserQuotaCachePort quotaCachePort;
    private final BiometricDataRepositoryPort biometricDataRepositoryPort;
    private final TryOnEventPublisherPort eventPublisherPort;
    private final List<AiInferenceStrategy> inferenceStrategies;
    private final AiProvider configuredProvider;
    private final TryOnValidatorHandler validationChain;
    private final TransactionTemplate transactionTemplate;
    private final OutboxPublisherPort outboxPublisherPort;

    @Autowired
    public CreateTryOnJobServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnEventPublisherPort eventPublisherPort,
            List<AiInferenceStrategy> inferenceStrategies,
            @Value("${styleradar.tryon.ai-provider:fashn}") String configuredProviderName,
            TransactionTemplate transactionTemplate,
            @Autowired(required = false) OutboxPublisherPort outboxPublisherPort
    ) {
        this.jobRepositoryPort = Objects.requireNonNull(jobRepositoryPort, "jobRepositoryPort no puede ser nulo");
        this.quotaRepositoryPort = Objects.requireNonNull(quotaRepositoryPort, "quotaRepositoryPort no puede ser nulo");
        this.quotaCachePort = Objects.requireNonNull(quotaCachePort, "quotaCachePort no puede ser nulo");
        this.biometricDataRepositoryPort = Objects.requireNonNull(biometricDataRepositoryPort, "biometricDataRepositoryPort no puede ser nulo");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort no puede ser nulo");
        this.inferenceStrategies = inferenceStrategies != null ? inferenceStrategies : List.of();
        this.configuredProvider = AiProvider.fromConfigValue(configuredProviderName);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate es obligatorio para garantizar atomicidad transaccional");
        this.outboxPublisherPort = outboxPublisherPort;

        TryOnValidatorHandler consentValidator = new BiometricConsentValidator();
        TryOnValidatorHandler quotaValidator = new MonthlyUserQuotaValidator();
        TryOnValidatorHandler garmentValidator = new GarmentCategoryValidator();
        consentValidator.setNext(quotaValidator).setNext(garmentValidator);
        this.validationChain = consentValidator;
    }

    @Override
    public TryOnJob createTryOnJob(
            UserId userId,
            GarmentId garmentId,
            String photoUrl,
            boolean biometricConsent
    ) {
        // 1. Verificación estricta de consentimiento biométrico explícito y registrado (Ley 1581 de 2012)
        if (!biometricConsent) {
            throw new ConsentRequiredException(
                    "Debe confirmar explícitamente el consentimiento biométrico para iniciar la prueba virtual");
        }
        boolean hasActivePersistedConsent = biometricDataRepositoryPort.findConsentByUserId(userId)
                .map(BiometricConsent::isValidForInference)
                .orElse(false);
        if (!hasActivePersistedConsent) {
            throw new ConsentRequiredException(
                    "El usuario " + userId.value() + " no cuenta con consentimiento biométrico activo registrado");
        }

        // 2. Verificación de existencia y titularidad de la fotografía frontal de silueta del usuario
        BiometricPhoto userPhoto = biometricDataRepositoryPort.findPhotoByUserId(userId)
                .orElseThrow(() -> new InvalidPhotoException(
                        "El usuario " + userId.value() + " no tiene una fotografía frontal de silueta registrada y validada"));

        if (photoUrl == null || photoUrl.isBlank()) {
            throw new InvalidPhotoException("La URL de la fotografía frontal es obligatoria");
        }

        if (!isPhotoOwnedByUser(photoUrl, userPhoto, userId)) {
            throw new InvalidPhotoException(
                    "La fotografía proporcionada no corresponde a la silueta biométrica del usuario autenticado");
        }

        String effectivePhotoUrl = (photoUrl != null && !photoUrl.isBlank())
                ? photoUrl.trim()
                : ((userPhoto.getPhotoUrl() != null) ? userPhoto.getPhotoUrl().value() : null);

        // 3. Validación de existencia real de la prenda en el catálogo
        CatalogGarment garment = quotaRepositoryPort.findCatalogGarmentById(garmentId)
                .orElseThrow(() -> new InvalidGarmentException(
                        "La prenda seleccionada con ID " + garmentId.value() + " no existe en el catálogo"));

        BillingPeriod currentPeriod = BillingPeriod.current();

        // 4. Creación atómica del TryOnJob, consumo concurrente seguro con bloqueo pesimista y persistencia Transactional Outbox
        TryOnJob savedQueuedJob = executeInTransaction(() -> {
            UserQuota quota = quotaRepositoryPort.findQuotaByUserIdAndPeriodForUpdate(userId, currentPeriod)
                    .orElseGet(() -> quotaRepositoryPort.saveQuota(UserQuota.createDefault(userId, currentPeriod)));

            OptionalInt cachedUsed = quotaCachePort.getCachedUsedCount(userId, currentPeriod);
            if (cachedUsed.isPresent() && cachedUsed.getAsInt() > quota.getUsedCount()) {
                quota = new UserQuota(
                        quota.getId(),
                        quota.getUserId(),
                        quota.getMonthlyLimit(),
                        cachedUsed.getAsInt(),
                        quota.getBillingPeriod(),
                        quota.isRadarPlus(),
                        quota.getLastResetAt()
                );
            }

            TryOnValidationContext validationContext = new TryOnValidationContext(
                    userId,
                    garment.getGarmentId(),
                    garment.getCategory(),
                    garment.getImageUrl(),
                    garment.isVisible(),
                    true,
                    quota
            );
            validationChain.validate(validationContext);

            quota.consumeQuota();
            quotaRepositoryPort.saveQuota(quota);
            quotaCachePort.cacheUsedCount(userId, currentPeriod, quota.getUsedCount());

            TryOnJob queuedJob = new TryOnJobBuilder()
                    .withUserId(userId)
                    .withGarmentId(garmentId)
                    .withPhotoUrl(effectivePhotoUrl)
                    .withCategory(garment.getCategory())
                    .withGarmentImageUrl(garment.getImageUrl())
                    .withProvider(configuredProvider)
                    .build();

            TryOnJob saved = jobRepositoryPort.saveJob(queuedJob);
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    saved.getId(),
                    null,
                    JobStatus.QUEUED,
                    "Trabajo de prueba virtual creado y encolado tras superar validaciones V01..V03"
            ));

            if (outboxPublisherPort != null) {
                outboxPublisherPort.saveOutboxEvent(saved.getId(), "tryon.job.created", TryOnJobDomainEvent.fromJob(saved));
            }
            return saved;
        });

        // 5. Publicación asíncrona en RabbitMQ
        try {
            if (outboxPublisherPort != null) {
                outboxPublisherPort.publishPendingEvents();
            } else {
                eventPublisherPort.publishJobEvent(TryOnJobDomainEvent.fromJob(savedQueuedJob));
            }
        } catch (RuntimeException ex) {
            if (outboxPublisherPort == null) {
                compensateFailedPublication(savedQueuedJob, userId, currentPeriod, ex.getMessage());
                throw ex;
            }
            log.warn("Fallo temporal al despachar evento AMQP inmediatamente. El evento Outbox garantizara la entrega: {}", ex.getMessage());
        }
        return savedQueuedJob;
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private void compensateFailedPublication(TryOnJob job, UserId userId, BillingPeriod period, String errorReason) {
        executeInTransaction(() -> {
            quotaRepositoryPort.findQuotaByUserIdAndPeriodForUpdate(userId, period).ifPresent(quota -> {
                quota.refundQuota();
                quotaRepositoryPort.saveQuota(quota);
                quotaCachePort.cacheUsedCount(userId, period, quota.getUsedCount());
            });

            job.markFailed("Compensacion: fallo al encolar en broker RabbitMQ (" + errorReason + ")");
            jobRepositoryPort.saveJob(job);
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    job.getId(),
                    JobStatus.QUEUED,
                    JobStatus.FAILED,
                    "Compensacion: cuota reintegrada tras fallo en broker de mensajeria: " + errorReason
            ));
            return null;
        });
    }

    private AiInferenceStrategy selectStrategy(AiProvider provider) {
        return inferenceStrategies.stream()
                .filter(strategy -> strategy.supportsProvider(provider))
                .findFirst()
                .orElse(null);
    }

    private boolean isPhotoOwnedByUser(String candidatePhotoUrl, BiometricPhoto userPhoto, UserId userId) {
        if (candidatePhotoUrl == null || candidatePhotoUrl.isBlank() || userPhoto == null || userId == null) {
            return false;
        }
        String expectedBlobPath = userPhoto.getBlobPath();
        if (expectedBlobPath == null || expectedBlobPath.isBlank()) {
            return false;
        }
        String expectedUserPrefix = userId.value().toString() + "/";
        if (!expectedBlobPath.startsWith(expectedUserPrefix)) {
            return false;
        }
        try {
            String decodedUrl = URLDecoder.decode(candidatePhotoUrl.trim(), StandardCharsets.UTF_8);
            if (decodedUrl.contains("..")) {
                return false; // Prevenir cualquier vector de directory traversal
            }
            URI uri = URI.create(decodedUrl).normalize();
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = decodedUrl;
            }
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            int firstSlash = path.indexOf('/');
            String pathWithoutContainer = (firstSlash >= 0) ? path.substring(firstSlash + 1) : path;
            while (pathWithoutContainer.endsWith("/")) {
                pathWithoutContainer = pathWithoutContainer.substring(0, pathWithoutContainer.length() - 1);
            }

            return expectedBlobPath.equalsIgnoreCase(path)
                    || expectedBlobPath.equalsIgnoreCase(pathWithoutContainer);
        } catch (Exception ex) {
            return false;
        }
    }
}
