package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA para la tabla user_quotas que persiste el límite y consumo mensual de pruebas virtuales (SR-RF-24).
 */
@Entity
@Table(name = "user_quotas")
public class UserQuotaJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "monthly_limit", nullable = false)
    private int monthlyLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "billing_period", nullable = false, length = 16)
    private String billingPeriod;

    @Column(name = "is_radar_plus", nullable = false)
    private boolean radarPlus;

    @Column(name = "last_reset_at", nullable = false)
    private Instant lastResetAt;

    public UserQuotaJpaEntity() {
    }

    public UserQuotaJpaEntity(
            UUID id,
            UUID userId,
            int monthlyLimit,
            int usedCount,
            String billingPeriod,
            boolean radarPlus,
            Instant lastResetAt
    ) {
        this.id = id;
        this.userId = userId;
        this.monthlyLimit = monthlyLimit;
        this.usedCount = usedCount;
        this.billingPeriod = billingPeriod;
        this.radarPlus = radarPlus;
        this.lastResetAt = lastResetAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public int getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(int monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public void setBillingPeriod(String billingPeriod) {
        this.billingPeriod = billingPeriod;
    }

    public boolean isRadarPlus() {
        return radarPlus;
    }

    public void setRadarPlus(boolean radarPlus) {
        this.radarPlus = radarPlus;
    }

    public Instant getLastResetAt() {
        return lastResetAt;
    }

    public void setLastResetAt(Instant lastResetAt) {
        this.lastResetAt = lastResetAt;
    }
}

