package edu.dosw.styleradar.virtualtryon.domain.exception;

/**
 * Excepción de dominio cuando no se ha seleccionado prenda, talla o fotografía personal válida (FA-01 · SR-RF-24).
 * Retorna HTTP 400 Bad Request con código funcional TRYON_SELECTION_INCOMPLETE.
 */
public class TryOnSelectionIncompleteException extends DomainException {

    public static final String DEFAULT_MESSAGE = "Selecciona una prenda, una talla y una fotografía válida.";

    public TryOnSelectionIncompleteException() {
        super(DEFAULT_MESSAGE, "TRYON_SELECTION_INCOMPLETE");
    }

    public TryOnSelectionIncompleteException(String message) {
        super(message != null && !message.isBlank() ? message : DEFAULT_MESSAGE, "TRYON_SELECTION_INCOMPLETE");
    }
}
