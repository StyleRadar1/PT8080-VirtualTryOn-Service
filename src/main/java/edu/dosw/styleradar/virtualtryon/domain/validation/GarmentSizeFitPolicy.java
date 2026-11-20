package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.model.BodyZone;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.UserBodyMeasurements;

import java.util.Locale;
import java.util.Set;

/**
 * Política de dominio pura para evaluar el ajuste orientativo de la talla de una prenda
 * frente a las medidas antropométricas del usuario (RN-02, RN-03, RN-07, RN-08 · SR-RF-24).
 */
public final class GarmentSizeFitPolicy {

    public static final String MSG_FIT_DATA_UNAVAILABLE = "No hay medidas suficientes para evaluar el ajuste de esta talla.";
    public static final String MSG_SIZE_FIT_WARNING = "La talla seleccionada podría no ajustarse a tus medidas.";
    public static final String MSG_SIZE_FIT_SUCCESS = "Ajuste aproximado adecuado para tus medidas corporales.";
    public static final String MSG_SIZE_REQUIRED = "Selecciona una talla para evaluar el ajuste orientativo.";

    private static final Set<String> KNOWN_SIZES = Set.of(
            "XS", "S", "M", "L", "XL", "XXL", "28", "30", "32", "34", "36", "38"
    );

    public record SizeFitEvaluation(
            String normalizedSize,
            Boolean sizeEligible,
            String fitMessage
    ) {}

    private GarmentSizeFitPolicy() {
    }

    public static boolean isValidSize(String size) {
        if (size == null || size.isBlank()) {
            return false;
        }
        return KNOWN_SIZES.contains(size.trim().toUpperCase(Locale.ROOT));
    }

    public static SizeFitEvaluation evaluate(
            String size,
            GarmentCategory category,
            UserBodyMeasurements measurements
    ) {
        if (size == null || size.isBlank()) {
            return new SizeFitEvaluation(null, null, MSG_SIZE_REQUIRED);
        }

        String normalized = size.trim().toUpperCase(Locale.ROOT);

        // Si no hay medidas registradas para el usuario (caso sin datos controlado según RN-02 y RN-03)
        if (measurements == null || !measurements.hasSufficientMeasurements()) {
            return new SizeFitEvaluation(normalized, null, MSG_FIT_DATA_UNAVAILABLE);
        }

        boolean fits = checkFit(normalized, category, measurements);
        if (fits) {
            return new SizeFitEvaluation(normalized, true, MSG_SIZE_FIT_SUCCESS);
        } else {
            return new SizeFitEvaluation(normalized, false, MSG_SIZE_FIT_WARNING);
        }
    }

    private static boolean checkFit(String size, GarmentCategory category, UserBodyMeasurements m) {
        if (category == null || category.getBodyZone() == null) {
            return true;
        }
        BodyZone zone = category.getBodyZone();
        double targetMeasure;
        if (zone == BodyZone.UPPER_BODY) {
            targetMeasure = m.chestCm() != null ? m.chestCm() : (m.waistCm() != null ? m.waistCm() : 95.0);
            return switch (size) {
                case "XS" -> targetMeasure < 88.0;
                case "S" -> targetMeasure >= 86.0 && targetMeasure <= 94.0;
                case "M" -> targetMeasure >= 92.0 && targetMeasure <= 102.0;
                case "L" -> targetMeasure >= 100.0 && targetMeasure <= 110.0;
                case "XL", "XXL" -> targetMeasure >= 108.0;
                default -> true;
            };
        } else if (zone == BodyZone.LOWER_BODY) {
            targetMeasure = m.waistCm() != null ? m.waistCm() : (m.hipCm() != null ? m.hipCm() : 80.0);
            return switch (size) {
                case "XS", "28" -> targetMeasure < 76.0;
                case "S", "30" -> targetMeasure >= 74.0 && targetMeasure <= 82.0;
                case "M", "32" -> targetMeasure >= 80.0 && targetMeasure <= 90.0;
                case "L", "34" -> targetMeasure >= 88.0 && targetMeasure <= 98.0;
                case "XL", "XXL", "36", "38" -> targetMeasure >= 96.0;
                default -> true;
            };
        } else {
            // FULL_BODY
            return true;
        }
    }
}
