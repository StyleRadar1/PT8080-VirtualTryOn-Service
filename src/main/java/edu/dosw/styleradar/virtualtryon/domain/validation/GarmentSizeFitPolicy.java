package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.model.BodyZone;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentSizeChart;
import edu.dosw.styleradar.virtualtryon.domain.model.SizeMeasurementRange;
import edu.dosw.styleradar.virtualtryon.domain.model.UserBodyMeasurements;

import java.util.Locale;
import java.util.Set;

/**
 * Política de dominio pura para evaluar el ajuste orientativo de la talla de una prenda
 * frente a las medidas antropométricas autorizadas del usuario y la tabla de medidas del catálogo
 * (RN-02, RN-03, RN-07, RN-08 · SR-RF-24 · FA-02, FA-03).
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

    /**
     * Evalúa el ajuste orientativo utilizando las medidas corporales del usuario y la tabla de medidas de la prenda.
     * Solo compara medidas pertinentes para la zona corporal de la prenda; si faltan datos comparables,
     * retorna sizeEligible = null (FA-02). Si existen datos comparables y no ajustan, retorna false (FA-03).
     */
    public static SizeFitEvaluation evaluate(
            String size,
            GarmentCategory category,
            UserBodyMeasurements measurements,
            GarmentSizeChart sizeChart
    ) {
        if (size == null || size.isBlank()) {
            return new SizeFitEvaluation(null, null, MSG_SIZE_REQUIRED);
        }

        String normalized = size.trim().toUpperCase(Locale.ROOT);

        // Si faltan medidas corporales de Usuarios o la tabla de medidas de Catálogo -> FA-02
        if (measurements == null || sizeChart == null || category == null || category.getBodyZone() == null) {
            return new SizeFitEvaluation(normalized, null, MSG_FIT_DATA_UNAVAILABLE);
        }

        SizeMeasurementRange range = sizeChart.getMeasurementRange(normalized);
        if (range == null) {
            return new SizeFitEvaluation(normalized, null, MSG_FIT_DATA_UNAVAILABLE);
        }

        BodyZone zone = category.getBodyZone();
        return checkFitByPertinentMeasurements(normalized, zone, measurements, range);
    }

    /**
     * Sobrecarga de compatibilidad: sin tabla de medidas específica de Catálogo, retorna datos insuficientes (FA-02).
     */
    public static SizeFitEvaluation evaluate(
            String size,
            GarmentCategory category,
            UserBodyMeasurements measurements
    ) {
        return evaluate(size, category, measurements, null);
    }

    private static SizeFitEvaluation checkFitByPertinentMeasurements(
            String normalizedSize,
            BodyZone zone,
            UserBodyMeasurements m,
            SizeMeasurementRange range
    ) {
        if (zone == BodyZone.UPPER_BODY) {
            // Solo compara medida de pecho/torso contra el rango de pecho de la prenda
            if (m.chestCm() == null || m.chestCm() <= 0 || !range.hasChestRange()) {
                return new SizeFitEvaluation(normalizedSize, null, MSG_FIT_DATA_UNAVAILABLE);
            }
            boolean fits = range.chestFits(m.chestCm());
            return fits
                    ? new SizeFitEvaluation(normalizedSize, true, MSG_SIZE_FIT_SUCCESS)
                    : new SizeFitEvaluation(normalizedSize, false, MSG_SIZE_FIT_WARNING);
        }

        if (zone == BodyZone.LOWER_BODY) {
            // Compara cintura y/o cadera pertinentes contra el rango de la prenda
            boolean evaluatedAtLeastOne = false;
            if (range.hasWaistRange()) {
                if (m.waistCm() == null || m.waistCm() <= 0) {
                    return new SizeFitEvaluation(normalizedSize, null, MSG_FIT_DATA_UNAVAILABLE);
                }
                if (!range.waistFits(m.waistCm())) {
                    return new SizeFitEvaluation(normalizedSize, false, MSG_SIZE_FIT_WARNING);
                }
                evaluatedAtLeastOne = true;
            }
            if (range.hasHipRange()) {
                if (m.hipCm() == null || m.hipCm() <= 0) {
                    return new SizeFitEvaluation(normalizedSize, null, MSG_FIT_DATA_UNAVAILABLE);
                }
                if (!range.hipFits(m.hipCm())) {
                    return new SizeFitEvaluation(normalizedSize, false, MSG_SIZE_FIT_WARNING);
                }
                evaluatedAtLeastOne = true;
            }
            if (!evaluatedAtLeastOne) {
                return new SizeFitEvaluation(normalizedSize, null, MSG_FIT_DATA_UNAVAILABLE);
            }
            return new SizeFitEvaluation(normalizedSize, true, MSG_SIZE_FIT_SUCCESS);
        }

        // FULL_BODY: Requiere comparar medidas pertinentes (pecho y/o cintura); no aprueba a ciegas
        boolean evaluatedAtLeastOne = false;
        if (range.hasChestRange()) {
            if (m.chestCm() == null || m.chestCm() <= 0) {
                return new SizeFitEvaluation(normalizedSize, null, MSG_FIT_DATA_UNAVAILABLE);
            }
            if (!range.chestFits(m.chestCm())) {
                return new SizeFitEvaluation(normalizedSize, false, MSG_SIZE_FIT_WARNING);
            }
            evaluatedAtLeastOne = true;
        }
        if (range.hasWaistRange()) {
            if (m.waistCm() == null || m.waistCm() <= 0) {
                return new SizeFitEvaluation(normalizedSize, null, MSG_FIT_DATA_UNAVAILABLE);
            }
            if (!range.waistFits(m.waistCm())) {
                return new SizeFitEvaluation(normalizedSize, false, MSG_SIZE_FIT_WARNING);
            }
            evaluatedAtLeastOne = true;
        }
        if (!evaluatedAtLeastOne) {
            return new SizeFitEvaluation(normalizedSize, null, MSG_FIT_DATA_UNAVAILABLE);
        }
        return new SizeFitEvaluation(normalizedSize, true, MSG_SIZE_FIT_SUCCESS);
    }
}
