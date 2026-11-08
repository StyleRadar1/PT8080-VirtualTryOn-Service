package edu.dosw.styleradar.virtualtryon.domain.event;

import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio inmutable emitido ante cambios de estado de un TryOnJob para publicación
 * asíncrona hacia el broker RabbitMQ (SR-RF-25).
 */
public record TryOnJobDomainEvent(
        UUID eventId,
        UUID jobId,
        UUID userId,
        UUID garmentId,
        JobStatus status,
        String resultImageUrl,
        String errorMessage,
        Instant occurredAt
) {

    public TryOnJobDomainEvent {
        Objects.requireNonNull(eventId, "eventId no puede ser nulo");
        Objects.requireNonNull(jobId, "jobId no puede ser nulo");
        Objects.requireNonNull(userId, "userId no puede ser nulo");
        Objects.requireNonNull(garmentId, "garmentId no puede ser nulo");
        Objects.requireNonNull(status, "status no puede ser nulo");
        Objects.requireNonNull(occurredAt, "occurredAt no puede ser nulo");
    }

    public static TryOnJobDomainEvent fromJob(TryOnJob job) {
        return new TryOnJobDomainEvent(
                UUID.randomUUID(),
                job.getId(),
                job.getUserId().value(),
                job.getGarmentId().value(),
                job.getStatus(),
                job.getResultImageUrl() != null ? job.getResultImageUrl().value() : null,
                job.getErrorMessage(),
                Instant.now()
        );
    }

    public String routingKey() {
        return switch (status) {
            case QUEUED, PROCESSING -> "tryon.job.created";
            case COMPLETED -> "tryon.job.completed";
            case FAILED, CANCELLED -> "tryon.job.failed";
        };
    }
}

