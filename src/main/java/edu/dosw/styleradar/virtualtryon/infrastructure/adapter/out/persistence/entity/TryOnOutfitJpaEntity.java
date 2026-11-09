package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA para la tabla try_on_outfits en PostgreSQL (probador_db),
 * que almacena el resultado visual renderizado y su URL firmada temporal (SR-RF-26).
 */
@Entity
@Table(name = "try_on_outfits")
public class TryOnOutfitJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "final_render_url", nullable = false, columnDefinition = "TEXT")
    private String finalRenderUrl;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TryOnOutfitJpaEntity() {
    }

    public TryOnOutfitJpaEntity(
            UUID id,
            UUID jobId,
            UUID userId,
            String finalRenderUrl,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.id = id;
        this.jobId = jobId;
        this.userId = userId;
        this.finalRenderUrl = finalRenderUrl;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFinalRenderUrl() {
        return finalRenderUrl;
    }

    public void setFinalRenderUrl(String finalRenderUrl) {
        this.finalRenderUrl = finalRenderUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

