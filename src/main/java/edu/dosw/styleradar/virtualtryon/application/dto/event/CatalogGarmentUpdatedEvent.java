package edu.dosw.styleradar.virtualtryon.application.dto.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de evento asíncrono recibido desde RabbitMQ (routing key: styleradar.catalogo.prenda.publicada)
 * cuando el microservicio de catálogo crea o actualiza una prenda (SR-RF-24).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogGarmentUpdatedEvent(
        UUID garmentId,
        String name,
        String category,
        String imageUrl,
        boolean visible,
        Instant updatedAt
) {

    @JsonCreator
    public static CatalogGarmentUpdatedEvent fromJson(
            @JsonProperty("garmentId") @JsonAlias({"itemId", "id", "prendaId"}) Object rawGarmentId,
            @JsonProperty("name") @JsonAlias({"nombrePrenda", "nombre", "titulo"}) String name,
            @JsonProperty("category") @JsonAlias({"categoria", "tipoPrenda"}) String category,
            @JsonProperty("imageUrl") @JsonAlias({"imagenUrl", "fotoUrl", "url", "imagen"}) String imageUrl,
            @JsonProperty("visible") @JsonAlias({"activo", "disponible"}) Boolean visible,
            @JsonProperty("updatedAt") @JsonAlias({"fechaPublicacion", "fechaActualizacion", "fechaEvento", "timestamp"}) Instant updatedAt
    ) {
        UUID parsedGarmentId = parseFlexibleUuid(rawGarmentId);
        boolean isVisible = visible == null || visible;
        Instant parsedUpdatedAt = updatedAt != null ? updatedAt : Instant.now();
        return new CatalogGarmentUpdatedEvent(parsedGarmentId, name, category, imageUrl, isVisible, parsedUpdatedAt);
    }

    private static UUID parseFlexibleUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        String str = raw.toString().trim();
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(str.getBytes(StandardCharsets.UTF_8));
        }
    }
}

