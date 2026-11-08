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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de salida de persistencia que implementa BiometricDataRepositoryPort.
 * Utiliza los repositorios Spring Data JPA cuando hay un DataSource activo, y cuenta con
 * almacenamiento concurrente en memoria como respaldo en perfiles sin base de datos activa.
 */
@Component
public class TryOnRepositoryAdapter implements BiometricDataRepositoryPort {

    private final BiometricConsentJpaRepository consentJpaRepository;
    private final BiometricPhotoJpaRepository photoJpaRepository;
    private final TryOnPersistenceMapper persistenceMapper;

    private final Map<UUID, BiometricConsent> fallbackConsents = new ConcurrentHashMap<>();
    private final Map<UUID, BiometricPhoto> fallbackPhotos = new ConcurrentHashMap<>();

    public TryOnRepositoryAdapter(
            @Autowired(required = false) BiometricConsentJpaRepository consentJpaRepository,
            @Autowired(required = false) BiometricPhotoJpaRepository photoJpaRepository,
            TryOnPersistenceMapper persistenceMapper
    ) {
        this.consentJpaRepository = consentJpaRepository;
        this.photoJpaRepository = photoJpaRepository;
        this.persistenceMapper = persistenceMapper;
    }

    @Override
    public BiometricConsent saveConsent(BiometricConsent consent) {
        if (consentJpaRepository != null) {
            BiometricConsentJpaEntity saved = consentJpaRepository.save(persistenceMapper.toConsentJpaEntity(consent));
            return persistenceMapper.toConsentDomain(saved);
        }
        fallbackConsents.put(consent.getUserId().value(), consent);
        return consent;
    }

    @Override
    public Optional<BiometricConsent> findConsentByUserId(UserId userId) {
        if (consentJpaRepository != null) {
            return consentJpaRepository.findFirstByUserIdOrderByConsentedAtDesc(userId.value())
                    .map(persistenceMapper::toConsentDomain);
        }
        return Optional.ofNullable(fallbackConsents.get(userId.value()));
    }

    @Override
    public BiometricPhoto savePhoto(BiometricPhoto photo) {
        if (photoJpaRepository != null) {
            Optional<BiometricPhotoJpaEntity> existing = photoJpaRepository.findByUserId(photo.getUserId().value());
            BiometricPhotoJpaEntity toSave = persistenceMapper.toPhotoJpaEntity(photo);
            existing.ifPresent(entity -> toSave.setId(entity.getId()));
            BiometricPhotoJpaEntity saved = photoJpaRepository.save(toSave);
            return persistenceMapper.toPhotoDomain(saved);
        }
        fallbackPhotos.put(photo.getUserId().value(), photo);
        return photo;
    }

    @Override
    public Optional<BiometricPhoto> findPhotoByUserId(UserId userId) {
        if (photoJpaRepository != null) {
            return photoJpaRepository.findByUserId(userId.value())
                    .map(persistenceMapper::toPhotoDomain);
        }
        return Optional.ofNullable(fallbackPhotos.get(userId.value()));
    }
}

