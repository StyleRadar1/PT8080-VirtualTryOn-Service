package edu.dosw.styleradar.virtualtryon.application.dto.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * DTO de evento asíncrono recibido desde RabbitMQ (routing keys:
 * {@code styleradar.catalogo.prenda.publicada}, {@code styleradar.catalogo.prenda.retirada}
 * y {@code styleradar.catalogo.moderacion.cambiada}) cuando el microservicio de catálogo
 * publica, retira o cambia el estado de moderación de una prenda (SR-RF-24).
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

    private static final Set<String> HIDDEN_MODERATION_STATES = Set.of(
            "REJECTED", "RECHAZADA", "RECHAZADO",
            "WITHDRAWN", "RETIRADA", "RETIRADO",
            "HIDDEN", "OCULTA", "OCULTO",
            "INACTIVE", "INACTIVA", "INACTIVO",
            "SUSPENDED", "SUSPENDIDA", "SUSPENDIDO",
            "PENDING", "PENDIENTE"
    );

    @JsonCreator
    public static CatalogGarmentUpdatedEvent fromJson(
            @JsonProperty("garmentId") @JsonAlias({"itemId", "id", "prendaId"}) Object rawGarmentId,
            @JsonProperty("name") @JsonAlias({"nombrePrenda", "nombre", "titulo"}) String name,
            @JsonProperty("category") @JsonAlias({"categoria", "tipoPrenda"}) String category,
            @JsonProperty("imageUrl") @JsonAlias({"imagenUrl", "fotoUrl", "url", "imagen"}) String imageUrl,
            @JsonProperty("visible") @JsonAlias({"activo", "activa", "disponible"}) Boolean visible,
            @JsonProperty("approved") @JsonAlias({"aprobada", "aprobado"}) Boolean approved,
            @JsonProperty("moderationStatus") @JsonAlias({"estadoModeracion", "estado", "moderationState"}) String moderationStatus,
            @JsonProperty("updatedAt") @JsonAlias({"fechaPublicacion", "fechaActualizacion", "fechaEvento", "timestamp"}) Instant updatedAt
    ) {
        UUID parsedGarmentId = parseFlexibleUuid(rawGarmentId);
        boolean isVisible = resolveVisibility(visible, approved, moderationStatus);
        Instant parsedUpdatedAt = updatedAt != null ? updatedAt : Instant.now();
        return new CatalogGarmentUpdatedEvent(parsedGarmentId, name, category, imageUrl, isVisible, parsedUpdatedAt);
    }

    public static CatalogGarmentUpdatedEvent fromJson(
            Object rawGarmentId,
            String name,
            String category,
            String imageUrl,
            Boolean visible,
            Instant updatedAt
    ) {
        return fromJson(rawGarmentId, name, category, imageUrl, visible, null, null, updatedAt);
    }

    private static boolean resolveVisibility(Boolean visible, Boolean approved, String moderationStatus) {
        if (Boolean.FALSE.equals(visible) || Boolean.FALSE.equals(approved)) {
            return false;
        }
        if (moderationStatus != null && !moderationStatus.isBlank()) {
            String normalizedStatus = moderationStatus.trim().toUpperCase(Locale.ROOT);
            if (HIDDEN_MODERATION_STATES.contains(normalizedStatus)) {
                return false;
            }
        }
        return visible == null || visible;
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

