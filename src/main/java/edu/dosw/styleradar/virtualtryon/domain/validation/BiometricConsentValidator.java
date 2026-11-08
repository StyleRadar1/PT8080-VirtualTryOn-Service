package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;

/**
 * Validador V01 (SR-RF-23 / SR-RNF-02): Verifica que el usuario haya otorgado el consentimiento
 * informado explícito de tratamiento de datos biométricos conforme a la Ley 1581 de 2012.
 */
public class BiometricConsentValidator extends AbstractTryOnValidatorHandler {

    @Override
    protected void doValidate(TryOnValidationContext context) {
        if (!context.isBiometricConsentAccepted()) {
            throw new ConsentRequiredException(context.getUserId().value());
        }
    }
}

