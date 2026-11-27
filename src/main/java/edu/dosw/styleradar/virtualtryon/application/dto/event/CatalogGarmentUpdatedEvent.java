package edu.dosw.styleradar.virtualtryon.application.dto.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        Instant updatedAt,
        Set<String> availableSizes
) {

    private static final Set<String> HIDDEN_MODERATION_STATES = Set.of(
            "REJECTED", "RECHAZADA", "RECHAZADO",
            "WITHDRAWN", "RETIRADA", "RETIRADO",
            "HIDDEN", "OCULTA", "OCULTO",
            "INACTIVE", "INACTIVA", "INACTIVO",
            "SUSPENDED", "SUSPENDIDA", "SUSPENDIDO",
            "PENDING", "PENDIENTE"
    );

    public CatalogGarmentUpdatedEvent(
            UUID garmentId,
            String name,
            String category,
            String imageUrl,
            boolean visible,
            Instant updatedAt
    ) {
        this(garmentId, name, category, imageUrl, visible, updatedAt, Set.of());
    }

    @JsonCreator
    public static CatalogGarmentUpdatedEvent fromJson(
            @JsonProperty("garmentId") @JsonAlias({"itemId", "id", "prendaId"}) Object rawGarmentId,
            @JsonProperty("name") @JsonAlias({"nombrePrenda", "nombre", "titulo"}) String name,
            @JsonProperty("category") @JsonAlias({"categoria", "tipoPrenda", "tipo"}) String category,
            @JsonProperty("imageUrl") @JsonAlias({"imagenUrl", "fotoUrl", "url", "imagen"}) String imageUrl,
            @JsonProperty("visible") @JsonAlias({"activo", "activa", "disponible"}) Boolean visible,
            @JsonProperty("approved") @JsonAlias({"aprobada", "aprobado"}) Boolean approved,
            @JsonProperty("moderationStatus") @JsonAlias({"estadoModeracion", "estado", "moderationState"}) String moderationStatus,
            @JsonProperty("updatedAt") @JsonAlias({"fechaPublicacion", "fechaActualizacion", "fechaEvento", "fechaModeracion", "timestamp", "fecha"}) Object rawUpdatedAt,
            @JsonProperty("availableSizes") @JsonAlias({"tallas", "sizes", "tallasDisponibles"}) Set<String> availableSizes
    ) {
        UUID parsedGarmentId = GarmentId.parseToUuid(rawGarmentId);
        boolean isVisible = resolveVisibility(visible, approved, moderationStatus);
        Instant parsedUpdatedAt = parseFlexibleInstant(rawUpdatedAt);
        Set<String> sizes = availableSizes != null ? availableSizes : Set.of();
        return new CatalogGarmentUpdatedEvent(parsedGarmentId, name, category, imageUrl, isVisible, parsedUpdatedAt, sizes);
    }

    /**
     * Parsea marcas de tiempo flexibles recibidas desde otros microservicios:
     * soporta Instant ISO-8601 con 'Z', LocalDateTime sin zona (p. ej. RabbitMQPublisherAdapter de Catálogo)
     * y OffsetDateTime. Si el valor es nulo, vacío, espacios o malformado, retorna null de forma segura.
     */
    public static Instant parseFlexibleInstantOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Instant instant) {
            return instant;
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            // Intenta LocalDateTime sin zona (formato enviado por ms-item-catalogo: evento.fecha().toString())
            try {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            } catch (Exception ignoredLocal) {
                try {
                    return OffsetDateTime.parse(text).toInstant();
                } catch (Exception ignoredOffset) {
                    return null;
                }
            }
        }
    }

    /**
     * Parsea marcas de tiempo flexibles para prendas de catálogo:
     * soporta Instant ISO-8601 con 'Z', LocalDateTime sin zona (p. ej. RabbitMQPublisherAdapter de Catálogo)
     * y OffsetDateTime. Si es nulo o vacío devuelve Instant.now().
     * Si la fecha es malformada, propaga DateTimeParseException para evitar procesar eventos corruptos
     * o sobreescribir proyecciones recientes con marcas de tiempo generadas artificialmente.
     */
    public static Instant parseFlexibleInstant(Object raw) {
        if (raw == null) {
            return Instant.now();
        }
        if (raw instanceof Instant instant) {
            return instant;
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return Instant.now();
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            // Intenta LocalDateTime sin zona (formato enviado por ms-item-catalogo: evento.fecha().toString())
            try {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            } catch (Exception ignoredLocal) {
                return OffsetDateTime.parse(text).toInstant();
            }
        }
    }

    public static CatalogGarmentUpdatedEvent fromJson(
            Object rawGarmentId,
            String name,
            String category,
            String imageUrl,
            Boolean visible,
            Instant updatedAt
    ) {
        return fromJson(rawGarmentId, name, category, imageUrl, visible, null, null, updatedAt, Set.of());
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
}
