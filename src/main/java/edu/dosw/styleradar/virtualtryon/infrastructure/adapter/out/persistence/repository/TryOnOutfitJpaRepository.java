package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnOutfitJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la persistencia de resultados renderizados TryOnOutfit (SR-RF-26).
 */
public interface TryOnOutfitJpaRepository extends JpaRepository<TryOnOutfitJpaEntity, UUID> {

    Optional<TryOnOutfitJpaEntity> findByJobId(UUID jobId);

    void deleteByJobId(UUID jobId);
}

