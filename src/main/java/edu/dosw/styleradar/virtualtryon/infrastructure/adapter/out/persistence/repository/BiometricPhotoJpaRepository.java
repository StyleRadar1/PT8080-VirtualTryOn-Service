package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la entidad BiometricPhotoJpaEntity.
 */
public interface BiometricPhotoJpaRepository extends JpaRepository<BiometricPhotoJpaEntity, UUID> {

    Optional<BiometricPhotoJpaEntity> findByUserId(UUID userId);
}

