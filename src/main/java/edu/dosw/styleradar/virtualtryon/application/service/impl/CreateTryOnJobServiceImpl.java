package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.CreateTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

/**
 * Implementación del servicio de aplicación que orquesta el flujo completo de creación y
 * generación de una prueba virtual con IA (SR-RF-25):
 * 1. Valida la cadena de responsabilidad (Consentimiento V01 -> Cuota Mensual V02 -> Prenda Compatible V03).
 * 2. Construye el agregado TryOnJob mediante TryOnJobBuilder en estado QUEUED y registra JobAuditLog.
 * 3. Consume 1 crédito de la cuota mensual en PostgreSQL y Redis.
 * 4. Publica el evento tryon.job.created en RabbitMQ.
 * 5. Ejecuta la estrategia de inferencia (FashnAiStrategy o MockAiStrategy), actualiza el job a COMPLETED
 *    o FAILED, registra la transición en JobAuditLog y publica tryon.job.completed / tryon.job.failed.
 */
@Service
@Transactional
public class CreateTryOnJobServiceImpl implements CreateTryOnJobService {

    private final TryOnJobRepositoryPort jobRepositoryPort;
    private final UserQuotaRepositoryPort quotaRepositoryPort;
    private final UserQuotaCachePort quotaCachePort;
    private final BiometricDataRepositoryPort biometricDataRepositoryPort;
    private final TryOnEventPublisherPort eventPublisherPort;
    private final List<AiInferenceStrategy> inferenceStrategies;
    private final AiProvider configuredProvider;
    private final TryOnValidatorHandler validationChain;

    public CreateTryOnJobServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnEventPublisherPort eventPublisherPort,
            List<AiInferenceStrategy> inferenceStrategies,
            @Value("${styleradar.tryon.ai-provider:mock}") String configuredProviderName
    ) {
        this.jobRepositoryPort = jobRepositoryPort;
        this.quotaRepositoryPort = quotaRepositoryPort;
        this.quotaCachePort = quotaCachePort;
        this.biometricDataRepositoryPort = biometricDataRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.inferenceStrategies = inferenceStrategies;
        this.configuredProvider = AiProvider.fromConfigValue(configuredProviderName);

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
        boolean hasPersistedConsent = biometricDataRepositoryPort.findConsentByUserId(userId)
                .map(BiometricConsent::isValidForInference)
                .orElse(false);
        boolean effectiveConsent = biometricConsent || hasPersistedConsent;

        BillingPeriod currentPeriod = BillingPeriod.current();
        UserQuota quota = resolveCurrentQuota(userId, currentPeriod);

        CatalogGarment garment = quotaRepositoryPort.findCatalogGarmentById(garmentId)
                .orElseGet(() -> new CatalogGarment(
                        garmentId,
                        "Prenda Catálogo " + garmentId.value(),
                        GarmentCategory.TOPS,
                        "https://styleradar.dosw.edu/catalog/garments/" + garmentId.value() + ".png",
                        true,
                        Instant.now()
                ));

        TryOnValidationContext validationContext = new TryOnValidationContext(
                userId,
                garment.getGarmentId(),
                garment.getCategory(),
                garment.getImageUrl(),
                garment.isVisible(),
                effectiveConsent,
                quota
        );
        validationChain.validate(validationContext);

        quota.consumeQuota();
        quotaRepositoryPort.saveQuota(quota);
        quotaCachePort.cacheUsedCount(userId, currentPeriod, quota.getUsedCount());

        TryOnJob queuedJob = new TryOnJobBuilder()
                .withUserId(userId)
                .withGarmentId(garmentId)
                .withPhotoUrl(photoUrl)
                .withCategory(garment.getCategory())
                .withProvider(configuredProvider)
                .build();

        TryOnJob savedQueuedJob = jobRepositoryPort.saveJob(queuedJob);
        jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                savedQueuedJob.getId(),
                null,
                JobStatus.QUEUED,
                "Trabajo de prueba virtual creado y encolado tras superar validaciones V01..V03"
        ));
        eventPublisherPort.publishJobEvent(TryOnJobDomainEvent.fromJob(savedQueuedJob));

        long startNanos = System.nanoTime();
        savedQueuedJob.startAiProcessing();
        jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                savedQueuedJob.getId(),
                JobStatus.QUEUED,
                JobStatus.PROCESSING,
                "Iniciando procesamiento de inferencia IA con proveedor " + configuredProvider
        ));

        AiInferenceStrategy selectedStrategy = selectStrategy(configuredProvider);
        try {
            SasSignedUrl renderUrl = selectedStrategy.submitTryOn(savedQueuedJob);
            long elapsedMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
            savedQueuedJob.complete(renderUrl, elapsedMs);
            TryOnJob completedJob = jobRepositoryPort.saveJob(savedQueuedJob);
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    completedJob.getId(),
                    JobStatus.PROCESSING,
                    JobStatus.COMPLETED,
                    "Renderizado de prueba virtual completado exitosamente en " + elapsedMs + " ms"
            ));
            eventPublisherPort.publishJobEvent(TryOnJobDomainEvent.fromJob(completedJob));
            return completedJob;
        } catch (RuntimeException ex) {
            savedQueuedJob.markFailed(ex.getMessage());
            TryOnJob failedJob = jobRepositoryPort.saveJob(savedQueuedJob);
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    failedJob.getId(),
                    JobStatus.PROCESSING,
                    JobStatus.FAILED,
                    "Error durante la generación de prueba virtual: " + failedJob.getErrorMessage()
            ));
            eventPublisherPort.publishJobEvent(TryOnJobDomainEvent.fromJob(failedJob));
            throw ex;
        }
    }

    private UserQuota resolveCurrentQuota(UserId userId, BillingPeriod currentPeriod) {
        UserQuota quota = quotaRepositoryPort.findQuotaByUserIdAndPeriod(userId, currentPeriod)
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
        return quota;
    }

    private AiInferenceStrategy selectStrategy(AiProvider provider) {
        return inferenceStrategies.stream()
                .filter(strategy -> strategy.supportsProvider(provider))
                .findFirst()
                .orElseGet(() -> inferenceStrategies.get(0));
    }
}

