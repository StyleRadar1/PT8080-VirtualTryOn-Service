package edu.dosw.styleradar.virtualtryon.domain.model;

/**
 * Modelo de valor de dominio que representa las medidas antropométricas del usuario
 * para evaluar el ajuste orientativo de talla en el Probador Virtual (SR-RF-24).
 */
public record UserBodyMeasurements(
        Double heightCm,
        Double chestCm,
        Double waistCm,
        Double hipCm
) {
    public static UserBodyMeasurements empty() {
        return new UserBodyMeasurements(null, null, null, null);
    }

    public boolean hasSufficientMeasurements() {
        return (chestCm != null && chestCm > 0)
                || (waistCm != null && waistCm > 0)
                || (hipCm != null && hipCm > 0);
    }
}

