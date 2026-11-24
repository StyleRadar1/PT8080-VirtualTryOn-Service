package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la proyección local de prendas del catálogo en PostgreSQL (SR-RF-24).
 */
public interface CatalogGarmentJpaRepository extends JpaRepository<CatalogGarmentJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM CatalogGarmentJpaEntity g WHERE g.garmentId = :garmentId")
    Optional<CatalogGarmentJpaEntity> findByIdForUpdate(@Param("garmentId") UUID garmentId);

    @Modifying
    @Query(value = """
            INSERT INTO catalog_garments (
                garment_id,
                name,
                category,
                image_url,
                visible,
                available_sizes,
                updated_at
            )
            VALUES (
                :garmentId,
                :name,
                :category,
                :imageUrl,
                :visible,
                :availableSizes,
                :updatedAt
            )
            """, nativeQuery = true)
    int insertRow(
            @Param("garmentId") UUID garmentId,
            @Param("name") String name,
            @Param("category") String category,
            @Param("imageUrl") String imageUrl,
            @Param("visible") boolean visible,
            @Param("availableSizes") String availableSizes,
            @Param("updatedAt") Instant updatedAt
    );
}

