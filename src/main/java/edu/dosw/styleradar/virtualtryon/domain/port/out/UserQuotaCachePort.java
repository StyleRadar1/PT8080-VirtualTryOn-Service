package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.OptionalInt;

/**
 * Puerto de salida (Driven Port) para el control volátil de cuotas mensuales y contadores
 * rápidos en Redis (probador-redis) (SR-RF-24).
 */
public interface UserQuotaCachePort {

    OptionalInt getCachedUsedCount(UserId userId, BillingPeriod billingPeriod);

    void cacheUsedCount(UserId userId, BillingPeriod billingPeriod, int usedCount);

    int incrementUsedCount(UserId userId, BillingPeriod billingPeriod);

    void evictQuota(UserId userId, BillingPeriod billingPeriod);
}

