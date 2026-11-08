package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;

/**
 * Validador V02 de la cadena de responsabilidad: verifica que el usuario disponga de saldo
 * de pruebas virtuales en el período mensual de facturación vigente (SR-RF-24).
 */
public class MonthlyUserQuotaValidator extends AbstractTryOnValidatorHandler {

    @Override
    protected void doValidate(TryOnValidationContext context) {
        UserQuota quota = context.getUserQuota();
        if (quota == null) {
            throw new QuotaExceededException(
                    "No se encontró información de cuota mensual para el usuario " + context.getUserId().value()
            );
        }

        if (!quota.hasAvailableQuota()) {
            throw new QuotaExceededException(
                    quota.getUserId().value(),
                    quota.getUsedCount(),
                    quota.getMonthlyLimit()
            );
        }
    }
}
