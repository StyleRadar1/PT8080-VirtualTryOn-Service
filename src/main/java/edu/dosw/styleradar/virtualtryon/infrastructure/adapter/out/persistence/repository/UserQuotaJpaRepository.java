package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la gestión de cuotas mensuales en PostgreSQL (probador_db) (SR-RF-24 y SR-RF-25).
 * Soporta bloqueo pesimista (PESSIMISTIC_WRITE) para prevenir condiciones de carrera y sobreconsumo concurrentes.
 */
public interface UserQuotaJpaRepository extends JpaRepository<UserQuotaJpaEntity, UUID> {

    Optional<UserQuotaJpaEntity> findByUserIdAndBillingPeriod(UUID userId, String billingPeriod);

    List<UserQuotaJpaEntity> findAllByUserIdAndBillingPeriod(UUID userId, String billingPeriod);

    Optional<UserQuotaJpaEntity> findFirstByUserIdOrderByBillingPeriodDescLastResetAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM UserQuotaJpaEntity q WHERE q.userId = :userId AND q.billingPeriod = :billingPeriod")
    Optional<UserQuotaJpaEntity> findByUserIdAndBillingPeriodForUpdate(
            @Param("userId") UUID userId,
            @Param("billingPeriod") String billingPeriod
    );

    @Modifying
    @Query(value = """
            INSERT INTO user_quotas (
                id,
                user_id,
                monthly_limit,
                used_count,
                billing_period,
                is_radar_plus,
                last_reset_at,
                plan_updated_at
            )
            VALUES (
                :id,
                :userId,
                :monthlyLimit,
                :usedCount,
                :billingPeriod,
                :radarPlus,
                :lastResetAt,
                :planUpdatedAt
            )
            ON CONFLICT (user_id, billing_period) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("monthlyLimit") int monthlyLimit,
            @Param("usedCount") int usedCount,
            @Param("billingPeriod") String billingPeriod,
            @Param("radarPlus") boolean radarPlus,
            @Param("lastResetAt") Instant lastResetAt,
            @Param("planUpdatedAt") Instant planUpdatedAt
    );
}

