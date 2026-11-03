package edu.dosw.styleradar.virtualtryon.domain.exception;

import java.util.UUID;

/**
 * Excepción de dominio cuando una prenda seleccionada es incompatible o no existe en catálogo.
 * Asociada a la HU PT8080 · SR-RF-24 (Seleccionar prenda para prueba virtual).
 */
public class InvalidGarmentException extends DomainException {

    public InvalidGarmentException(String message) {
        super(message, "INVALID_GARMENT");
    }

    public InvalidGarmentException(UUID garmentId, String reason) {
        super("La prenda con identificador " + garmentId + " es incompatible para prueba virtual: " + reason, "INVALID_GARMENT");
    }
}

