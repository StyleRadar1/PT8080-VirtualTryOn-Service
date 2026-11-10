package edu.dosw.styleradar.virtualtryon.domain.exception;

/**
 * Excepción de dominio cuando el proveedor de IA (FASHN AI) rechaza definitivamente la solicitud
 * por credenciales inválidas, parámetros erróneos, ejecución fallida o imágenes incompatibles.
 * Representa un error permanente no recuperable mediante reintentos.
 */
public class AiInferencePermanentException extends DomainException {

    public AiInferencePermanentException(String message) {
        super("Fallo permanente del proveedor de IA: " + message, "AI_INFERENCE_PERMANENT_ERROR");
    }

    public AiInferencePermanentException(String message, Throwable cause) {
        super("Fallo permanente del proveedor de IA: " + message, "AI_INFERENCE_PERMANENT_ERROR", cause);
    }
}

