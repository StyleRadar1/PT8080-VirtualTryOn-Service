package edu.dosw.styleradar.virtualtryon.domain.exception;

import java.util.UUID;

/**
 * Se lanza cuando el usuario no ha otorgado el consentimiento explícito de tratamiento
 * de datos biométricos exigido por la Ley 1581 de Habeas Data.
 * Mapea a HTTP 400 Bad Request.
 */
public class ConsentRequiredException extends DomainException {

    public ConsentRequiredException() {
        super("Es obligatorio aceptar el consentimiento informado de tratamiento de datos biométricos para utilizar el probador virtual.", "CONSENT_REQUIRED");
    }

    public ConsentRequiredException(String message) {
        super(message, "CONSENT_REQUIRED");
    }

    public ConsentRequiredException(UUID userId) {
        super(String.format("El usuario %s no ha otorgado el consentimiento informado de tratamiento de datos biométricos.", userId), "CONSENT_REQUIRED");
    }
}
