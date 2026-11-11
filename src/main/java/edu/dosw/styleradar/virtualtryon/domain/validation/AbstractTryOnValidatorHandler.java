package edu.dosw.styleradar.virtualtryon.domain.validation;

/**
 * Clase base abstracta del patrón Chain of Responsibility que gestiona el enlace al siguiente
 * validador en la cadena y delega la validación específica a las subclases concretas.
 */
public abstract class AbstractTryOnValidatorHandler implements TryOnValidatorHandler {

    private TryOnValidatorHandler nextHandler;

    @Override
    public TryOnValidatorHandler setNext(TryOnValidatorHandler nextHandler) {
        this.nextHandler = nextHandler;
        return nextHandler;
    }

    @Override
    public final void validate(TryOnValidationContext context) {
        doValidate(context);
        if (nextHandler != null) {
            nextHandler.validate(context);
        }
    }

    /**
     * Método plantilla implementado por cada validador concreto de la cadena.
     */
    protected abstract void doValidate(TryOnValidationContext context);
}

