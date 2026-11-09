package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnJobJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la gestión de trabajos TryOnJob en PostgreSQL (probador_db) (SR-RF-25).
 * Incluye soporte de bloqueo pesimista (PESSIMISTIC_WRITE) para evitar procesamiento concurrente duplicado.
 */
public interface TryOnJobJpaRepository extends JpaRepository<TryOnJobJpaEntity, UUID> {

    List<TryOnJobJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM TryOnJobJpaEntity j WHERE j.id = :id")
    Optional<TryOnJobJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    List<TryOnJobJpaEntity> findByStatusAndUpdatedAtBefore(String status, Instant threshold);
}
