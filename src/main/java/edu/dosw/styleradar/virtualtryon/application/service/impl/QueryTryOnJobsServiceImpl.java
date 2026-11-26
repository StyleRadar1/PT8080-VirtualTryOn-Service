package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotEligibleException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentSizeUnavailableException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobAccessDeniedException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.TryOnSelectionIncompleteException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentSizeChart;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentValidationResult;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnOutfit;
import edu.dosw.styleradar.virtualtryon.domain.model.UserBodyMeasurements;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.CatalogGarmentLookupPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.GarmentSizeMeasurementPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserMeasurementPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserQuotaCachePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserQuotaRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.validation.BiometricConsentValidator;
import edu.dosw.styleradar.virtualtryon.domain.validation.GarmentSelectionPolicy;
import edu.dosw.styleradar.virtualtryon.domain.validation.GarmentSizeFitPolicy;
import edu.dosw.styleradar.virtualtryon.domain.validation.MonthlyUserQuotaValidator;
import edu.dosw.styleradar.virtualtryon.domain.validation.TryOnValidationContext;
import edu.dosw.styleradar.virtualtryon.domain.validation.TryOnValidatorHandler;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Implementación del servicio de aplicación para la consulta y sincronización de cuotas mensuales,
 * validación de prendas compatibles del catálogo, consulta de trabajos y purga biométrica (SR-RF-23 a SR-RF-26).
 */
@Service
@Transactional
public class QueryTryOnJobsServiceImpl implements QueryTryOnJobsService {

    private static final Logger log = LoggerFactory.getLogger(QueryTryOnJobsServiceImpl.class);

    private final UserQuotaRepositoryPort quotaRepositoryPort;
    private final UserQuotaCachePort quotaCachePort;
    private final BiometricDataRepositoryPort biometricDataRepositoryPort;
    private final TryOnJobRepositoryPort jobRepositoryPort;
    private final BlobStoragePort blobStoragePort;
    private final UserMeasurementPort userMeasurementPort;
    private final GarmentSizeMeasurementPort garmentSizeMeasurementPort;
    private final CatalogGarmentLookupPort catalogGarmentLookupPort;
    private final TryOnValidatorHandler eligibilityValidationChain;
    private Supplier<BillingPeriod> billingPeriodSupplier = BillingPeriod::current;

    @Autowired
    public QueryTryOnJobsServiceImpl(
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnJobRepositoryPort jobRepositoryPort,
            @Autowired(required = false) BlobStoragePort blobStoragePort,
            @Autowired(required = false) UserMeasurementPort userMeasurementPort,
            @Autowired(required = false) GarmentSizeMeasurementPort garmentSizeMeasurementPort,
            @Autowired(required = false) CatalogGarmentLookupPort catalogGarmentLookupPort
    ) {
        this.quotaRepositoryPort = quotaRepositoryPort;
        this.quotaCachePort = quotaCachePort;
        this.biometricDataRepositoryPort = biometricDataRepositoryPort;
        this.jobRepositoryPort = jobRepositoryPort;
        this.blobStoragePort = blobStoragePort;
        this.userMeasurementPort = userMeasurementPort != null ? userMeasurementPort : u -> Optional.empty();
        this.garmentSizeMeasurementPort = garmentSizeMeasurementPort != null ? garmentSizeMeasurementPort : g -> Optional.empty();
        this.catalogGarmentLookupPort = catalogGarmentLookupPort != null ? catalogGarmentLookupPort : g -> Optional.empty();

        TryOnValidatorHandler consentValidator = new BiometricConsentValidator();
        TryOnValidatorHandler quotaValidator = new MonthlyUserQuotaValidator();
        consentValidator.setNext(quotaValidator);
        this.eligibilityValidationChain = consentValidator;
    }

