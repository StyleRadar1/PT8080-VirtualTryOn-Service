package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserQuotaCachePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserQuotaRepositoryPort;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Implementación del servicio de aplicación para la consulta y sincronización de cuotas mensuales,
 * validación de prendas compatibles del catálogo, consulta de trabajos y purga biométrica (SR-RF-23 a SR-RF-25).
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
    private final TryOnValidatorHandler eligibilityValidationChain;

    @Autowired
    public QueryTryOnJobsServiceImpl(
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnJobRepositoryPort jobRepositoryPort,
            @Autowired(required = false) BlobStoragePort blobStoragePort
    ) {
        this.quotaRepositoryPort = quotaRepositoryPort;
        this.quotaCachePort = quotaCachePort;
        this.biometricDataRepositoryPort = biometricDataRepositoryPort;
        this.jobRepositoryPort = jobRepositoryPort;
        this.blobStoragePort = blobStoragePort;

        TryOnValidatorHandler consentValidator = new BiometricConsentValidator();
        TryOnValidatorHandler quotaValidator = new MonthlyUserQuotaValidator();
        TryOnValidatorHandler garmentValidator = new GarmentCategoryValidator();
        consentValidator.setNext(quotaValidator).setNext(garmentValidator);
        this.eligibilityValidationChain = consentValidator;
    }

    public QueryTryOnJobsServiceImpl(
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            TryOnJobRepositoryPort jobRepositoryPort
    ) {
        this(quotaRepositoryPort, quotaCachePort, biometricDataRepositoryPort, jobRepositoryPort, null);
    }

    public QueryTryOnJobsServiceImpl(
            UserQuotaRepositoryPort quotaRepositoryPort,
            UserQuotaCachePort quotaCachePort,
            BiometricDataRepositoryPort biometricDataRepositoryPort
    ) {
        this(quotaRepositoryPort, quotaCachePort, biometricDataRepositoryPort, null, null);
    }

    @Override
    public UserQuota getUserQuota(UserId userId) {
        BillingPeriod currentPeriod = BillingPeriod.current();
        UserQuota quota = quotaRepositoryPort.findQuotaByUserIdAndPeriod(userId, currentPeriod)
                .orElseGet(() -> quotaRepositoryPort.saveQuota(UserQuota.createDefault(userId, currentPeriod)));

        OptionalInt cachedUsed = quotaCachePort.getCachedUsedCount(userId, currentPeriod);
        if (cachedUsed.isPresent() && cachedUsed.getAsInt() > quota.getUsedCount()) {
            UserQuota synchronizedQuota = new UserQuota(
                    quota.getId(),
                    quota.getUserId(),
                    quota.getMonthlyLimit(),
                    cachedUsed.getAsInt(),
                    quota.getBillingPeriod(),
                    quota.isRadarPlus(),
                    quota.getLastResetAt()
            );
            quota = quotaRepositoryPort.saveQuota(synchronizedQuota);
        } else if (cachedUsed.isEmpty()) {
            quotaCachePort.cacheUsedCount(userId, currentPeriod, quota.getUsedCount());
        }

        return quota;
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogGarment validateGarmentEligibility(UserId userId, GarmentId garmentId) {
        CatalogGarment garment = quotaRepositoryPort.findCatalogGarmentById(garmentId)
                .orElseThrow(() -> new InvalidGarmentException(
                        garmentId.value(),
                        "No existe la prenda del catálogo o aún no ha sido publicada"
                ));

        boolean hasActiveConsent = biometricDataRepositoryPort.findConsentByUserId(userId)
                .map(BiometricConsent::isValidForInference)
                .orElse(false);

        BillingPeriod currentPeriod = BillingPeriod.current();
        UserQuota quota = quotaRepositoryPort.findQuotaByUserIdAndPeriod(userId, currentPeriod)
                .orElseGet(() -> UserQuota.createDefault(userId, currentPeriod));

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
        return garment;
    }

    @Override
    public CatalogGarment syncCatalogGarment(CatalogGarment garment) {
        return quotaRepositoryPort.saveCatalogGarment(garment);
    }

    @Override
    public TryOnJob getTryOnJob(UUID jobId, UserId callerUserId, boolean isAdmin) {
        if (jobRepositoryPort == null) {
            throw new JobNotFoundException(jobId);
        }
        TryOnJob job = jobRepositoryPort.findJobById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (!isAdmin && (callerUserId == null || !job.getUserId().equals(callerUserId))) {
            throw new AccessDeniedException("No tiene permisos para consultar este trabajo de prueba virtual");
        }
        return job;
    }

    @Override
    public void purgeJobBiometricPhoto(UUID jobId, UserId callerUserId, boolean isAdmin) {
        TryOnJob job = getTryOnJob(jobId, callerUserId, isAdmin);

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
            if (jobRepositoryPort != null) {
                jobRepositoryPort.saveJob(job);
                jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                        job.getId(),
                        job.getStatus(),
                        job.getStatus(),
                        "Purga física y anonimización de fotografía frontal de silueta completada exitosamente en Azure Blob Storage (Ley 1581)"
                ));
            }
        }

        // 2. Borrado físico estricto de imagen renderizada generada (sin silenciar excepciones de Azure)
        if (renderNeedsPurge) {
            log.info("Iniciando borrado físico en Azure Blob Storage del render de resultado para trabajo {}", jobId);
            blobStoragePort.deleteBlob(job.getResultImageUrl().value());
            job.purgeResultRenderImage();
            if (jobRepositoryPort != null) {
                jobRepositoryPort.saveJob(job);
                jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                        job.getId(),
                        job.getStatus(),
                        job.getStatus(),
                        "Purga física de imagen renderizada generada completada exitosamente en Azure Blob Storage"
                ));
            }
        }

        // 3. Registro de auditoría de completitud integral bajo Ley 1581 confirmado tras borrado físico
        if (jobRepositoryPort != null) {
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    job.getId(),
                    job.getStatus(),
                    job.getStatus(),
                    "Purga integral y borrado físico de datos biométricos completado bajo Ley 1581 de 2012"
            ));
        }
    }
}

