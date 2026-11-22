package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que controla el saldo y consumo mensual de cuotas de prueba virtual de un usuario (SR-RF-24).
 */
public class UserQuota {

    public static final int DEFAULT_FREE_MONTHLY_LIMIT = 10;
    public static final int RADAR_PLUS_MONTHLY_LIMIT = 50;

    private final UUID id;
    private final UserId userId;
    private int monthlyLimit;
    private int usedCount;
    private BillingPeriod billingPeriod;
    private boolean radarPlus;
    private Instant lastResetAt;
    private Instant planUpdatedAt;

    public UserQuota(
            UUID id,
            UserId userId,
            int monthlyLimit,
            int usedCount,
            BillingPeriod billingPeriod,
            boolean radarPlus,
            Instant lastResetAt
    ) {
        this(id, userId, monthlyLimit, usedCount, billingPeriod, radarPlus, lastResetAt, null);
    }

    public UserQuota(
            UUID id,
            UserId userId,
            int monthlyLimit,
            int usedCount,
            BillingPeriod billingPeriod,
            boolean radarPlus,
            Instant lastResetAt,
            Instant planUpdatedAt
    ) {
        this.id = Objects.requireNonNull(id, "El id de la cuota no puede ser nulo");
        this.userId = Objects.requireNonNull(userId, "El identificador del usuario no puede ser nulo");
        if (monthlyLimit <= 0) {
            throw new IllegalArgumentException("El límite mensual de cuota debe ser mayor que cero");
        }
        if (usedCount < 0) {
            throw new IllegalArgumentException("El conteo de uso de cuota no puede ser negativo");
        }
        this.monthlyLimit = monthlyLimit;
        this.usedCount = usedCount;
        this.billingPeriod = Objects.requireNonNull(billingPeriod, "El período de facturación no puede ser nulo");
        this.radarPlus = radarPlus;
        this.lastResetAt = Objects.requireNonNull(lastResetAt, "La fecha de último reinicio no puede ser nula");
        this.planUpdatedAt = planUpdatedAt;
    }

    public static UserQuota createDefault(UserId userId, BillingPeriod billingPeriod) {
        return new UserQuota(
                UUID.randomUUID(),
                userId,
                DEFAULT_FREE_MONTHLY_LIMIT,
                0,
                billingPeriod,
                false,
                Instant.now(),
                null
        );
    }

    public static UserQuota createForNewPeriod(UserId userId, BillingPeriod billingPeriod, UserQuota previousQuota) {
        if (previousQuota == null || !previousQuota.isRadarPlus()) {
            UserQuota fresh = createDefault(userId, billingPeriod);
            if (previousQuota != null) {
                fresh.setPlanUpdatedAt(previousQuota.getPlanUpdatedAt());
            }
            return fresh;
        }
        return new UserQuota(
                UUID.randomUUID(),
                userId,
                RADAR_PLUS_MONTHLY_LIMIT,
                0,
                billingPeriod,
                true,
                Instant.now(),
                previousQuota.getPlanUpdatedAt()
        );
    }

    public boolean hasAvailableQuota() {
        return getRemainingCount() > 0;
    }

    public int getRemainingCount() {
        return Math.max(0, monthlyLimit - usedCount);
    }

    public void consumeQuota() {
        if (!hasAvailableQuota()) {
            throw new QuotaExceededException(userId.value(), usedCount, monthlyLimit);
        }
        this.usedCount++;
    }

    public void refundQuota() {
        if (this.usedCount > 0) {
            this.usedCount--;
        }
    }

    public void resetQuota(String newPeriod) {
        this.billingPeriod = BillingPeriod.of(newPeriod);
        this.usedCount = 0;
        this.lastResetAt = Instant.now();
    }

    public void upgradeToRadarPlus() {
        this.radarPlus = true;
        this.monthlyLimit = RADAR_PLUS_MONTHLY_LIMIT;
    }

    public void upgradeToRadarPlus(Instant occurredAt) {
        upgradeToRadarPlus();
        if (occurredAt != null) {
            this.planUpdatedAt = occurredAt;
        }
    }

    public void downgradeToFreePlan() {
        this.radarPlus = false;
        this.monthlyLimit = DEFAULT_FREE_MONTHLY_LIMIT;
    }

    public void downgradeToFreePlan(Instant occurredAt) {
        downgradeToFreePlan();
        if (occurredAt != null) {
            this.planUpdatedAt = occurredAt;
        }
    }

    public UUID getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public int getMonthlyLimit() {
        return monthlyLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public boolean isRadarPlus() {
        return radarPlus;
    }

    public Instant getLastResetAt() {
        return lastResetAt;
    }

    public Instant getPlanUpdatedAt() {
        return planUpdatedAt;
    }

    public void setPlanUpdatedAt(Instant planUpdatedAt) {
        this.planUpdatedAt = planUpdatedAt;
    }
}

