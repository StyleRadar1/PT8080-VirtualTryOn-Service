package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence;

import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserQuotaRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricConsentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricPhotoJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.CatalogGarmentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.UserQuotaJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador de salida de persistencia que implementa BiometricDataRepositoryPort y UserQuotaRepositoryPort
 * delegando en los repositorios Spring Data JPA hacia PostgreSQL (probador_db) (SR-RF-23 y SR-RF-24).
 */
@Component
public class TryOnRepositoryAdapter implements BiometricDataRepositoryPort, UserQuotaRepositoryPort {

    private final BiometricConsentJpaRepository consentJpaRepository;
    private final BiometricPhotoJpaRepository photoJpaRepository;
    private final UserQuotaJpaRepository quotaJpaRepository;
    private final CatalogGarmentJpaRepository catalogGarmentJpaRepository;
    private final TryOnPersistenceMapper persistenceMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public TryOnRepositoryAdapter(
            BiometricConsentJpaRepository consentJpaRepository,
            BiometricPhotoJpaRepository photoJpaRepository,
            UserQuotaJpaRepository quotaJpaRepository,
            CatalogGarmentJpaRepository catalogGarmentJpaRepository,
            TryOnPersistenceMapper persistenceMapper
    ) {
        this.consentJpaRepository = consentJpaRepository;
        this.photoJpaRepository = photoJpaRepository;
        this.quotaJpaRepository = quotaJpaRepository;
        this.catalogGarmentJpaRepository = catalogGarmentJpaRepository;
        this.persistenceMapper = persistenceMapper;
    }

    public TryOnRepositoryAdapter(
            BiometricConsentJpaRepository consentJpaRepository,
            BiometricPhotoJpaRepository photoJpaRepository,
            TryOnPersistenceMapper persistenceMapper
    ) {
        this(consentJpaRepository, photoJpaRepository, null, null, persistenceMapper);
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
}
