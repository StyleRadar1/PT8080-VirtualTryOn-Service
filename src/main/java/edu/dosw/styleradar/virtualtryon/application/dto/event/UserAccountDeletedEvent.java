package edu.dosw.styleradar.virtualtryon.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio entrante publicado cuando un usuario elimina su cuenta de StyleRadar
 * (user.account.deleted), disparando la purga definitiva de datos biométricos (Ley 1581 · SR-RF-27).
 */
public record UserAccountDeletedEvent(
        UUID userId,
        Instant deletedAt
) {
}
