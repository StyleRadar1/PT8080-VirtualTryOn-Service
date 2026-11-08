package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la gestión de cuotas mensuales en PostgreSQL (probador_db) (SR-RF-24).
 */
public interface UserQuotaJpaRepository extends JpaRepository<UserQuotaJpaEntity, UUID> {

    Optional<UserQuotaJpaEntity> findByUserIdAndBillingPeriod(UUID userId, String billingPeriod);
}

