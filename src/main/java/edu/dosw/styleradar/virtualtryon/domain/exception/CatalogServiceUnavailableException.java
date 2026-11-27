package edu.dosw.styleradar.virtualtryon.domain.exception;

/**
 * Excepción de dominio cuando el microservicio de catálogo no responde a tiempo
 * o devuelve un fallo temporal de disponibilidad (5xx / timeout) durante la hidratación REST.
 */
public class CatalogServiceUnavailableException extends DomainException {

    public CatalogServiceUnavailableException(String message) {
        super(message, "CATALOG_SERVICE_UNAVAILABLE");
    }

    public CatalogServiceUnavailableException(String message, Throwable cause) {
        super(message, "CATALOG_SERVICE_UNAVAILABLE", cause);
    }
}
