package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias para la entidad de dominio UserQuota y sus reglas de negocio (SR-RF-24 y SR-RF-25).
 */
class UserQuotaTest {

    @Test
    @DisplayName("HU SR-RF-24: Creación de cuota predeterminada gratuita con límite de 10 pruebas mensuales")
    void shouldCreateDefaultQuota() {
        UserId userId = UserId.of(UUID.randomUUID());
        BillingPeriod period = BillingPeriod.current();
        UserQuota quota = UserQuota.createDefault(userId, period);

        assertThat(quota.getId()).isNotNull();
        assertThat(quota.getUserId()).isEqualTo(userId);
        assertThat(quota.getMonthlyLimit()).isEqualTo(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT);
        assertThat(quota.getUsedCount()).isZero();
        assertThat(quota.getRemainingCount()).isEqualTo(10);
        assertThat(quota.hasAvailableQuota()).isTrue();
        assertThat(quota.isRadarPlus()).isFalse();
    }

    @Test
    @DisplayName("HU SR-RF-24: Consumo progresivo de cuota y lanzamiento de QuotaExceededException al agotar saldo")
    void shouldConsumeQuotaUntilExhausted() {
        UserId userId = UserId.of(UUID.randomUUID());
        BillingPeriod period = BillingPeriod.current();
        UserQuota quota = UserQuota.createDefault(userId, period);

        for (int i = 0; i < 10; i++) {
            assertThat(quota.hasAvailableQuota()).isTrue();
            quota.consumeQuota();
        }

        assertThat(quota.getUsedCount()).isEqualTo(10);
        assertThat(quota.getRemainingCount()).isZero();
        assertThat(quota.hasAvailableQuota()).isFalse();

        assertThatThrownBy(quota::consumeQuota)
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    @DisplayName("HU SR-RF-25: Reintegro de cuota (refundQuota) tras fallo en broker de mensajería")
    void shouldRefundQuotaCorrectly() {
        UserId userId = UserId.of(UUID.randomUUID());
        BillingPeriod period = BillingPeriod.current();
        UserQuota quota = UserQuota.createDefault(userId, period);

        quota.consumeQuota();
        assertThat(quota.getUsedCount()).isEqualTo(1);
        assertThat(quota.getRemainingCount()).isEqualTo(9);

        // Reintegro por compensación
        quota.refundQuota();
        assertThat(quota.getUsedCount()).isZero();
        assertThat(quota.getRemainingCount()).isEqualTo(10);

        // Reintegro adicional cuando ya está en cero no debe volverse negativo
        quota.refundQuota();
        assertThat(quota.getUsedCount()).isZero();
    }

    @Test
    @DisplayName("HU SR-RF-24: Actualización a suscripción Radar+ expande límite mensual a 50")
    void shouldUpgradeToRadarPlus() {
        UserId userId = UserId.of(UUID.randomUUID());
        BillingPeriod period = BillingPeriod.current();
        UserQuota quota = UserQuota.createDefault(userId, period);

        quota.upgradeToRadarPlus();
        assertThat(quota.isRadarPlus()).isTrue();
        assertThat(quota.getMonthlyLimit()).isEqualTo(UserQuota.RADAR_PLUS_MONTHLY_LIMIT);
        assertThat(quota.getRemainingCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("HU SR-RF-24: Reinicio de cuota por nuevo período de facturación")
    void shouldResetQuotaForNewBillingPeriod() {
        UserId userId = UserId.of(UUID.randomUUID());
        BillingPeriod period = BillingPeriod.of("2026-10");
        UserQuota quota = UserQuota.createDefault(userId, period);
        quota.consumeQuota();
        quota.consumeQuota();
        assertThat(quota.getUsedCount()).isEqualTo(2);

        quota.resetQuota("2026-11");
        assertThat(quota.getBillingPeriod().getValue()).isEqualTo("2026-11");
        assertThat(quota.getUsedCount()).isZero();
        assertThat(quota.getRemainingCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("HU SR-RF-24: Validaciones de invariantes en constructor de UserQuota")
    void shouldValidateInvariantsInConstructor() {
        UserId userId = UserId.of(UUID.randomUUID());
        BillingPeriod period = BillingPeriod.current();
        UUID quotaId = UUID.randomUUID();

        assertThatThrownBy(() -> new UserQuota(null, userId, 10, 0, period, false, Instant.now()))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new UserQuota(quotaId, null, 10, 0, period, false, Instant.now()))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new UserQuota(quotaId, userId, 0, 0, period, false, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new UserQuota(quotaId, userId, 10, -1, period, false, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new UserQuota(quotaId, userId, 10, 0, null, false, Instant.now()))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new UserQuota(quotaId, userId, 10, 0, period, false, null))
                .isInstanceOf(NullPointerException.class);
    }
}

