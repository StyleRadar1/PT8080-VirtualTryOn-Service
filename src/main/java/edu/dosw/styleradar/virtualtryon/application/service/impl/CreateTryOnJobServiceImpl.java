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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
 * el trabajo, la auditoría y el evento tryon.job.created en la tabla outbox_events dentro de la misma
 * transacción JDBC (Transactional Outbox), eliminando problemas de consistencia Dual-Write.
 */
@Service
public class CreateTryOnJobServiceImpl implements CreateTryOnJobService {

    private static final Logger log = LoggerFactory.getLogger(CreateTryOnJobServiceImpl.class);

    private final TryOnJobRepositoryPort jobRepositoryPort;
    private final UserQuotaRepositoryPort quotaRepositoryPort;
    private final UserQuotaCachePort quotaCachePort;
    private final BiometricDataRepositoryPort biometricDataRepositoryPort;
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
            List<AiInferenceStrategy> inferenceStrategies,
            @Value("${styleradar.tryon.ai-provider:fashn}") String configuredProviderName,
            TransactionTemplate transactionTemplate,
            OutboxPublisherPort outboxPublisherPort
    ) {
        this.jobRepositoryPort = Objects.requireNonNull(jobRepositoryPort, "jobRepositoryPort no puede ser nulo");
        this.quotaRepositoryPort = Objects.requireNonNull(quotaRepositoryPort, "quotaRepositoryPort no puede ser nulo");
        this.quotaCachePort = Objects.requireNonNull(quotaCachePort, "quotaCachePort no puede ser nulo");
        this.biometricDataRepositoryPort = Objects.requireNonNull(biometricDataRepositoryPort, "biometricDataRepositoryPort no puede ser nulo");
        this.inferenceStrategies = inferenceStrategies != null ? inferenceStrategies : List.of();
        this.configuredProvider = AiProvider.fromConfigValue(configuredProviderName);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate es obligatorio para garantizar atomicidad transaccional");
        this.outboxPublisherPort = Objects.requireNonNull(outboxPublisherPort, "outboxPublisherPort es obligatorio para garantizar consistencia Dual-Write (Transactional Outbox)");

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
        // Validar tempranamente que el proveedor configurado cuente con una estrategia soportada registrada
        selectStrategy(configuredProvider);

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

        // 4. Creación atómica del TryOnJob, consumo de cuota y persistencia en Outbox dentro de la misma transacción JDBC
        int[] updatedUsedCount = new int[1];
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
            updatedUsedCount[0] = quota.getUsedCount();

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

            // Persistencia obligatoria del evento en el Outbox en la misma transacción PostgreSQL
            outboxPublisherPort.saveOutboxEvent(saved.getId(), "tryon.job.created", TryOnJobDomainEvent.fromJob(saved));
            return saved;
        });

        // 5. Actualización de cuota en Redis estrictamente POST-COMMIT de PostgreSQL
        // Evita inconsistencias de saldo o créditos cuando la persistencia del trabajo o del Outbox falla
        syncQuotaCachePostCommit(userId, currentPeriod, updatedUsedCount[0]);

        // 6. Despacho asíncrono inmediato opcional (si el broker falla, el Outbox periódico garantiza la entrega At-Least-Once)
        try {
            outboxPublisherPort.publishPendingEvents();
        } catch (Exception ex) {
            log.warn("Fallo temporal al despachar evento AMQP inmediatamente. El scheduler de Outbox garantizará la entrega: {}",
                    ex.getMessage());
        }

        return savedQueuedJob;
    }

    private void syncQuotaCachePostCommit(UserId userId, BillingPeriod period, int usedCount) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeUpdateQuotaCache(userId, period, usedCount);
                }
            });
        } else {
            safeUpdateQuotaCache(userId, period, usedCount);
        }
    }

    private void safeUpdateQuotaCache(UserId userId, BillingPeriod period, int usedCount) {
        try {
            quotaCachePort.cacheUsedCount(userId, period, usedCount);
            log.debug("Caché de cuota en Redis actualizada exitosamente post-commit para usuario {}: {}",
                    userId.value(), usedCount);
        } catch (Exception ex) {
            log.warn("Fallo no bloqueante al actualizar cuota en Redis post-commit para usuario {}: {}",
                    userId.value(), ex.getMessage());
        }
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    AiInferenceStrategy selectStrategy(AiProvider provider) {
        return inferenceStrategies.stream()
                .filter(strategy -> strategy.supportsProvider(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No existe una estrategia configurada para el proveedor: " + provider));
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
