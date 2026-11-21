package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.application.dto.response.GarmentValidationResponse;
import edu.dosw.styleradar.virtualtryon.domain.validation.GarmentSelectionPolicy;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - GarmentValidationResult y GarmentValidationResponse (SR-RF-24)")
class GarmentValidationModelAndDtoTest {

    @Test
    @DisplayName("GarmentValidationResult cubre constructores, inmutabilidad y métodos de record")
    void shouldCoverGarmentValidationResultConstructorsAndMethods() {
        CatalogGarment garment = new CatalogGarment(
                GarmentId.of(UUID.randomUUID()),
                "Camiseta Básica",
                GarmentCategory.TOP,
                "https://storage/garment.png",
                true,
                Instant.now()
        );

        // Constructor completo con replacedGarmentIds no nulo
        GarmentId replacedId = GarmentId.of(UUID.randomUUID());
        GarmentValidationResult result1 = new GarmentValidationResult(
                garment,
                true,
                true,
                BodyZone.UPPER_BODY,
                true,
                "Compatible",
                List.of(replacedId),
                "M",
                true,
                "Ajuste adecuado",
                true
        );

        assertSame(garment, result1.garment());
        assertTrue(result1.compatible());
        assertTrue(result1.visible());
        assertEquals(BodyZone.UPPER_BODY, result1.zone());
        assertTrue(result1.eligibleForTryOn());
        assertEquals("Compatible", result1.validationMessage());
        assertEquals(List.of(replacedId), result1.replacedGarmentIds());
        assertEquals("M", result1.selectedSize());
        assertEquals(Boolean.TRUE, result1.sizeEligible());
        assertEquals("Ajuste adecuado", result1.fitMessage());
        assertTrue(result1.quotaAvailable());

        // Constructor con replacedGarmentIds = null -> se transforma en lista vacía
        GarmentValidationResult resultNullReplaced = new GarmentValidationResult(
                garment,
                true,
                true,
                BodyZone.UPPER_BODY,
                true,
                "Compatible",
                null,
                "L",
                false,
                "Ajuste ajustado",
                false
        );
        assertNotNull(resultNullReplaced.replacedGarmentIds());
        assertTrue(resultNullReplaced.replacedGarmentIds().isEmpty());

        // Constructor legacy de 7 argumentos
        GarmentValidationResult resultLegacy = new GarmentValidationResult(
                garment,
                true,
                true,
                BodyZone.UPPER_BODY,
                true,
                "Compatible legacy",
                List.of()
        );
        assertNull(resultLegacy.selectedSize());
        assertNull(resultLegacy.sizeEligible());
        assertNull(resultLegacy.fitMessage());
        assertTrue(resultLegacy.quotaAvailable());

        // Null garment lanza NullPointerException
        assertThrows(NullPointerException.class, () -> new GarmentValidationResult(
                null, true, true, BodyZone.UPPER_BODY, true, "msg", List.of()
        ));

        // Equals, hashCode y toString
        GarmentValidationResult copy = new GarmentValidationResult(
                garment, true, true, BodyZone.UPPER_BODY, true, "Compatible", List.of(replacedId),
                "M", true, "Ajuste adecuado", true
        );
        assertEquals(result1, copy);
        assertEquals(result1.hashCode(), copy.hashCode());
        assertNotNull(result1.toString());
    }