    public QueryTryOnJobsServiceImpl(
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnJobRepositoryPort jobRepositoryPort,
            BlobStoragePort blobStoragePort,
            UserMeasurementPort userMeasurementPort,
            GarmentSizeMeasurementPort garmentSizeMeasurementPort
    ) {
        this(
                quotaRepositoryPort,
                quotaCachePort,
                biometricDataRepositoryPort,
                jobRepositoryPort,
                blobStoragePort,
                userMeasurementPort,
                garmentSizeMeasurementPort,
                null
        );
    }

    public QueryTryOnJobsServiceImpl(
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnJobRepositoryPort jobRepositoryPort,
            BlobStoragePort blobStoragePort,
            UserMeasurementPort userMeasurementPort
    ) {
        this(quotaRepositoryPort, quotaCachePort, biometricDataRepositoryPort, jobRepositoryPort, blobStoragePort, userMeasurementPort, null);
    }

    public QueryTryOnJobsServiceImpl(
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnJobRepositoryPort jobRepositoryPort,
            BlobStoragePort blobStoragePort
    ) {
        this(quotaRepositoryPort, quotaCachePort, biometricDataRepositoryPort, jobRepositoryPort, blobStoragePort, null, null);
    }

    public QueryTryOnJobsServiceImpl(
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnJobRepositoryPort jobRepositoryPort
    ) {
        this(quotaRepositoryPort, quotaCachePort, biometricDataRepositoryPort, jobRepositoryPort, null, null, null);
    }

    public QueryTryOnJobsServiceImpl(
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort
    ) {
        this(quotaRepositoryPort, quotaCachePort, biometricDataRepositoryPort, null, null, null);
    }

    void setBillingPeriodSupplier(Supplier<BillingPeriod> billingPeriodSupplier) {
        this.billingPeriodSupplier = billingPeriodSupplier != null ? billingPeriodSupplier : BillingPeriod::current;
    }

