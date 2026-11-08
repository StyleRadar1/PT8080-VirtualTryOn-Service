package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence;

import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricConsentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricPhotoJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador de salida de persistencia que implementa BiometricDataRepositoryPort
 * delegando en los repositorios Spring Data JPA hacia PostgreSQL (probador_db).
 */
@Component
public class TryOnRepositoryAdapter implements BiometricDataRepositoryPort {

    private final BiometricConsentJpaRepository consentJpaRepository;
    private final BiometricPhotoJpaRepository photoJpaRepository;
    private final TryOnPersistenceMapper persistenceMapper;

    public TryOnRepositoryAdapter(
            BiometricConsentJpaRepository consentJpaRepository,
            BiometricPhotoJpaRepository photoJpaRepository,
            TryOnPersistenceMapper persistenceMapper
    ) {
        this.consentJpaRepository = consentJpaRepository;
        this.photoJpaRepository = photoJpaRepository;
        this.persistenceMapper = persistenceMapper;
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
}
