package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA para la tabla catalog_garments que persiste la proyección local de prendas del catálogo (SR-RF-24).
 */
@Entity
@Table(name = "catalog_garments")
public class CatalogGarmentJpaEntity {

    @Id
    @Column(name = "garment_id", nullable = false, updatable = false)
    private UUID garmentId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "visible", nullable = false)
    private boolean visible;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CatalogGarmentJpaEntity() {
    }

    public CatalogGarmentJpaEntity(
            UUID garmentId,
            String name,
            String category,
            String imageUrl,
            boolean visible,
            Instant updatedAt
    ) {
        this.garmentId = garmentId;
        this.name = name;
        this.category = category;
        this.imageUrl = imageUrl;
        this.visible = visible;
        this.updatedAt = updatedAt;
    }

    public UUID getGarmentId() {
        return garmentId;
    }

    public void setGarmentId(UUID garmentId) {
        this.garmentId = garmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