    @Test
    @DisplayName("GarmentValidationResponse cubre todos los constructores sobrecargados y accesores")
    void shouldCoverGarmentValidationResponseConstructorsAndGetters() {
        UUID garmentId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID repId = UUID.randomUUID();

        // 1. Constructor completo de 16 argumentos
        GarmentValidationResponse r16 = new GarmentValidationResponse(
                garmentId,
                "Pantalón Chino",
                "BOTTOM",
                "bottoms",
                "https://storage/chino.png",
                true,
                true,
                "LOWER_BODY",
                true,
                "Prenda lista para prueba",
                List.of(repId),
                now,
                "32",
                true,
                "Ajuste adecuado",
                true
        );

        assertEquals(garmentId, r16.garmentId());
        assertEquals("Pantalón Chino", r16.name());
        assertEquals("BOTTOM", r16.category());
        assertEquals("bottoms", r16.fashnCategoryCode());
        assertEquals("https://storage/chino.png", r16.imageUrl());
        assertTrue(r16.compatible());
        assertTrue(r16.visible());
        assertEquals("LOWER_BODY", r16.zone());
        assertTrue(r16.eligibleForTryOn());
        assertEquals("Prenda lista para prueba", r16.validationMessage());
        assertEquals(List.of(repId), r16.replacedGarmentIds());
        assertEquals(now, r16.updatedAt());
        assertEquals("32", r16.selectedSize());
        assertEquals(Boolean.TRUE, r16.sizeEligible());
        assertEquals("Ajuste adecuado", r16.fitMessage());
        assertTrue(r16.quotaAvailable());

        // 2. Constructor sobrecargado de 12 argumentos
        GarmentValidationResponse r12 = new GarmentValidationResponse(
                garmentId,
                "Pantalón Chino",
                "BOTTOM",
                "bottoms",
                "https://storage/chino.png",
                true,
                true,
                "LOWER_BODY",
                true,
                "Prenda lista",
                List.of(repId),
                now
        );
        assertNull(r12.selectedSize());
        assertNull(r12.sizeEligible());
        assertNull(r12.fitMessage());
        assertTrue(r12.quotaAvailable());

        // 3. Constructor sobrecargado de 7 argumentos
        GarmentValidationResponse r7 = new GarmentValidationResponse(
                garmentId,
                "Pantalón Chino",
                "BOTTOM",
                "bottoms",
                "https://storage/chino.png",
                true,
                now
        );
        assertTrue(r7.compatible());
        assertTrue(r7.visible());
        assertNull(r7.zone());
        assertTrue(r7.eligibleForTryOn());
        assertTrue(r7.replacedGarmentIds().isEmpty());

        // Equals, hashCode y toString
        assertNotNull(r16.toString());
        assertEquals(r16, new GarmentValidationResponse(
                garmentId, "Pantalón Chino", "BOTTOM", "bottoms", "https://storage/chino.png",
                true, true, "LOWER_BODY", true, "Prenda lista para prueba", List.of(repId), now,
                "32", true, "Ajuste adecuado", true
        ));
    }

    @Test
    @DisplayName("GarmentSelectionPolicy cubre sobrecarga de 2 y 6 argumentos")
    void shouldCoverGarmentSelectionPolicyOverloads() {
        CatalogGarment top = new CatalogGarment(
                GarmentId.of(UUID.randomUUID()),
                "Saco Negro",
                GarmentCategory.TOP,
                "https://storage/saco.png",
                true,
                Instant.now()
        );

        // 2 args
        GarmentValidationResult r2 = GarmentSelectionPolicy.evaluate(top, List.of());
        assertTrue(r2.compatible());
        assertTrue(r2.eligibleForTryOn());
        assertNull(r2.selectedSize());
        assertTrue(r2.quotaAvailable());

        // 6 args con valores
        GarmentValidationResult r6 = GarmentSelectionPolicy.evaluate(
                top, List.of(), "L", true, "Ajuste OK", false
        );
        assertEquals("L", r6.selectedSize());
        assertTrue(r6.sizeEligible());
        assertEquals("Ajuste OK", r6.fitMessage());
        assertFalse(r6.quotaAvailable());
    }

    @Test
    @DisplayName("GetUserQuotaUseCase default method validateGarmentEligibility con selectedSize")
    void shouldCoverGetUserQuotaUseCaseDefaultMethod() {
        edu.dosw.styleradar.virtualtryon.domain.port.in.GetUserQuotaUseCase useCase =
                new edu.dosw.styleradar.virtualtryon.domain.port.in.GetUserQuotaUseCase() {
                    @Override
                    public UserQuota getUserQuota(edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId userId) {
                        return null;
                    }
                    @Override
                    public GarmentValidationResult validateGarmentEligibility(
                            edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId userId,
                            GarmentId garmentId,
                            List<GarmentId> selectedGarmentIds
                    ) {
                        return null;
                    }
                    @Override
                    public CatalogGarment syncCatalogGarment(CatalogGarment garment) {
                        return null;
                    }
                };

        assertNull(useCase.validateGarmentEligibility(
                edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId.of(UUID.randomUUID()),
                GarmentId.of(UUID.randomUUID()),
                "M",
                List.of()
        ));
    }
}
