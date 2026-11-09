package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio JPA para la persistencia y consulta de eventos outbox pendientes (SR-RF-25).
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Query("SELECT o FROM OutboxEventJpaEntity o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxEventJpaEntity> findPendingEvents();
}

