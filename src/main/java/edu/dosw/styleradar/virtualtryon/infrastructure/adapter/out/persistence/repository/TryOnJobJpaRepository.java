package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnJobJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la gestión de trabajos TryOnJob en PostgreSQL (probador_db) (SR-RF-25).
 */
public interface TryOnJobJpaRepository extends JpaRepository<TryOnJobJpaEntity, UUID> {

    List<TryOnJobJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}

