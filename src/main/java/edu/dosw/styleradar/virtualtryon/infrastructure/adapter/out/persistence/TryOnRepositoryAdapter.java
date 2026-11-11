package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence;

import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnOutfit;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserQuotaRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.JobAuditLogJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnJobJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnOutfitJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricConsentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricPhotoJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.CatalogGarmentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.JobAuditLogJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.TryOnJobJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.TryOnOutfitJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.UserQuotaJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de salida de persistencia que implementa BiometricDataRepositoryPort,
 * UserQuotaRepositoryPort y TryOnJobRepositoryPort delegando en Spring Data JPA hacia PostgreSQL (probador_db).
 */
@Component
public class TryOnRepositoryAdapter implements BiometricDataRepositoryPort, UserQuotaRepositoryPort, TryOnJobRepositoryPort {

    private final BiometricConsentJpaRepository consentJpaRepository;
    private final BiometricPhotoJpaRepository photoJpaRepository;
    private final UserQuotaJpaRepository quotaJpaRepository;
    private final CatalogGarmentJpaRepository catalogGarmentJpaRepository;
    private final TryOnJobJpaRepository tryOnJobJpaRepository;
    private final JobAuditLogJpaRepository jobAuditLogJpaRepository;
    private final TryOnOutfitJpaRepository tryOnOutfitJpaRepository;
    private final TryOnPersistenceMapper persistenceMapper;

    @Autowired
    public TryOnRepositoryAdapter(
            BiometricConsentJpaRepository consentJpaRepository,
            BiometricPhotoJpaRepository photoJpaRepository,
            UserQuotaJpaRepository quotaJpaRepository,
            CatalogGarmentJpaRepository catalogGarmentJpaRepository,
            TryOnJobJpaRepository tryOnJobJpaRepository,
            JobAuditLogJpaRepository jobAuditLogJpaRepository,
            TryOnOutfitJpaRepository tryOnOutfitJpaRepository,
            TryOnPersistenceMapper persistenceMapper
    ) {
        this.consentJpaRepository = consentJpaRepository;
        this.photoJpaRepository = photoJpaRepository;
        this.quotaJpaRepository = quotaJpaRepository;
        this.catalogGarmentJpaRepository = catalogGarmentJpaRepository;
        this.tryOnJobJpaRepository = tryOnJobJpaRepository;
        this.jobAuditLogJpaRepository = jobAuditLogJpaRepository;
        this.tryOnOutfitJpaRepository = tryOnOutfitJpaRepository;
        this.persistenceMapper = persistenceMapper;
    }

    public TryOnRepositoryAdapter(
            BiometricConsentJpaRepository consentJpaRepository,
            BiometricPhotoJpaRepository photoJpaRepository,
            UserQuotaJpaRepository quotaJpaRepository,
            CatalogGarmentJpaRepository catalogGarmentJpaRepository,
            TryOnJobJpaRepository tryOnJobJpaRepository,
            JobAuditLogJpaRepository jobAuditLogJpaRepository,
            TryOnPersistenceMapper persistenceMapper
    ) {
        this(
                consentJpaRepository,
                photoJpaRepository,
                quotaJpaRepository,
                catalogGarmentJpaRepository,
                tryOnJobJpaRepository,
                jobAuditLogJpaRepository,
                null,
                persistenceMapper
        );
    }

    public TryOnRepositoryAdapter(
            BiometricConsentJpaRepository consentJpaRepository,
            BiometricPhotoJpaRepository photoJpaRepository,
            UserQuotaJpaRepository quotaJpaRepository,
            CatalogGarmentJpaRepository catalogGarmentJpaRepository,
            TryOnPersistenceMapper persistenceMapper
    ) {
        this(
                consentJpaRepository,
                photoJpaRepository,
                quotaJpaRepository,
                catalogGarmentJpaRepository,
                null,
                null,
                persistenceMapper
        );
    }

    public TryOnRepositoryAdapter(
            BiometricConsentJpaRepository consentJpaRepository,
            BiometricPhotoJpaRepository photoJpaRepository,
            TryOnPersistenceMapper persistenceMapper
    ) {
        this(consentJpaRepository, photoJpaRepository, null, null, null, null, persistenceMapper);
    }

    @Override
    public BiometricConsent saveConsent(BiometricConsent consent) {
        BiometricConsentJpaEntity saved = consentJpaRepository.save(persistenceMapper.toConsentJpaEntity(consent));
        return persistenceMapper.toConsentDomain(saved);
    }

    @Override
    public Optional<BiometricConsent> findConsentByUserId(UserId userId) {
        return consentJpaRepository.findFirstByUserIdOrderByConsentedAtDesc(userId.value())
                .map(persistenceMapper::toConsentDomain);
    }

    @Override
    public BiometricPhoto savePhoto(BiometricPhoto photo) {
        Optional<BiometricPhotoJpaEntity> existing = photoJpaRepository.findByUserId(photo.getUserId().value());
        BiometricPhotoJpaEntity toSave = persistenceMapper.toPhotoJpaEntity(photo);
        existing.ifPresent(entity -> toSave.setId(entity.getId()));
        BiometricPhotoJpaEntity saved = photoJpaRepository.save(toSave);
        return persistenceMapper.toPhotoDomain(saved);
    }

