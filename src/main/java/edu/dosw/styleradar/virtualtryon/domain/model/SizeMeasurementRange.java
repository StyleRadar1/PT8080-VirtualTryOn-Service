package edu.dosw.styleradar.virtualtryon.domain.model;

/**
 * Rango de medidas corporales admisibles para una talla específica de una prenda (SR-RF-24).
 * Valores en centímetros; null indica que la prenda no restringe esa dimensión anatómica.
 */
public record SizeMeasurementRange(
        Double minChestCm,
        Double maxChestCm,
        Double minWaistCm,
        Double maxWaistCm,
        Double minHipCm,
        Double maxHipCm
) {
    public static SizeMeasurementRange ofChest(double min, double max) {
        return new SizeMeasurementRange(min, max, null, null, null, null);
    }

    public static SizeMeasurementRange ofWaist(double min, double max) {
        return new SizeMeasurementRange(null, null, min, max, null, null);
    }

    public static SizeMeasurementRange ofWaistAndHip(double minWaist, double maxWaist, double minHip, double maxHip) {
        return new SizeMeasurementRange(null, null, minWaist, maxWaist, minHip, maxHip);
    }

    public static SizeMeasurementRange ofFullBody(double minChest, double maxChest, double minWaist, double maxWaist) {
        return new SizeMeasurementRange(minChest, maxChest, minWaist, maxWaist, null, null);
    }

    public boolean hasChestRange() {
        return minChestCm != null && maxChestCm != null;
    }

    public boolean hasWaistRange() {
        return minWaistCm != null && maxWaistCm != null;
    }

    public boolean hasHipRange() {
        return minHipCm != null && maxHipCm != null;
    }

    public boolean chestFits(Double chest) {
        if (chest == null || !hasChestRange()) {
            return false;
        }
        return chest >= minChestCm && chest <= maxChestCm;
    }

    public boolean waistFits(Double waist) {
        if (waist == null || !hasWaistRange()) {
            return false;
        }
        return waist >= minWaistCm && waist <= maxWaistCm;
    }

    public boolean hipFits(Double hip) {
        if (hip == null || !hasHipRange()) {
            return false;
        }
        return hip >= minHipCm && hip <= maxHipCm;
    }
}
