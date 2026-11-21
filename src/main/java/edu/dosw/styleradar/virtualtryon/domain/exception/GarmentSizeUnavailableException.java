package edu.dosw.styleradar.virtualtryon.domain.exception;

/**
 * Excepción de dominio cuando la talla seleccionada no está disponible para la prenda en el catálogo (RN-07 · SR-RF-24).
 * Retorna HTTP 400 Bad Request con código funcional TRYON_SIZE_UNAVAILABLE.
 */
public class GarmentSizeUnavailableException extends DomainException {

    public GarmentSizeUnavailableException(String size) {
        super("La talla '" + size + "' no está disponible para la prenda seleccionada.", "TRYON_SIZE_UNAVAILABLE");
    }

    public GarmentSizeUnavailableException(String size, String message) {
        super(message != null && !message.isBlank() ? message : "La talla '" + size + "' no está disponible para la prenda seleccionada.", "TRYON_SIZE_UNAVAILABLE");
    }
}
