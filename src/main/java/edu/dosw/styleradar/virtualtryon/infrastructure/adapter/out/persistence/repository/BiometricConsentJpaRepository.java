package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la entidad BiometricConsentJpaEntity.
 */
public interface BiometricConsentJpaRepository extends JpaRepository<BiometricConsentJpaEntity, UUID> {

    Optional<BiometricConsentJpaEntity> findFirstByUserIdOrderByConsentedAtDesc(UUID userId);
}

