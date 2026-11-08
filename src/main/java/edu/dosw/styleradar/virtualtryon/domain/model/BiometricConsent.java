package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa la autorización explícita de tratamiento de datos biométricos
 * otorgada por un usuario conforme a la Ley 1581 de 2012 (Habeas Data).
 */
public class BiometricConsent {

    private final UUID consentId;
    private final UserId userId;
    private final String termsVersion;
    private final boolean accepted;
    private final List<String> purposes;
    private final Instant consentedAt;
    private Instant revokedAt;
    private final String ipAddress;

    public BiometricConsent(
            UUID consentId,
            UserId userId,
            String termsVersion,
            boolean accepted,
            List<String> purposes,
            Instant consentedAt,
            Instant revokedAt,
            String ipAddress
    ) {
        this.consentId = Objects.requireNonNull(consentId, "El identificador del consentimiento no puede ser nulo");
        this.userId = Objects.requireNonNull(userId, "El identificador del usuario no puede ser nulo");
        if (termsVersion == null || termsVersion.isBlank()) {
            throw new IllegalArgumentException("La versión de los términos legales es obligatoria");
        }
        this.termsVersion = termsVersion.trim();
        this.accepted = accepted;
        this.purposes = purposes == null ? List.of("VIRTUAL_TRY_ON_INFERENCE") : List.copyOf(purposes);
        this.consentedAt = Objects.requireNonNull(consentedAt, "La fecha de consentimiento es obligatoria");
        this.revokedAt = revokedAt;
        this.ipAddress = ipAddress;
    }

    /**
     * Verifica si el consentimiento biométrico está activo y permite realizar inferencias o almacenar siluetas.
     */
    public boolean isValidForInference() {
        return accepted && revokedAt == null;
    }

    /**
     * Revoca el consentimiento biométrico registrando la marca de tiempo de revocación.
     */
    public void revoke(Instant revocationTime) {
        this.revokedAt = Objects.requireNonNull(revocationTime, "La fecha de revocación no puede ser nula");
    }

    public UUID getConsentId() {
        return consentId;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public List<String> getPurposes() {
        return purposes;
    }

    public Instant getConsentedAt() {
        return consentedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}

