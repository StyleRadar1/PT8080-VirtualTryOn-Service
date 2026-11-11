package edu.dosw.styleradar.virtualtryon.domain.exception;

/**
 * Excepción base para todos los errores de regla de negocio en el dominio del probador virtual.
 * Hereda de RuntimeException para no obligar a declarar excepciones chequeadas.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    protected DomainException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
