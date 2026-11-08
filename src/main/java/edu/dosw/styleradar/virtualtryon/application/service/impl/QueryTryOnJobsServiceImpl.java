package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnOutfit;
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
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Implementación del servicio de aplicación para la consulta y sincronización de cuotas mensuales,
 * validación de prendas compatibles y visualización de resultados e historial de trabajos (SR-RF-24 y SR-RF-26).
 */
@Service
@Transactional
public class QueryTryOnJobsServiceImpl implements QueryTryOnJobsService {

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
            BlobStoragePort blobStoragePort
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
    public TryOnJob getJobStatus(UUID jobId, UserId callerUserId, boolean callerIsAdmin) {
        TryOnJob job = jobRepositoryPort.findJobById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (!callerIsAdmin && !job.getUserId().equals(callerUserId)) {
            throw new AccessDeniedException(
                    "No tiene permisos para visualizar el trabajo de prueba virtual de otro usuario"
            );
        }

        return enrichJobWithReadOnlySignedUrl(job);
    }

    @Override
    public List<TryOnJob> getJobHistory(UserId userId) {
        return jobRepositoryPort.findJobsByUserId(userId).stream()
                .map(this::enrichJobWithReadOnlySignedUrl)
                .toList();
    }

    private TryOnJob enrichJobWithReadOnlySignedUrl(TryOnJob job) {
        if (job.getStatus() != JobStatus.COMPLETED || job.isPhotoPurged() || job.getResultImageUrl() == null) {
            return job;
        }
        Optional<TryOnOutfit> maybeOutfit = jobRepositoryPort.findOutfitByJobId(job.getId());
        if (maybeOutfit.isPresent()) {
            TryOnOutfit outfit = maybeOutfit.get();
            if (outfit.isUrlExpired() && blobStoragePort != null) {
                SasSignedUrl refreshed = blobStoragePort.generateReadOnlySasUrl(outfit.getFinalRenderUrl());
                outfit.refreshSignedUrl(refreshed);
                jobRepositoryPort.saveOutfit(outfit);
            }
            job.updateResultImageUrl(outfit.getFinalRenderUrl());
        } else if (blobStoragePort != null) {
            SasSignedUrl readOnlyUrl = blobStoragePort.generateReadOnlySasUrl(job.getResultImageUrl());
            TryOnOutfit createdOutfit = TryOnOutfit.createForCompletedJob(job.getId(), job.getUserId(), readOnlyUrl);
            jobRepositoryPort.saveOutfit(createdOutfit);
            job.updateResultImageUrl(readOnlyUrl);
        }
        return job;
    }
}
