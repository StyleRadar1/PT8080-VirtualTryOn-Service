package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio JPA para la persistencia y reclamación concurrente exclusiva de eventos outbox (SR-RF-25).
 * Emplea bloqueo pesimista con SKIP LOCKED (timeout = -2) para permitir que múltiples instancias
 * de workers reclamen eventos sin bloquearse mutuamente ni generar condiciones de carrera.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")
    })
    @Query("SELECT o FROM OutboxEventJpaEntity o WHERE o.status = 'PENDING' OR (o.status = 'PROCESSING' AND o.claimExpiresAt < :now) ORDER BY o.createdAt ASC")
    List<OutboxEventJpaEntity> findClaimableEventsForUpdate(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT o FROM OutboxEventJpaEntity o WHERE o.status = 'PENDING' OR (o.status = 'PROCESSING' AND o.claimExpiresAt < :now) ORDER BY o.createdAt ASC")
    List<OutboxEventJpaEntity> findClaimableEvents(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT o FROM OutboxEventJpaEntity o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxEventJpaEntity> findPendingEvents();
}
