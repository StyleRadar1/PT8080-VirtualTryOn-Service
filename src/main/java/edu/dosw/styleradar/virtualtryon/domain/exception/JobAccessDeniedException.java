package edu.dosw.styleradar.virtualtryon.domain.exception;

/**
 * Se lanza cuando un usuario intenta consultar o manipular un trabajo de prueba virtual
 * que no le pertenece y no posee privilegios de administrador (prevención de IDOR).
 * Mapea a HTTP 403 Forbidden.
 */
public class JobAccessDeniedException extends DomainException {

    public JobAccessDeniedException(String message) {
        super(message, "ACCESS_DENIED");
    }
}