    @Override
    public Optional<BiometricPhoto> findPhotoByUserId(UserId userId) {
        return photoJpaRepository.findByUserId(userId.value())
                .map(persistenceMapper::toPhotoDomain);
    }

    @Override
    public Optional<BiometricPhoto> findPhotoById(UUID photoId) {
        return photoJpaRepository.findById(photoId)
                .map(persistenceMapper::toPhotoDomain);
    }

    @Override
    public void deletePhotoByUserId(UserId userId) {
        photoJpaRepository.deleteByUserId(userId.value());
    }

    @Override
    public UserQuota saveQuota(UserQuota userQuota) {
        Optional<UserQuotaJpaEntity> existing = quotaJpaRepository.findByUserIdAndBillingPeriod(
                userQuota.getUserId().value(),
                userQuota.getBillingPeriod().getValue()
        );
        UserQuotaJpaEntity toSave = persistenceMapper.toQuotaJpaEntity(userQuota);
        existing.ifPresent(entity -> toSave.setId(entity.getId()));
        UserQuotaJpaEntity saved = quotaJpaRepository.save(toSave);
        return persistenceMapper.toQuotaDomain(saved);
    }

    @Override
    public Optional<UserQuota> findQuotaByUserIdAndPeriod(UserId userId, BillingPeriod billingPeriod) {
        return quotaJpaRepository.findByUserIdAndBillingPeriod(userId.value(), billingPeriod.getValue())
                .map(persistenceMapper::toQuotaDomain);
    }

    @Override
    public Optional<UserQuota> findQuotaByUserIdAndPeriodForUpdate(UserId userId, BillingPeriod billingPeriod) {
        return quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(userId.value(), billingPeriod.getValue())
                .map(persistenceMapper::toQuotaDomain);
    }

    @Override
    public CatalogGarment saveCatalogGarment(CatalogGarment garment) {
        CatalogGarmentJpaEntity saved = catalogGarmentJpaRepository.save(
                persistenceMapper.toCatalogGarmentJpaEntity(garment)
        );
        return persistenceMapper.toCatalogGarmentDomain(saved);
    }

    @Override
    public Optional<CatalogGarment> findCatalogGarmentById(GarmentId garmentId) {
        return catalogGarmentJpaRepository.findById(garmentId.value())
                .map(persistenceMapper::toCatalogGarmentDomain);
    }

    @Override
    public TryOnJob saveJob(TryOnJob job) {
        TryOnJobJpaEntity saved = tryOnJobJpaRepository.save(persistenceMapper.toJobJpaEntity(job));
        return persistenceMapper.toJobDomain(saved);
    }

    @Override
    public Optional<TryOnJob> findJobById(UUID jobId) {
        return tryOnJobJpaRepository.findById(jobId)
                .map(persistenceMapper::toJobDomain);
    }

    @Override
    public Optional<TryOnJob> findJobByIdForUpdate(UUID jobId) {
        return tryOnJobJpaRepository.findByIdForUpdate(jobId)
                .map(persistenceMapper::toJobDomain);
    }

    @Override
    public List<TryOnJob> findJobsByUserId(UserId userId) {
        return tryOnJobJpaRepository.findByUserIdOrderByCreatedAtDesc(userId.value()).stream()
                .map(persistenceMapper::toJobDomain)
                .toList();
    }

    @Override
    public List<TryOnJob> findStaleProcessingJobs(java.time.Instant threshold) {
        return tryOnJobJpaRepository.findByStatusAndUpdatedAtBefore("PROCESSING", threshold).stream()
                .map(persistenceMapper::toJobDomain)
                .toList();
    }

    @Override
    public JobAuditLog saveAuditLog(JobAuditLog auditLog) {
        JobAuditLogJpaEntity saved = jobAuditLogJpaRepository.save(persistenceMapper.toAuditLogJpaEntity(auditLog));
        return persistenceMapper.toAuditLogDomain(saved);
    }

    @Override
    public List<JobAuditLog> findAuditLogsByJobId(UUID jobId) {
        return jobAuditLogJpaRepository.findByJobIdOrderByOccurredAtAsc(jobId).stream()
                .map(persistenceMapper::toAuditLogDomain)
                .toList();
    }

    @Override
    public TryOnOutfit saveOutfit(TryOnOutfit outfit) {
        if (tryOnOutfitJpaRepository == null) {
            return outfit;
        }
        Optional<TryOnOutfitJpaEntity> existing = tryOnOutfitJpaRepository.findByJobId(outfit.getJobId());
        TryOnOutfitJpaEntity toSave = persistenceMapper.toOutfitJpaEntity(outfit);
        existing.ifPresent(entity -> toSave.setId(entity.getId()));
        TryOnOutfitJpaEntity saved = tryOnOutfitJpaRepository.save(toSave);
        return persistenceMapper.toOutfitDomain(saved);
    }

    @Override
    public Optional<TryOnOutfit> findOutfitByJobId(UUID jobId) {
        if (tryOnOutfitJpaRepository == null) {
            return Optional.empty();
        }
        return tryOnOutfitJpaRepository.findByJobId(jobId)
                .map(persistenceMapper::toOutfitDomain);
    }

    @Override
    public void deleteOutfitByJobId(UUID jobId) {
        if (tryOnOutfitJpaRepository != null) {
            tryOnOutfitJpaRepository.deleteByJobId(jobId);
        }
    }
}
