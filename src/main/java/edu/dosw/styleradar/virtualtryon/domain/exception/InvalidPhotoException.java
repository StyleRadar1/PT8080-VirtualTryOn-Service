package edu.dosw.styleradar.virtualtryon.domain.exception;

/**
 * Se lanza cuando la fotografía enviada no cumple con las validaciones biométricas o técnicas
 * (formato no admitido, excede tamaño máximo de 10MB o imagen corrupta).
 * Mapea a HTTP 422 Unprocessable Entity.
 */
public class InvalidPhotoException extends DomainException {

    public InvalidPhotoException(String reason) {
        super("Fotografía no válida para el probador virtual: " + reason, "INVALID_PHOTO");
    }
}
