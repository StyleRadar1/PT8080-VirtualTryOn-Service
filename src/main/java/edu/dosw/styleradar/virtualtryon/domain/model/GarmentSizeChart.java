package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tabla de medidas y tallas disponibles para una prenda del catálogo (RN-02, RN-07 · SR-RF-24).
 */
public record GarmentSizeChart(
        GarmentId garmentId,
        Set<String> availableSizes,
        Map<String, SizeMeasurementRange> sizeMeasurements
) {
    public GarmentSizeChart {
        Objects.requireNonNull(garmentId, "El garmentId no puede ser nulo");
        availableSizes = availableSizes != null ? Set.copyOf(availableSizes) : Set.of();
        sizeMeasurements = sizeMeasurements != null ? Map.copyOf(sizeMeasurements) : Map.of();
    }

    public boolean offersSize(String size) {
        if (size == null || size.isBlank()) {
            return false;
        }
        String normalized = size.trim().toUpperCase(Locale.ROOT);
        return availableSizes.stream().anyMatch(s -> s.equalsIgnoreCase(normalized));
    }

    public SizeMeasurementRange getMeasurementRange(String size) {
        if (size == null || size.isBlank()) {
            return null;
        }
        String normalized = size.trim().toUpperCase(Locale.ROOT);
        return sizeMeasurements.get(normalized);
    }
}
