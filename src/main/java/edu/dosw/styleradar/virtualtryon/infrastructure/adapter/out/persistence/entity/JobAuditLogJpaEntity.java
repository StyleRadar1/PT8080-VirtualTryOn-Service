package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA para la tabla job_audit_logs que persiste las transiciones de estado de cada TryOnJob (SR-RF-25).
 */
@Entity
@Table(name = "job_audit_logs")
public class JobAuditLogJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(name = "transition_reason", nullable = false, length = 512)
    private String transitionReason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public JobAuditLogJpaEntity() {
    }

    public JobAuditLogJpaEntity(
            UUID id,
            UUID jobId,
            String previousStatus,
            String newStatus,
            String transitionReason,
            Instant occurredAt
    ) {
        this.id = id;
        this.jobId = jobId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.transitionReason = transitionReason;
        this.occurredAt = occurredAt;
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

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getTransitionReason() {
        return transitionReason;
    }

    public void setTransitionReason(String transitionReason) {
        this.transitionReason = transitionReason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}