    private Optional<CatalogGarment> hydrateCatalogGarmentFromRemote(GarmentId garmentId) {
        Optional<CatalogGarment> remote = catalogGarmentLookupPort.findGarmentById(garmentId);
        if (remote.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(quotaRepositoryPort.upsertCatalogGarmentIfNewer(remote.get()));
    }

    private BillingPeriod currentBillingPeriod() {
        return billingPeriodSupplier.get();
    }

    private UserQuota buildQuotaForNewPeriod(UserId userId, BillingPeriod targetPeriod) {
        UserQuota previous = quotaRepositoryPort.findLatestQuotaByUserId(userId).orElse(null);
        return UserQuota.createForNewPeriod(userId, targetPeriod, previous);
    }

    @Override
    public UserQuota getUserQuota(UserId userId) {
        BillingPeriod currentPeriod = currentBillingPeriod();
        UserQuota quota = quotaRepositoryPort.findQuotaByUserIdAndPeriod(userId, currentPeriod)
                .orElseGet(() -> quotaRepositoryPort.findOrCreateQuotaForUpdate(userId, currentPeriod));

        // PostgreSQL es la fuente de verdad autoritativa (consumos y reembolsos con bloqueo pesimista);
        // nunca se sobrescribe la BD con contadores potencialmente desactualizados de Redis.
        resyncCacheFromAuthoritativeQuota(userId, currentPeriod, quota.getUsedCount());

        return quota;
    }

    @Override
    public UserQuota updateUserSubscriptionPlan(UserId userId, boolean radarPlusActive) {
        return updateUserSubscriptionPlan(userId, radarPlusActive, null);
    }

    @Override
    public UserQuota updateUserSubscriptionPlan(UserId userId, boolean radarPlusActive, Instant occurredAt) {
        java.util.Objects.requireNonNull(userId, "El identificador del usuario no puede ser nulo");
        BillingPeriod currentPeriod = currentBillingPeriod();
        if (occurredAt == null) {
            log.warn("Evento de plan {} ignorado para usuario {}: carece de timestamp/versión occurredAt emitida por Pagos; se conserva el estado vigente",
                    radarPlusActive ? "Radar+" : "Gratuito",
                    userId.value());
            return quotaRepositoryPort.findQuotaByUserIdAndPeriod(userId, currentPeriod)
                    .orElseGet(() -> buildQuotaForNewPeriod(userId, currentPeriod));
        }

        UserQuota saved = quotaRepositoryPort.updateQuotaPlanAtomically(
                userId,
                currentPeriod,
                radarPlusActive,
                occurredAt
        );
        if (saved != null) {
            resyncCacheFromAuthoritativeQuota(userId, currentPeriod, saved.getUsedCount());
        }
        return saved;
    }

    @Override
    @Transactional
    public GarmentValidationResult validateGarmentEligibility(
            UserId userId,
            GarmentId garmentId,
            String selectedSize,
            List<GarmentId> selectedGarmentIds
    ) {
        if (selectedSize == null || selectedSize.isBlank()) {
            throw new TryOnSelectionIncompleteException("Selecciona una prenda, una talla y una fotografía válida.");
        }
        String normalizedSize = selectedSize.trim().toUpperCase(java.util.Locale.ROOT);

        CatalogGarment garment = quotaRepositoryPort.findCatalogGarmentById(garmentId)
                .or(() -> hydrateCatalogGarmentFromRemote(garmentId))
                .orElseThrow(() -> new GarmentNotFoundException(garmentId.value()));

        if (!garment.isVisible() || garment.getCategory() == null || !garment.getCategory().isCompatibleWithTryOn()) {
            throw new GarmentNotEligibleException("La prenda no está disponible o no admite prueba virtual.");
        }

        boolean hasActiveConsent = biometricDataRepositoryPort.findConsentByUserId(userId)
                .map(BiometricConsent::isValidForInference)
                .orElse(false);

        BillingPeriod currentPeriod = currentBillingPeriod();
        UserQuota quota = quotaRepositoryPort.findQuotaByUserIdAndPeriod(userId, currentPeriod)
                .orElseGet(() -> buildQuotaForNewPeriod(userId, currentPeriod));

        resyncCacheFromAuthoritativeQuota(userId, currentPeriod, quota.getUsedCount());

        TryOnValidationContext context = new TryOnValidationContext(
                userId,
                garment.getGarmentId(),
                garment.getCategory(),
                garment.getImageUrl(),
                garment.isVisible(),
                hasActiveConsent,
                quota
        );
        eligibilityValidationChain.validate(context);

        GarmentSizeChart sizeChart = (garmentSizeMeasurementPort != null)
                ? garmentSizeMeasurementPort.findSizeChartByGarmentId(garmentId).orElse(null)
                : null;

        boolean sizeOffered;
        if (sizeChart != null && sizeChart.hasExplicitAvailableSizes()) {
            sizeOffered = sizeChart.offersSize(normalizedSize);
        } else {
            sizeOffered = garment.offersSize(normalizedSize);
        }
        if (!sizeOffered) {
            throw new GarmentSizeUnavailableException(normalizedSize);
        }

        UserBodyMeasurements measurements = (userMeasurementPort != null)
                ? userMeasurementPort.findMeasurementsByUserId(userId).orElse(null)
                : null;

        GarmentSizeFitPolicy.SizeFitEvaluation sizeEvaluation = GarmentSizeFitPolicy.evaluate(
                normalizedSize,
                garment.getCategory(),
                measurements,
                sizeChart
        );

        List<CatalogGarment> selectedGarments = (selectedGarmentIds == null || selectedGarmentIds.isEmpty())
                ? List.of()
                : quotaRepositoryPort.findCatalogGarmentsByIds(selectedGarmentIds);

        boolean quotaAvailable = quota.hasAvailableQuota();

        return GarmentSelectionPolicy.evaluate(
                garment,
                selectedGarments,
                sizeEvaluation.normalizedSize(),
                sizeEvaluation.sizeEligible(),
                sizeEvaluation.fitMessage(),
                quotaAvailable
        );
    }

    @Override
    @Transactional(readOnly = true)
    public GarmentValidationResult validateGarmentEligibility(
            UserId userId,
            GarmentId garmentId,
            List<GarmentId> selectedGarmentIds
    ) {
        throw new TryOnSelectionIncompleteException("Selecciona una prenda, una talla y una fotografía válida.");
    }

    private void resyncCacheFromAuthoritativeQuota(UserId userId, BillingPeriod period, int authoritativeUsedCount) {
        if (quotaCachePort == null) {
            return;
        }
        try {
            OptionalInt cachedUsed = quotaCachePort.getCachedUsedCount(userId, period);
            if (cachedUsed.isEmpty() || cachedUsed.getAsInt() != authoritativeUsedCount) {
                quotaCachePort.cacheUsedCount(userId, period, authoritativeUsedCount);
            }
        } catch (RuntimeException ex) {
            log.warn("No fue posible resincronizar en Redis la cuota autoritativa para usuario {} período {}: {}",
                    userId.value(), period.getValue(), ex.getMessage());
        }
    }

    @Override
    public CatalogGarment syncCatalogGarment(CatalogGarment garment) {
        java.util.Objects.requireNonNull(garment, "La prenda de catálogo no puede ser nula");
        return quotaRepositoryPort.upsertCatalogGarmentIfNewer(garment);
    }

    @Override
    public Optional<CatalogGarment> updateCatalogGarmentVisibility(
            edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId garmentId,
            boolean visible,
            java.time.Instant updatedAt
    ) {
        java.util.Objects.requireNonNull(garmentId, "El identificador de prenda no puede ser nulo");
        java.time.Instant effectiveUpdatedAt = updatedAt != null ? updatedAt : java.time.Instant.now();
        return quotaRepositoryPort.findCatalogGarmentById(garmentId)
                .map(existing -> {
                    if (existing.getUpdatedAt() != null && effectiveUpdatedAt.isBefore(existing.getUpdatedAt())) {
                        log.warn("Descartando cambio de visibilidad obsoleto para prenda {}: updatedAt entrante {} es anterior a existente {}",
                                garmentId.value(), effectiveUpdatedAt, existing.getUpdatedAt());
                        return existing;
                    }
                    return quotaRepositoryPort.saveCatalogGarment(new CatalogGarment(
                            existing.getGarmentId(),
                            existing.getName(),
                            existing.getCategory(),
                            existing.getImageUrl(),
                            visible,
                            effectiveUpdatedAt
                    ));
                });
    }

    @Override
    public TryOnJob getTryOnJob(UUID jobId, UserId callerUserId, boolean isAdmin) {
        if (jobRepositoryPort == null) {
            throw new JobNotFoundException(jobId);
        }
        TryOnJob job = jobRepositoryPort.findJobById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (!isAdmin && (callerUserId == null || !job.getUserId().equals(callerUserId))) {
            throw new JobAccessDeniedException("No tiene permisos para consultar este trabajo de prueba virtual");
        }
        return enrichJobWithReadOnlySignedUrl(job);
    }

    @Override
    public TryOnJob getJobStatus(UUID jobId, UserId callerUserId, boolean callerIsAdmin) {
        return getTryOnJob(jobId, callerUserId, callerIsAdmin);
    }

    @Override
    public List<TryOnJob> getJobHistory(UserId userId) {
        if (jobRepositoryPort == null) {
            return List.of();
        }
        return jobRepositoryPort.findJobsByUserId(userId).stream()
                .map(this::enrichJobWithReadOnlySignedUrl)
                .toList();
    }

    private TryOnJob enrichJobWithReadOnlySignedUrl(TryOnJob job) {
        if (job.getStatus() != JobStatus.COMPLETED || job.isPhotoPurged() || job.getResultImageUrl() == null) {
            return job;
        }

        // Se almacena únicamente la referencia permanente del blob en PostgreSQL sin credenciales temporales SAS
        String permanentBlobUrl = job.getResultImageUrl().value().split("\\?", 2)[0];
        Optional<TryOnOutfit> maybeOutfit = jobRepositoryPort.findOutfitByJobId(job.getId());
        if (maybeOutfit.isEmpty()) {
            TryOnOutfit permanentOutfit = TryOnOutfit.createForCompletedJob(
                    job.getId(),
                    job.getUserId(),
                    SasSignedUrl.of(permanentBlobUrl)
            );
            jobRepositoryPort.saveOutfit(permanentOutfit);
        }

        // Se genera la URL temporal firmada (SAS) en memoria al momento de consultar el resultado
        if (blobStoragePort != null) {
            SasSignedUrl readOnlyUrl = blobStoragePort.generateReadOnlySasUrl(SasSignedUrl.of(permanentBlobUrl));
            if (readOnlyUrl != null) {
                job.updateResultImageUrl(readOnlyUrl);
            }
        }
        return job;
    }

    @Override
    public void purgeJobBiometricPhoto(UUID jobId, UserId callerUserId, boolean isAdmin) {
        if (jobRepositoryPort == null) {
            throw new JobNotFoundException(jobId);
        }
        TryOnJob job = jobRepositoryPort.findJobById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (!isAdmin && (callerUserId == null || !job.getUserId().equals(callerUserId))) {
            throw new JobAccessDeniedException("No tiene permisos para consultar este trabajo de prueba virtual");
        }

        if (job.isPhotoPurged() && job.getResultImageUrl() == null) {
            throw new JobNotFoundException(jobId);
        }

        if (blobStoragePort == null) {
            throw new IllegalStateException(
                    "No es posible ejecutar la purga biométrica bajo la Ley 1581 de 2012 porque el conector de almacenamiento físico (BlobStoragePort) no está configurado o disponible"
            );
        }

        boolean rawPhotoNeedsPurge = job.getPhotoUrl() != null
                && !job.isPhotoPurged()
                && !job.getPhotoUrl().startsWith("PURGED");
        boolean renderNeedsPurge = job.getResultImageUrl() != null;

        // 1. Borrado físico estricto de fotografía frontal de silueta (sin silenciar excepciones de Azure)
        if (rawPhotoNeedsPurge) {
            log.info("Iniciando borrado físico en Azure Blob Storage de silueta biométrica para trabajo {}", jobId);
            blobStoragePort.deleteBlob(job.getPhotoUrl());
            job.purgeRawSilhouettePhoto();
            jobRepositoryPort.saveJob(job);
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    job.getId(),
                    job.getStatus(),
                    job.getStatus(),
                    "Purga física y anonimización de fotografía frontal de silueta completada exitosamente en Azure Blob Storage (Ley 1581)"
            ));
        }

        // 2. Borrado físico estricto de imagen renderizada generada (sin silenciar excepciones de Azure)
        if (renderNeedsPurge) {
            log.info("Iniciando borrado físico en Azure Blob Storage del render de resultado para trabajo {}", jobId);
            blobStoragePort.deleteBlob(job.getResultImageUrl().value());
            job.purgeResultRenderImage();
            jobRepositoryPort.saveJob(job);
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    job.getId(),
                    job.getStatus(),
                    job.getStatus(),
                    "Purga física de imagen renderizada generada completada exitosamente en Azure Blob Storage"
            ));
        }

        // 3. Eliminación consistente del registro asociado en try_on_outfits para garantizar borrado integral (Ley 1581)
        jobRepositoryPort.deleteOutfitByJobId(jobId);

        // 4. Registro de auditoría de completitud integral bajo Ley 1581 confirmado tras borrado físico
        jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                job.getId(),
                job.getStatus(),
                job.getStatus(),
                "Purga integral y borrado físico de datos biométricos completado bajo Ley 1581 de 2012"
        ));
    }
}
