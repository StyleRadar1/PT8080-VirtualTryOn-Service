package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de dominio para la selección de prendas compatibles (GarmentCategoryValidator),
 * validación de cuotas mensuales (MonthlyUserQuotaValidator), Value Objects y entidades de SR-RF-24.
 */
class GarmentAndQuotaValidationChainTest {

    private TryOnValidatorHandler validationChain;
    private UserId userId;
    private GarmentId garmentId;

    @BeforeEach
    void setUp() {
        TryOnValidatorHandler consentValidator = new BiometricConsentValidator();
        TryOnValidatorHandler quotaValidator = new MonthlyUserQuotaValidator();
        TryOnValidatorHandler garmentValidator = new GarmentCategoryValidator();
        consentValidator.setNext(quotaValidator).setNext(garmentValidator);
        this.validationChain = consentValidator;

        this.userId = UserId.of(UUID.randomUUID());
        this.garmentId = GarmentId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado usuario con consentimiento, cuota disponible y prenda TOPS visible con foto, Cuando se valida la cadena, Entonces aprueba sin excepción")
    void shouldPassWhenConsentQuotaAndCompatibleGarmentAreValid() {
        UserQuota quota = UserQuota.createDefault(userId, BillingPeriod.of("2026-11"));
        TryOnValidationContext context = new TryOnValidationContext(
                userId,
                garmentId,
                GarmentCategory.TOPS,
                "https://styleradar.blob.core.windows.net/catalog/shirt-01.png",
                true,
                true,
                quota
        );

        assertDoesNotThrow(() -> validationChain.validate(context));
        assertTrue(context.hasBiometricConsent());
        assertEquals(0.0, context.getFileSizeMb());
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado usuario sin consentimiento biométrico, Cuando se valida la cadena de elegibilidad, Entonces lanza ConsentRequiredException")
    void shouldRejectWhenConsentIsNotAccepted() {
        UserQuota quota = UserQuota.createDefault(userId, BillingPeriod.of("2026-11"));
        TryOnValidationContext context = new TryOnValidationContext(
                userId,
                garmentId,
                GarmentCategory.BOTTOMS,
                "https://styleradar.blob.core.windows.net/catalog/pants-01.png",
                true,
                false,
                quota
        );

        assertThrows(ConsentRequiredException.class, () -> validationChain.validate(context));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado usuario con cuota mensual agotada (10/10), Cuando se valida la cadena, Entonces lanza QuotaExceededException")
    void shouldRejectWhenMonthlyQuotaIsExhausted() {
        UserQuota exhaustedQuota = new UserQuota(
                UUID.randomUUID(),
                userId,
                10,
                10,
                BillingPeriod.of("2026-11"),
                false,
                Instant.now()
        );
        TryOnValidationContext context = new TryOnValidationContext(
                userId,
                garmentId,
                GarmentCategory.ONE_PIECES,
                "https://styleradar.blob.core.windows.net/catalog/dress-01.png",
                true,
                true,
                exhaustedQuota
        );

        assertThrows(QuotaExceededException.class, () -> validationChain.validate(context));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado contexto sin objeto UserQuota, Cuando se ejecuta MonthlyUserQuotaValidator, Entonces lanza QuotaExceededException")
    void shouldRejectWhenUserQuotaIsMissingInContext() {
        TryOnValidationContext context = new TryOnValidationContext(
                userId,
                garmentId,
                GarmentCategory.TOPS,
                "https://styleradar.blob.core.windows.net/catalog/top-01.png",
                true,
                true,
                null
        );

        assertThrows(QuotaExceededException.class, () -> validationChain.validate(context));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dada prenda de categoría FOOTWEAR o ACCESSORIES incompatible, Cuando se ejecuta GarmentCategoryValidator, Entonces lanza InvalidGarmentException")
    void shouldRejectIncompatibleGarmentCategories() {
        UserQuota quota = UserQuota.createDefault(userId, BillingPeriod.of("2026-11"));
        TryOnValidationContext footwearContext = new TryOnValidationContext(
                userId,
                garmentId,
                GarmentCategory.FOOTWEAR,
                "https://styleradar.blob.core.windows.net/catalog/shoes-01.png",
                true,
                true,
                quota
        );
        TryOnValidationContext accessoriesContext = new TryOnValidationContext(
                userId,
                garmentId,
                GarmentCategory.ACCESSORIES,
                "https://styleradar.blob.core.windows.net/catalog/hat-01.png",
                true,
                true,
                quota
        );

        assertThrows(InvalidGarmentException.class, () -> validationChain.validate(footwearContext));
        assertThrows(InvalidGarmentException.class, () -> validationChain.validate(accessoriesContext));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dada prenda oculta por moderación o sin imagen o sin categoría, Cuando se ejecuta GarmentCategoryValidator, Entonces lanza InvalidGarmentException")
    void shouldRejectHiddenOrImageLessOrUncategorizedGarment() {
        UserQuota quota = UserQuota.createDefault(userId, BillingPeriod.of("2026-11"));

        TryOnValidationContext missingIdContext = new TryOnValidationContext(
                userId, null, GarmentCategory.TOPS, "https://styleradar.blob.core.windows.net/catalog/top.png", true, true, quota
        );
        TryOnValidationContext hiddenContext = new TryOnValidationContext(
                userId, garmentId, GarmentCategory.TOPS, "https://styleradar.blob.core.windows.net/catalog/top.png", false, true, quota
        );
        TryOnValidationContext missingCategoryContext = new TryOnValidationContext(
                userId, garmentId, null, "https://styleradar.blob.core.windows.net/catalog/top.png", true, true, quota
        );
        TryOnValidationContext missingImageContext = new TryOnValidationContext(
                userId, garmentId, GarmentCategory.TOPS, "   ", true, true, quota
        );

        assertThrows(InvalidGarmentException.class, () -> validationChain.validate(missingIdContext));
        assertThrows(InvalidGarmentException.class, () -> validationChain.validate(hiddenContext));
        assertThrows(InvalidGarmentException.class, () -> validationChain.validate(missingCategoryContext));
        assertThrows(InvalidGarmentException.class, () -> validationChain.validate(missingImageContext));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado GarmentCategory.fromRawValue con alias en español e inglés, Cuando se convierte, Entonces mapea correctamente y asigna código FASHN AI")
    void shouldMapGarmentCategoryAliasesAndFashnCodes() {
        assertEquals(GarmentCategory.TOPS, GarmentCategory.fromRawValue("SUPERIOR"));
        assertEquals("tops", GarmentCategory.TOPS.getFashnCategoryCode());
        assertTrue(GarmentCategory.TOPS.isCompatibleWithTryOn());

        assertEquals(GarmentCategory.BOTTOMS, GarmentCategory.fromRawValue("INFERIOR"));
        assertEquals("bottoms", GarmentCategory.BOTTOMS.getFashnCategoryCode());

        assertEquals(GarmentCategory.ONE_PIECES, GarmentCategory.fromRawValue("VESTIDO"));
        assertEquals("one-pieces", GarmentCategory.ONE_PIECES.getFashnCategoryCode());

        assertEquals(GarmentCategory.OUTERWEAR, GarmentCategory.fromRawValue("CHAQUETA"));
        assertEquals(GarmentCategory.FOOTWEAR, GarmentCategory.fromRawValue("CALZADO"));
        assertFalse(GarmentCategory.FOOTWEAR.isCompatibleWithTryOn());
        assertEquals(GarmentCategory.ACCESSORIES, GarmentCategory.fromRawValue("ACCESORIOS"));
        assertFalse(GarmentCategory.ACCESSORIES.isCompatibleWithTryOn());

        assertThrows(IllegalArgumentException.class, () -> GarmentCategory.fromRawValue(" "));
        assertThrows(IllegalArgumentException.class, () -> GarmentCategory.fromRawValue("PERFUME"));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dada entidad UserQuota y Value Objects BillingPeriod/GarmentId, Cuando se consumen créditos, reinician períodos o actualizan a Radar+, Entonces respetan invariantes")
    void shouldEnforceUserQuotaBillingPeriodAndCatalogGarmentInvariants() {
        BillingPeriod period = BillingPeriod.of("2026-11");
        assertEquals("2026-11", period.getValue());
        assertNotNull(BillingPeriod.current().getValue());
        assertThrows(IllegalArgumentException.class, () -> BillingPeriod.of("2026-13"));

        GarmentId parsedId = GarmentId.fromString(garmentId.getValue().toString());
        assertEquals(garmentId.getValue(), parsedId.getValue());

        UserQuota quota = UserQuota.createDefault(userId, period);
        assertEquals(10, quota.getMonthlyLimit());
        assertEquals(10, quota.getRemainingCount());
        assertFalse(quota.isRadarPlus());

        quota.consumeQuota();
        assertEquals(1, quota.getUsedCount());
        assertEquals(9, quota.getRemainingCount());

        quota.upgradeToRadarPlus();
        assertTrue(quota.isRadarPlus());
        assertEquals(50, quota.getMonthlyLimit());

        quota.resetQuota("2026-12");
        assertEquals(0, quota.getUsedCount());
        assertEquals("2026-12", quota.getBillingPeriod().getValue());

        CatalogGarment garment = new CatalogGarment(
                garmentId,
                " Vestido Largo Verano ",
                GarmentCategory.ONE_PIECES,
                "https://styleradar.blob.core.windows.net/catalog/dress.png",
                true,
                Instant.now()
        );
        assertTrue(garment.isEligibleForTryOn());
        assertEquals("Vestido Largo Verano", garment.getName());
    }
}

