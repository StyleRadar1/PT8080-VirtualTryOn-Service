package edu.dosw.styleradar.virtualtryon.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio inmutable emitido ante la purga física y definitiva de una fotografía biométrica
 * y/o render de prueba virtual en cumplimiento de la Ley 1581 de 2012 (Habeas Data · SR-RF-27).
 */
public record PhotoPurgedDomainEvent(
        UUID eventId,
        UUID jobId,
        UUID userId,
        String routingKey,
        Instant occurredAt
) {
    public static final String ROUTING_KEY = "tryon.photo.purged";

    public PhotoPurgedDomainEvent {
        Objects.requireNonNull(eventId, "eventId no puede ser nulo");
        Objects.requireNonNull(userId, "userId no puede ser nulo");
        Objects.requireNonNull(routingKey, "routingKey no puede ser nulo");
        Objects.requireNonNull(occurredAt, "occurredAt no puede ser nulo");
    }

    public static PhotoPurgedDomainEvent of(UUID jobId, UUID userId) {
        return new PhotoPurgedDomainEvent(
                UUID.randomUUID(),
                jobId,
                userId,
                ROUTING_KEY,
                Instant.now()
        );
    }
}

