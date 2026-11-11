package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la proyección local de prendas del catálogo en PostgreSQL (SR-RF-24).
 */
public interface CatalogGarmentJpaRepository extends JpaRepository<CatalogGarmentJpaEntity, UUID> {
}

