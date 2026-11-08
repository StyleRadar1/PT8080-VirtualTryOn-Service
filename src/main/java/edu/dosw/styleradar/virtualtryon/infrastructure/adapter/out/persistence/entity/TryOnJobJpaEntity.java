package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA para la tabla try_on_jobs que persiste las ejecuciones del Probador Virtual (SR-RF-25).
 */
@Entity
@Table(name = "try_on_jobs")
public class TryOnJobJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "garment_id", nullable = false)
    private UUID garmentId;

    @Column(name = "photo_url", nullable = false, columnDefinition = "TEXT")
    private String photoUrl;

    @Column(name = "garment_category", nullable = false, length = 32)
    private String garmentCategory;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "result_image_url", columnDefinition = "TEXT")
    private String resultImageUrl;

    @Column(name = "ai_provider", nullable = false, length = 32)
    private String aiProvider;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "photo_purged", nullable = false)
    private boolean photoPurged;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TryOnJobJpaEntity() {
    }

    public TryOnJobJpaEntity(
            UUID id,
            UUID userId,
            UUID garmentId,
            String photoUrl,
            String garmentCategory,
            String status,
            String resultImageUrl,
            String aiProvider,
            Long executionTimeMs,
            String errorMessage,
            boolean photoPurged,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.garmentId = garmentId;
        this.photoUrl = photoUrl;
        this.garmentCategory = garmentCategory;
        this.status = status;
        this.resultImageUrl = resultImageUrl;
        this.aiProvider = aiProvider;
        this.executionTimeMs = executionTimeMs;
        this.errorMessage = errorMessage;
        this.photoPurged = photoPurged;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getGarmentId() {
        return garmentId;
    }

    public void setGarmentId(UUID garmentId) {
        this.garmentId = garmentId;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getGarmentCategory() {
        return garmentCategory;
    }

    public void setGarmentCategory(String garmentCategory) {
        this.garmentCategory = garmentCategory;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultImageUrl() {
        return resultImageUrl;
    }

    public void setResultImageUrl(String resultImageUrl) {
        this.resultImageUrl = resultImageUrl;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isPhotoPurged() {
        return photoPurged;
    }

    public void setPhotoPurged(boolean photoPurged) {
        this.photoPurged = photoPurged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

