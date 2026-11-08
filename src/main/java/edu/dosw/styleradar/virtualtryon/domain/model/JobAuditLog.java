package edu.dosw.styleradar.virtualtryon.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que registra cada transición de estado de un TryOnJob con fines de
 * trazabilidad, auditoría y cumplimiento normativo (SR-RF-25).
 */
public class JobAuditLog {

    private final UUID id;
    private final UUID jobId;
    private final JobStatus previousStatus;
    private final JobStatus newStatus;
    private final String transitionReason;
    private final Instant occurredAt;

    public JobAuditLog(
            UUID id,
            UUID jobId,
            JobStatus previousStatus,
            JobStatus newStatus,
            String transitionReason,
            Instant occurredAt
    ) {
        this.id = Objects.requireNonNull(id, "El id del registro de auditoría no puede ser nulo");
        this.jobId = Objects.requireNonNull(jobId, "El identificador del trabajo no puede ser nulo");
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus, "El nuevo estado no puede ser nulo");
        if (transitionReason == null || transitionReason.isBlank()) {
            throw new IllegalArgumentException("El motivo de transición de estado no puede ser nulo o vacío");
        }
        this.transitionReason = transitionReason.trim();
        this.occurredAt = Objects.requireNonNull(occurredAt, "La fecha del evento de auditoría no puede ser nula");
    }

    public static JobAuditLog ofTransition(UUID jobId, JobStatus previousStatus, JobStatus newStatus, String reason) {
        return new JobAuditLog(UUID.randomUUID(), jobId, previousStatus, newStatus, reason, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public JobStatus getPreviousStatus() {
        return previousStatus;
    }

    public JobStatus getNewStatus() {
        return newStatus;
    }

    public String getTransitionReason() {
        return transitionReason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}

