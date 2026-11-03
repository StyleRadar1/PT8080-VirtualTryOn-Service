package edu.dosw.styleradar.virtualtryon.domain.exception;

/**
 * Excepción de dominio cuando el proveedor de IA (FASHN AI) excede el tiempo límite o no responde.
 * Asociada a la HU PT8080 · SR-RF-25 (Generar visualización aproximada de la prenda).
 */
public class AiProviderTimeoutException extends DomainException {

    public AiProviderTimeoutException(String message) {
        super(message, "AI_PROVIDER_TIMEOUT");
    }
}

