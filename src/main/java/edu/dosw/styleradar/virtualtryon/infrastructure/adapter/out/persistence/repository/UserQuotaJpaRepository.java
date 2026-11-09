package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la gestión de cuotas mensuales en PostgreSQL (probador_db) (SR-RF-24 y SR-RF-25).
 * Soporta bloqueo pesimista (PESSIMISTIC_WRITE) para prevenir condiciones de carrera y sobreconsumo concurrentes.
 */
public interface UserQuotaJpaRepository extends JpaRepository<UserQuotaJpaEntity, UUID> {

    Optional<UserQuotaJpaEntity> findByUserIdAndBillingPeriod(UUID userId, String billingPeriod);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM UserQuotaJpaEntity q WHERE q.userId = :userId AND q.billingPeriod = :billingPeriod")
    Optional<UserQuotaJpaEntity> findByUserIdAndBillingPeriodForUpdate(
            @Param("userId") UUID userId,
            @Param("billingPeriod") String billingPeriod
    );
}

