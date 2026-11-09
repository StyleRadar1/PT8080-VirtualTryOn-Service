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
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
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
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Implementación del servicio de aplicación que orquesta el flujo completo de creación y
 * generación de una prueba virtual con IA (SR-RF-25):
 * 1. Valida el consentimiento biométrico informado (Ley 1581 de 2012) y la titularidad de la silueta.
 * 2. Valida la existencia real y compatibilidad de la prenda del catálogo.
 * 3. Ejecuta la cadena de validación (Consentimiento V01 -> Cuota Mensual V02 -> Prenda Compatible V03).
 * 4. Persiste de forma atómica en PostgreSQL el TryOnJob en estado QUEUED y descuenta la cuota.
 * 5. Ejecuta la inferencia fuera de transacciones JDBC para no retener conexiones durante I/O HTTP.
 * 6. Garantiza la persistencia confirmada de estados COMPLETED o FAILED junto con su auditoría y eventos AMQP.
 */
@Service
public class CreateTryOnJobServiceImpl implements CreateTryOnJobService {

    private final TryOnJobRepositoryPort jobRepositoryPort;
    private final UserQuotaRepositoryPort quotaRepositoryPort;
    private final UserQuotaCachePort quotaCachePort;
    private final BiometricDataRepositoryPort biometricDataRepositoryPort;
    private final TryOnEventPublisherPort eventPublisherPort;
    private final List<AiInferenceStrategy> inferenceStrategies;
    private final AiProvider configuredProvider;
    private final TryOnValidatorHandler validationChain;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public CreateTryOnJobServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnEventPublisherPort eventPublisherPort,
            List<AiInferenceStrategy> inferenceStrategies,
            @Value("${styleradar.tryon.ai-provider:fashn}") String configuredProviderName,
            ObjectProvider<TransactionTemplate> transactionTemplateProvider
    ) {
        this.jobRepositoryPort = jobRepositoryPort;
        this.quotaRepositoryPort = quotaRepositoryPort;
        this.quotaCachePort = quotaCachePort;
        this.biometricDataRepositoryPort = biometricDataRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.inferenceStrategies = inferenceStrategies;
        this.configuredProvider = AiProvider.fromConfigValue(configuredProviderName);
        this.transactionTemplate = transactionTemplateProvider != null ? transactionTemplateProvider.getIfAvailable() : null;

        TryOnValidatorHandler consentValidator = new BiometricConsentValidator();
        TryOnValidatorHandler quotaValidator = new MonthlyUserQuotaValidator();
        TryOnValidatorHandler garmentValidator = new GarmentCategoryValidator();
        consentValidator.setNext(quotaValidator).setNext(garmentValidator);
        this.validationChain = consentValidator;
    }

    public CreateTryOnJobServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnEventPublisherPort eventPublisherPort,
            List<AiInferenceStrategy> inferenceStrategies,
            String configuredProviderName
    ) {
        this(jobRepositoryPort, quotaRepositoryPort, quotaCachePort, biometricDataRepositoryPort, eventPublisherPort, inferenceStrategies, configuredProviderName, null);
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

        String userUuidStr = userId.value().toString();
        boolean belongsToUser = photoUrl.contains(userUuidStr)
                || (userPhoto.getBlobPath() != null && photoUrl.contains(userPhoto.getBlobPath()));
        if (!belongsToUser) {
            throw new InvalidPhotoException(
                    "La fotografía proporcionada no corresponde a la silueta biométrica del usuario autenticado");
        }

        String effectivePhotoUrl = (userPhoto.getPhotoUrl() != null && userPhoto.getPhotoUrl().value() != null)
                ? userPhoto.getPhotoUrl().value()
                : photoUrl.trim();

        // 3. Validación de existencia real de la prenda en el catálogo
        CatalogGarment garment = quotaRepositoryPort.findCatalogGarmentById(garmentId)
                .orElseThrow(() -> new InvalidGarmentException(
                        "La prenda seleccionada con ID " + garmentId.value() + " no existe en el catálogo"));

        BillingPeriod currentPeriod = BillingPeriod.current();

        // 4. Creación atómica del TryOnJob y consumo concurrente seguro con bloqueo pesimista en PostgreSQL
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
            return saved;
        });

        // 5. Publicación asíncrona en RabbitMQ para que el consumidor desacoplado ejecute la inferencia
        eventPublisherPort.publishJobEvent(TryOnJobDomainEvent.fromJob(savedQueuedJob));
        return savedQueuedJob;
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    private AiInferenceStrategy selectStrategy(AiProvider provider) {
        return inferenceStrategies.stream()
                .filter(strategy -> strategy.supportsProvider(provider))
                .findFirst()
                .orElse(null);
    }
}
