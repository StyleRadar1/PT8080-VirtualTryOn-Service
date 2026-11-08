package edu.dosw.styleradar.virtualtryon.domain.validation;

/**
 * Interfaz del patrón Chain of Responsibility para encadenar validadores de reglas de dominio
 * antes de almacenar la fotografía biométrica o ejecutar una inferencia virtual.
 */
public interface TryOnValidatorHandler {

    /**
     * Define el siguiente eslabón de la cadena de validación y lo retorna para encadenamiento fluido.
     */
    TryOnValidatorHandler setNext(TryOnValidatorHandler nextHandler);

    /**
     * Ejecuta la validación sobre el contexto actual y delega al siguiente validador si existe.
     */
    void validate(TryOnValidationContext context);
}

