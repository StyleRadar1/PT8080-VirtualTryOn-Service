package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raíz del dominio que encapsula el estado, metadatos e invariantes de una
 * solicitud de prueba virtual con Inteligencia Artificial (SR-RF-25).
 */
public class TryOnJob {

    private final UUID id;
    private final UserId userId;
    private final GarmentId garmentId;
    private String photoUrl;
    private final GarmentCategory garmentCategory;
    private final String garmentImageUrl;
    private JobStatus status;
    private SasSignedUrl resultImageUrl;
    private final AiProvider aiProvider;
    private Long executionTimeMs;
    private String errorMessage;
    private boolean photoPurged;
    private final Instant createdAt;
    private Instant updatedAt;

    public TryOnJob(
            UUID id,
            UserId userId,
            GarmentId garmentId,
            String photoUrl,
            GarmentCategory garmentCategory,
            String garmentImageUrl,
            JobStatus status,
            SasSignedUrl resultImageUrl,
            AiProvider aiProvider,
            Long executionTimeMs,
            String errorMessage,
            boolean photoPurged,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "El id del trabajo (TryOnJob) no puede ser nulo");
        this.userId = Objects.requireNonNull(userId, "El identificador del usuario no puede ser nulo");
        this.garmentId = Objects.requireNonNull(garmentId, "El identificador de la prenda no puede ser nulo");
        if (!photoPurged && (photoUrl == null || photoUrl.isBlank())) {
            throw new IllegalArgumentException("La URL de la fotografía frontal no puede ser nula o vacía");
        }
        this.photoUrl = photoUrl;
        this.garmentCategory = Objects.requireNonNull(garmentCategory, "La categoría de la prenda no puede ser nula");
        this.garmentImageUrl = garmentImageUrl;
        this.status = Objects.requireNonNull(status, "El estado del trabajo no puede ser nulo");
        this.resultImageUrl = resultImageUrl;
        this.aiProvider = Objects.requireNonNull(aiProvider, "El proveedor de IA no puede ser nulo");
        this.executionTimeMs = executionTimeMs;
        this.errorMessage = errorMessage;
        this.photoPurged = photoPurged;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula");
    }

    public TryOnJob(
            UUID id,
            UserId userId,
            GarmentId garmentId,
            String photoUrl,
            GarmentCategory garmentCategory,
            JobStatus status,
            SasSignedUrl resultImageUrl,
            AiProvider aiProvider,
            Long executionTimeMs,
            String errorMessage,
            boolean photoPurged,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, userId, garmentId, photoUrl, garmentCategory, null, status, resultImageUrl, aiProvider, executionTimeMs, errorMessage, photoPurged, createdAt, updatedAt);
    }

    public void startAiProcessing() {
        this.status = JobStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void complete(SasSignedUrl renderUrl, Long elapsedMs) {
        this.resultImageUrl = Objects.requireNonNull(renderUrl, "La URL firmada del renderizado no puede ser nula");
        this.executionTimeMs = elapsedMs != null && elapsedMs >= 0 ? elapsedMs : 0L;
        this.errorMessage = null;
        this.status = JobStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.errorMessage = (reason == null || reason.isBlank()) ? "Error en el procesamiento de inferencia IA" : reason.trim();
        this.status = JobStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void purgeBiometricPhoto() {
        this.photoUrl = "PURGED_BY_USER_REQUEST";
        this.resultImageUrl = null;
        this.photoPurged = true;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public GarmentId getGarmentId() {
        return garmentId;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public GarmentCategory getGarmentCategory() {
        return garmentCategory;
    }

    public String getGarmentImageUrl() {
        return garmentImageUrl;
    }

    public JobStatus getStatus() {
        return status;
    }

    public SasSignedUrl getResultImageUrl() {
        return resultImageUrl;
    }

    public AiProvider getAiProvider() {
        return aiProvider;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isPhotoPurged() {
        return photoPurged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

