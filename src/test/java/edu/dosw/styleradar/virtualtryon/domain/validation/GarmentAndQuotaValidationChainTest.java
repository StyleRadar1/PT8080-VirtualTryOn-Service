package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotEligibleException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.domain.model.BodyZone;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentValidationResult;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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
                GarmentCategory.TOP,
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
                GarmentCategory.BOTTOM,
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
                GarmentCategory.ONE_PIECE,
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
                GarmentCategory.TOP,
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

        TryOnValidationContext outerwearContext = new TryOnValidationContext(
                userId,
                garmentId,
                GarmentCategory.OUTERWEAR,
                "https://styleradar.blob.core.windows.net/catalog/jacket-01.png",
                true,
                true,
                quota
        );

        assertThrows(GarmentNotEligibleException.class, () -> validationChain.validate(footwearContext));
        assertThrows(GarmentNotEligibleException.class, () -> validationChain.validate(accessoriesContext));
        assertThrows(GarmentNotEligibleException.class, () -> validationChain.validate(outerwearContext));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dada prenda oculta por moderación o sin imagen o sin categoría, Cuando se ejecuta GarmentCategoryValidator, Entonces lanza GarmentNotEligibleException o InvalidGarmentException")
    void shouldRejectHiddenOrImageLessOrUncategorizedGarment() {
        UserQuota quota = UserQuota.createDefault(userId, BillingPeriod.of("2026-11"));

        TryOnValidationContext missingIdContext = new TryOnValidationContext(
                userId, null, GarmentCategory.TOP, "https://styleradar.blob.core.windows.net/catalog/top.png", true, true, quota
        );
        TryOnValidationContext hiddenContext = new TryOnValidationContext(
                userId, garmentId, GarmentCategory.TOP, "https://styleradar.blob.core.windows.net/catalog/top.png", false, true, quota
        );
        TryOnValidationContext missingCategoryContext = new TryOnValidationContext(
                userId, garmentId, null, "https://styleradar.blob.core.windows.net/catalog/top.png", true, true, quota
        );
        TryOnValidationContext missingImageContext = new TryOnValidationContext(
                userId, garmentId, GarmentCategory.TOP, "   ", true, true, quota
        );

        assertThrows(InvalidGarmentException.class, () -> validationChain.validate(missingIdContext));
        assertThrows(GarmentNotEligibleException.class, () -> validationChain.validate(hiddenContext));
        assertThrows(GarmentNotEligibleException.class, () -> validationChain.validate(missingCategoryContext));
        assertThrows(GarmentNotEligibleException.class, () -> validationChain.validate(missingImageContext));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado GarmentCategory.fromRawValue con alias en español e inglés, Cuando se convierte, Entonces mapea correctamente y asigna código FASHN AI")
    void shouldMapGarmentCategoryAliasesAndFashnCodes() {
        assertEquals(GarmentCategory.TOP, GarmentCategory.fromRawValue("SUPERIOR"));
        assertEquals("tops", GarmentCategory.TOP.getFashnCategoryCode());
        assertEquals(BodyZone.UPPER_BODY, GarmentCategory.TOP.getBodyZone());
        assertTrue(GarmentCategory.TOP.isCompatibleWithTryOn());

        assertEquals(GarmentCategory.BOTTOM, GarmentCategory.fromRawValue("INFERIOR"));
        assertEquals("bottoms", GarmentCategory.BOTTOM.getFashnCategoryCode());
        assertEquals(BodyZone.LOWER_BODY, GarmentCategory.BOTTOM.getBodyZone());

        assertEquals(GarmentCategory.ONE_PIECE, GarmentCategory.fromRawValue("VESTIDO"));
        assertEquals(GarmentCategory.ONE_PIECE, GarmentCategory.fromRawValue("ONE-PIECE"));
        assertEquals("one-pieces", GarmentCategory.ONE_PIECE.getFashnCategoryCode());
        assertEquals(BodyZone.FULL_BODY, GarmentCategory.ONE_PIECE.getBodyZone());

        assertEquals(GarmentCategory.OUTERWEAR, GarmentCategory.fromRawValue("CHAQUETA"));
        assertFalse(GarmentCategory.OUTERWEAR.isCompatibleWithTryOn());
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
        assertEquals("2026-10", BillingPeriod.fromInstant(Instant.parse("2026-10-15T12:00:00Z")).getValue());
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
                GarmentCategory.ONE_PIECE,
                "https://styleradar.blob.core.windows.net/catalog/dress.png",
                true,
                Instant.now()
        );
        assertTrue(garment.isEligibleForTryOn());
        assertEquals("Vestido Largo Verano", garment.getName());
    }

    @Test
    @DisplayName("HU SR-RF-24 (RN-01 / RN-02 / FA-01 / FA-02 / FA-04): GarmentSelectionPolicy evalúa compatibilidad, visibilidad y reemplazos por zona corporal")
    void shouldEvaluateGarmentSelectionPolicyAndZoneReplacements() {
        CatalogGarment shirt = new CatalogGarment(
                GarmentId.of(UUID.randomUUID()), "Camisa", GarmentCategory.TOP, "https://cat/top.png", true, Instant.now());
        CatalogGarment blazer = new CatalogGarment(
                GarmentId.of(UUID.randomUUID()), "Blazer", GarmentCategory.TOP, "https://cat/top2.png", true, Instant.now());
        CatalogGarment jeans = new CatalogGarment(
                GarmentId.of(UUID.randomUUID()), "Jeans", GarmentCategory.BOTTOM, "https://cat/bottom.png", true, Instant.now());
        CatalogGarment dress = new CatalogGarment(
                GarmentId.of(UUID.randomUUID()), "Vestido", GarmentCategory.ONE_PIECE, "https://cat/dress.png", true, Instant.now());
        CatalogGarment jacket = new CatalogGarment(
                GarmentId.of(UUID.randomUUID()), "Chaqueta", GarmentCategory.OUTERWEAR, "https://cat/jacket.png", true, Instant.now());
        CatalogGarment hiddenTop = new CatalogGarment(
                GarmentId.of(UUID.randomUUID()), "Oculta", GarmentCategory.TOP, "https://cat/hidden.png", false, Instant.now());

        GarmentValidationResult topConflict = GarmentSelectionPolicy.evaluate(
                blazer, List.of(shirt, jeans, blazer, jacket)
        );
        assertTrue(topConflict.compatible());
        assertTrue(topConflict.visible());
        assertTrue(topConflict.eligibleForTryOn());
        assertEquals(BodyZone.UPPER_BODY, topConflict.zone());
        assertEquals(List.of(shirt.getGarmentId()), topConflict.replacedGarmentIds());

        GarmentValidationResult dressReplacesBoth = GarmentSelectionPolicy.evaluate(dress, List.of(shirt, jeans));
        assertTrue(dressReplacesBoth.eligibleForTryOn());
        assertEquals(BodyZone.FULL_BODY, dressReplacesBoth.zone());
        assertEquals(List.of(shirt.getGarmentId(), jeans.getGarmentId()), dressReplacesBoth.replacedGarmentIds());

        GarmentValidationResult shirtReplacesDress = GarmentSelectionPolicy.evaluate(shirt, List.of(dress));
        assertEquals(List.of(dress.getGarmentId()), shirtReplacesDress.replacedGarmentIds());

        GarmentValidationResult jeansWithShirt = GarmentSelectionPolicy.evaluate(jeans, List.of(shirt));
        assertTrue(jeansWithShirt.replacedGarmentIds().isEmpty());

        GarmentValidationResult outerwearResult = GarmentSelectionPolicy.evaluate(jacket, List.of(shirt));
        assertFalse(outerwearResult.compatible());
        assertTrue(outerwearResult.visible());
        assertFalse(outerwearResult.eligibleForTryOn());
        assertTrue(outerwearResult.replacedGarmentIds().isEmpty());
        assertThrows(InvalidGarmentException.class, () -> GarmentSelectionPolicy.assertEligible(jacket));

        GarmentValidationResult hiddenResult = GarmentSelectionPolicy.evaluate(hiddenTop, List.of(shirt));
        assertTrue(hiddenResult.compatible());
        assertFalse(hiddenResult.visible());
        assertFalse(hiddenResult.eligibleForTryOn());
        assertTrue(hiddenResult.replacedGarmentIds().isEmpty());
        assertThrows(InvalidGarmentException.class, () -> GarmentSelectionPolicy.assertEligible(hiddenTop));
        assertDoesNotThrow(() -> GarmentSelectionPolicy.assertEligible(shirt));
    }
}

