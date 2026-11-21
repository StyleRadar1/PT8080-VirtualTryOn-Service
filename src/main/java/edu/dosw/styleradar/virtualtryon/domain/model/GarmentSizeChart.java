package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tabla de medidas y tallas disponibles para una prenda del catálogo (RN-02, RN-07 · SR-RF-24).
 * availableSizes representa la disponibilidad comercial informada por Catálogo.
 * Si es null, Catálogo no informó la disponibilidad de tallas (se recurre a contingencia local).
 * Si es una colección (incluso vacía []), representa la fuente de verdad explícita de Catálogo.
 */
public record GarmentSizeChart(
        GarmentId garmentId,
        Set<String> availableSizes,
        Map<String, SizeMeasurementRange> sizeMeasurements
) {
    public GarmentSizeChart {
        Objects.requireNonNull(garmentId, "El garmentId no puede ser nulo");
        availableSizes = availableSizes != null ? Set.copyOf(availableSizes) : null;
        sizeMeasurements = sizeMeasurements != null ? Map.copyOf(sizeMeasurements) : Map.of();
    }

    /**
     * Indica si el catálogo informó explícitamente la disponibilidad comercial de tallas (incluso si la lista está vacía).
     */
    public boolean hasExplicitAvailableSizes() {
        return availableSizes != null;
    }

    public boolean offersSize(String size) {
        if (size == null || size.isBlank() || availableSizes == null) {
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
