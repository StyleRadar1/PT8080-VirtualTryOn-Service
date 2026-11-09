package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa el resultado visual renderizado de alta resolución (Outfit)
 * asociado a un trabajo de prueba virtual completado, incluyendo su URL firmada de solo lectura
 * y su tiempo de expiración (SR-RF-26).
 */
public class TryOnOutfit {

    public static final Duration DEFAULT_SAS_VALIDITY = Duration.ofMinutes(15);

    private final UUID id;
    private final UUID jobId;
    private final UserId userId;
    private SasSignedUrl finalRenderUrl;
    private Instant expiresAt;
    private final Instant createdAt;

    public TryOnOutfit(
            UUID id,
            UUID jobId,
            UserId userId,
            SasSignedUrl finalRenderUrl,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "El id del outfit no puede ser nulo");
        this.jobId = Objects.requireNonNull(jobId, "El id del trabajo (jobId) no puede ser nulo");
        this.userId = Objects.requireNonNull(userId, "El identificador del usuario no puede ser nulo");
        this.finalRenderUrl = Objects.requireNonNull(finalRenderUrl, "La URL firmada del renderizado no puede ser nula");
        this.expiresAt = Objects.requireNonNull(expiresAt, "La fecha de expiración de la URL no puede ser nula");
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula");
    }

    public static TryOnOutfit createForCompletedJob(UUID jobId, UserId userId, SasSignedUrl finalRenderUrl) {
        Instant now = Instant.now();
        return new TryOnOutfit(
                UUID.randomUUID(),
                jobId,
                userId,
                finalRenderUrl,
                now.plus(DEFAULT_SAS_VALIDITY),
                now
        );
    }

    public boolean isUrlExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void refreshSignedUrl(SasSignedUrl refreshedUrl) {
        this.finalRenderUrl = Objects.requireNonNull(refreshedUrl, "La nueva URL firmada no puede ser nula");
        this.expiresAt = Instant.now().plus(DEFAULT_SAS_VALIDITY);
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UserId getUserId() {
        return userId;
    }

    public SasSignedUrl getFinalRenderUrl() {
        return finalRenderUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

