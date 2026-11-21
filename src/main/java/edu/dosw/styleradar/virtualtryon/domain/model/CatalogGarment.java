package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Entidad de dominio que representa la proyección local de una prenda del catálogo
 * sincronizada para validar elegibilidad en el Probador Virtual (SR-RF-24).
 */
public class CatalogGarment {

    private final GarmentId garmentId;
    private final String name;
    private final GarmentCategory category;
    private final String imageUrl;
    private final boolean visible;
    private final Instant updatedAt;
    private final Set<String> availableSizes;

    public CatalogGarment(
            GarmentId garmentId,
            String name,
            GarmentCategory category,
            String imageUrl,
            boolean visible,
            Instant updatedAt,
            Set<String> availableSizes
    ) {
        this.garmentId = Objects.requireNonNull(garmentId, "El identificador de la prenda no puede ser nulo");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la prenda no puede ser nulo o vacío");
        }
        this.name = name.trim();
        this.category = Objects.requireNonNull(category, "La categoría de la prenda no puede ser nula");
        this.imageUrl = (imageUrl == null || imageUrl.isBlank()) ? null : imageUrl.trim();
        this.visible = visible;
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula");
        this.availableSizes = availableSizes != null ? Set.copyOf(availableSizes) : Set.of();
    }

    public CatalogGarment(
            GarmentId garmentId,
            String name,
            GarmentCategory category,
            String imageUrl,
            boolean visible,
            Instant updatedAt
    ) {
        this(garmentId, name, category, imageUrl, visible, updatedAt, Set.of());
    }

    public boolean hasValidImage() {
        return imageUrl != null && !imageUrl.isBlank();
    }

    public boolean isEligibleForTryOn() {
        return visible && category.isCompatibleWithTryOn() && hasValidImage();
    }

    public boolean offersSize(String size) {
        if (size == null || size.isBlank()) {
            return false;
        }
        if (availableSizes.isEmpty()) {
            return true;
        }
        String normalized = size.trim().toUpperCase(Locale.ROOT);
        return availableSizes.stream().anyMatch(s -> s.equalsIgnoreCase(normalized));
    }

    public Set<String> getAvailableSizes() {
        return availableSizes;
    }

    public GarmentId getGarmentId() {
        return garmentId;
    }

    public String getName() {
        return name;
    }

    public GarmentCategory getCategory() {
        return category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isVisible() {
        return visible;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
