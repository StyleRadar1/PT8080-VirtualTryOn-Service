package edu.dosw.styleradar.virtualtryon.domain.exception;

import java.util.UUID;

/**
 * Se lanza cuando una prenda con identificador UUID válido no existe en el catálogo proyectado ni remoto.
 * Mapea a HTTP 404 Not Found.
 */
public class GarmentNotFoundException extends DomainException {

    public GarmentNotFoundException(UUID garmentId) {
        super("No se encontró la prenda con identificador: " + garmentId, "GARMENT_NOT_FOUND");
    }

    public GarmentNotFoundException(String message) {
        super(message, "GARMENT_NOT_FOUND");
    }
}
