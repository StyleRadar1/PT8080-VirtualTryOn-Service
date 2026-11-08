package edu.dosw.styleradar.virtualtryon.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de evento asíncrono recibido desde RabbitMQ (routing key: catalog.garment.updated)
 * cuando el microservicio de catálogo crea o actualiza una prenda (SR-RF-24).
 */
public record CatalogGarmentUpdatedEvent(
        UUID garmentId,
        String name,
        String category,
        String imageUrl,
        boolean visible,
        Instant updatedAt
) {
}

