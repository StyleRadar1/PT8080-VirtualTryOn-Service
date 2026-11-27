package edu.dosw.styleradar.virtualtryon.domain.exception;

import java.util.UUID;

/**
 * Excepción de dominio cuando una prenda no está visible o su categoría no admite prueba virtual (FA-04 · SR-RF-24).
 * Retorna HTTP 422 Unprocessable Entity con código funcional GARMENT_NOT_ELIGIBLE.
 */
public class GarmentNotEligibleException extends InvalidGarmentException {

    public static final String DEFAULT_MESSAGE = "La prenda no está disponible o no admite prueba virtual.";

    public GarmentNotEligibleException() {
        super(DEFAULT_MESSAGE);
    }

    public GarmentNotEligibleException(String message) {
        super(message != null && !message.isBlank() ? message : DEFAULT_MESSAGE);
    }

    public GarmentNotEligibleException(UUID garmentId) {
        super(DEFAULT_MESSAGE);
    }

    @Override
    public String getErrorCode() {
        return "GARMENT_NOT_ELIGIBLE";
    }
}
