package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA para la tabla biometric_consents en PostgreSQL 16 (SR-RF-23 / Ley 1581).
 */
@Entity
@Table(name = "biometric_consents")
public class BiometricConsentJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "terms_version", nullable = false, length = 64)
    private String termsVersion;

    @Column(name = "accepted", nullable = false)
    private boolean accepted;

    @Column(name = "purposes", nullable = false, columnDefinition = "TEXT")
    private String purposes;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    public BiometricConsentJpaEntity() {
    }

    public BiometricConsentJpaEntity(
            UUID id,
            UUID userId,
            String termsVersion,
            boolean accepted,
            String purposes,
            Instant consentedAt,
            Instant revokedAt,
            String ipAddress
    ) {
        this.id = id;
        this.userId = userId;
        this.termsVersion = termsVersion;
        this.accepted = accepted;
        this.purposes = purposes;
        this.consentedAt = consentedAt;
        this.revokedAt = revokedAt;
        this.ipAddress = ipAddress;
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

    public String getTermsVersion() {
        return termsVersion;
    }

    public void setTermsVersion(String termsVersion) {
        this.termsVersion = termsVersion;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getPurposes() {
        return purposes;
    }

    public void setPurposes(String purposes) {
        this.purposes = purposes;
    }

    public Instant getConsentedAt() {
        return consentedAt;
    }

    public void setConsentedAt(Instant consentedAt) {
        this.consentedAt = consentedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}

