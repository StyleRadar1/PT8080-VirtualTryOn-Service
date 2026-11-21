package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.UserBodyMeasurements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias Exhaustivas - GarmentSizeFitPolicy (SR-RF-24)")
class GarmentSizeFitPolicyTest {

    @Test
    @DisplayName("Cuando no hay medidas corporales (null), retorna sizeEligible=null y mensaje orientativo")
    void shouldReturnNullWhenMeasurementsAreNull() {
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, null);

        assertNotNull(result);
        assertEquals("M", result.normalizedSize());
        assertNull(result.sizeEligible(), "sizeEligible debe ser null cuando no hay medidas corporales");
        assertEquals(GarmentSizeFitPolicy.MSG_FIT_DATA_UNAVAILABLE, result.fitMessage());
    }

    @Test
    @DisplayName("Cuando las medidas corporales son vacías o insuficientes, retorna sizeEligible=null")
    void shouldReturnNullWhenMeasurementsAreEmpty() {
        UserBodyMeasurements empty = UserBodyMeasurements.empty();
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, empty);

        assertNotNull(result);
        assertEquals("M", result.normalizedSize());
        assertNull(result.sizeEligible(), "sizeEligible debe ser null cuando las medidas no son suficientes");
        assertEquals(GarmentSizeFitPolicy.MSG_FIT_DATA_UNAVAILABLE, result.fitMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Cuando la talla seleccionada está en blanco o vacía, retorna sizeEligible=null y mensaje de talla requerida")
    void shouldReturnSizeRequiredWhenSelectedSizeIsBlank(String blankSize) {
        UserBodyMeasurements measurements = new UserBodyMeasurements(175.0, 98.0, 82.0, 96.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate(blankSize, GarmentCategory.TOP, measurements);

        assertNotNull(result);
        assertNull(result.normalizedSize());
        assertNull(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_REQUIRED, result.fitMessage());
    }

    @Test
    @DisplayName("Cuando la talla seleccionada es null, retorna sizeEligible=null y mensaje de talla requerida")
    void shouldReturnSizeRequiredWhenSelectedSizeIsNull() {
        UserBodyMeasurements measurements = new UserBodyMeasurements(175.0, 98.0, 82.0, 96.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate(null, GarmentCategory.TOP, measurements);

        assertNotNull(result);
        assertNull(result.normalizedSize());
        assertNull(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_REQUIRED, result.fitMessage());
    }

    @Test
    @DisplayName("UPPER_BODY: Cobertura completa de todas las tallas XS, S, M, L, XL, XXL y fallback")
    void shouldCoverAllUpperBodySizesAndBranches() {
        // XS (< 88.0)
        UserBodyMeasurements xsFit = new UserBodyMeasurements(160.0, 84.0, 70.0, 88.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("XS", GarmentCategory.TOP, xsFit).sizeEligible());
        UserBodyMeasurements xsNoFit = new UserBodyMeasurements(160.0, 90.0, 70.0, 88.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("XS", GarmentCategory.TOP, xsNoFit).sizeEligible());

        // S (86.0 .. 94.0)
        UserBodyMeasurements sFit = new UserBodyMeasurements(165.0, 90.0, 72.0, 90.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("S", GarmentCategory.TOP, sFit).sizeEligible());
        UserBodyMeasurements sTooSmall = new UserBodyMeasurements(165.0, 82.0, 72.0, 90.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("S", GarmentCategory.TOP, sTooSmall).sizeEligible());
        UserBodyMeasurements sTooLarge = new UserBodyMeasurements(165.0, 98.0, 72.0, 90.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("S", GarmentCategory.TOP, sTooLarge).sizeEligible());

        // M (92.0 .. 102.0)
        UserBodyMeasurements mFit = new UserBodyMeasurements(175.0, 98.0, 80.0, 95.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, mFit).sizeEligible());
        UserBodyMeasurements mTooSmall = new UserBodyMeasurements(175.0, 88.0, 80.0, 95.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, mTooSmall).sizeEligible());
        UserBodyMeasurements mTooLarge = new UserBodyMeasurements(175.0, 105.0, 80.0, 95.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, mTooLarge).sizeEligible());

        // L (100.0 .. 110.0)
        UserBodyMeasurements lFit = new UserBodyMeasurements(180.0, 105.0, 88.0, 102.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("L", GarmentCategory.TOP, lFit).sizeEligible());
        UserBodyMeasurements lTooSmall = new UserBodyMeasurements(180.0, 95.0, 88.0, 102.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("L", GarmentCategory.TOP, lTooSmall).sizeEligible());
        UserBodyMeasurements lTooLarge = new UserBodyMeasurements(180.0, 115.0, 88.0, 102.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("L", GarmentCategory.TOP, lTooLarge).sizeEligible());

        // XL (>= 108.0)
        UserBodyMeasurements xlFit = new UserBodyMeasurements(185.0, 112.0, 94.0, 108.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("XL", GarmentCategory.TOP, xlFit).sizeEligible());
        UserBodyMeasurements xlNoFit = new UserBodyMeasurements(185.0, 102.0, 94.0, 108.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("XL", GarmentCategory.TOP, xlNoFit).sizeEligible());

        // XXL (>= 108.0)
        UserBodyMeasurements xxlFit = new UserBodyMeasurements(185.0, 120.0, 100.0, 115.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("XXL", GarmentCategory.TOP, xxlFit).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("XXL", GarmentCategory.TOP, xlNoFit).sizeEligible());

        // Default branch para talla fuera de switch (ej. 32 en UPPER_BODY) -> retorna true
        assertTrue(GarmentSizeFitPolicy.evaluate("32", GarmentCategory.TOP, mFit).sizeEligible());
    }

    @Test
    @DisplayName("UPPER_BODY: Fallback de targetMeasure cuando chestCm es null")
    void shouldFallBackToWaistOrConstantInUpperBody() {
        // chestCm == null, usa waistCm
        UserBodyMeasurements waistOnly = new UserBodyMeasurements(175.0, null, 98.0, 95.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, waistOnly).sizeEligible());

        // chestCm == null y waistCm == null, usa default 95.0
        UserBodyMeasurements hipOnly = new UserBodyMeasurements(175.0, null, null, 95.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, hipOnly).sizeEligible());
    }

    @Test
    @DisplayName("LOWER_BODY: Cobertura completa de todas las tallas XS/28, S/30, M/32, L/34, XL/XXL/36/38")
    void shouldCoverAllLowerBodySizesAndBranches() {
        // XS y 28 (< 76.0)
        UserBodyMeasurements xsFit = new UserBodyMeasurements(160.0, 85.0, 72.0, 85.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("XS", GarmentCategory.BOTTOM, xsFit).sizeEligible());
        assertTrue(GarmentSizeFitPolicy.evaluate("28", GarmentCategory.BOTTOM, xsFit).sizeEligible());
        UserBodyMeasurements xsNoFit = new UserBodyMeasurements(160.0, 85.0, 80.0, 85.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("XS", GarmentCategory.BOTTOM, xsNoFit).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("28", GarmentCategory.BOTTOM, xsNoFit).sizeEligible());

        // S y 30 (74.0 .. 82.0)
        UserBodyMeasurements sFit = new UserBodyMeasurements(168.0, 88.0, 78.0, 88.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("S", GarmentCategory.BOTTOM, sFit).sizeEligible());
        assertTrue(GarmentSizeFitPolicy.evaluate("30", GarmentCategory.BOTTOM, sFit).sizeEligible());
        UserBodyMeasurements sTooSmall = new UserBodyMeasurements(168.0, 88.0, 70.0, 88.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("S", GarmentCategory.BOTTOM, sTooSmall).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("30", GarmentCategory.BOTTOM, sTooSmall).sizeEligible());
        UserBodyMeasurements sTooLarge = new UserBodyMeasurements(168.0, 88.0, 86.0, 88.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("S", GarmentCategory.BOTTOM, sTooLarge).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("30", GarmentCategory.BOTTOM, sTooLarge).sizeEligible());

        // M y 32 (80.0 .. 90.0)
        UserBodyMeasurements mFit = new UserBodyMeasurements(175.0, 92.0, 85.0, 95.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("M", GarmentCategory.BOTTOM, mFit).sizeEligible());
        assertTrue(GarmentSizeFitPolicy.evaluate("32", GarmentCategory.BOTTOM, mFit).sizeEligible());
        UserBodyMeasurements mTooSmall = new UserBodyMeasurements(175.0, 92.0, 75.0, 95.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("M", GarmentCategory.BOTTOM, mTooSmall).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("32", GarmentCategory.BOTTOM, mTooSmall).sizeEligible());
        UserBodyMeasurements mTooLarge = new UserBodyMeasurements(175.0, 92.0, 95.0, 95.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("M", GarmentCategory.BOTTOM, mTooLarge).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("32", GarmentCategory.BOTTOM, mTooLarge).sizeEligible());

        // L y 34 (88.0 .. 98.0)
        UserBodyMeasurements lFit = new UserBodyMeasurements(180.0, 98.0, 92.0, 102.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("L", GarmentCategory.BOTTOM, lFit).sizeEligible());
        assertTrue(GarmentSizeFitPolicy.evaluate("34", GarmentCategory.BOTTOM, lFit).sizeEligible());
        UserBodyMeasurements lTooSmall = new UserBodyMeasurements(180.0, 98.0, 82.0, 102.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("L", GarmentCategory.BOTTOM, lTooSmall).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("34", GarmentCategory.BOTTOM, lTooSmall).sizeEligible());
        UserBodyMeasurements lTooLarge = new UserBodyMeasurements(180.0, 98.0, 102.0, 102.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("L", GarmentCategory.BOTTOM, lTooLarge).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("34", GarmentCategory.BOTTOM, lTooLarge).sizeEligible());

        // XL, XXL, 36, 38 (>= 96.0)
        UserBodyMeasurements xlFit = new UserBodyMeasurements(185.0, 105.0, 100.0, 110.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("XL", GarmentCategory.BOTTOM, xlFit).sizeEligible());
        assertTrue(GarmentSizeFitPolicy.evaluate("XXL", GarmentCategory.BOTTOM, xlFit).sizeEligible());
        assertTrue(GarmentSizeFitPolicy.evaluate("36", GarmentCategory.BOTTOM, xlFit).sizeEligible());
        assertTrue(GarmentSizeFitPolicy.evaluate("38", GarmentCategory.BOTTOM, xlFit).sizeEligible());

        UserBodyMeasurements xlNoFit = new UserBodyMeasurements(185.0, 105.0, 90.0, 110.0);
        assertFalse(GarmentSizeFitPolicy.evaluate("XL", GarmentCategory.BOTTOM, xlNoFit).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("XXL", GarmentCategory.BOTTOM, xlNoFit).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("36", GarmentCategory.BOTTOM, xlNoFit).sizeEligible());
        assertFalse(GarmentSizeFitPolicy.evaluate("38", GarmentCategory.BOTTOM, xlNoFit).sizeEligible());

        // Default branch en LOWER_BODY
        assertTrue(GarmentSizeFitPolicy.evaluate("CUSTOM", GarmentCategory.BOTTOM, mFit).sizeEligible());
    }

    @Test
    @DisplayName("LOWER_BODY: Fallback de targetMeasure cuando waistCm es null")
    void shouldFallBackToHipOrConstantInLowerBody() {
        // waistCm == null, usa hipCm
        UserBodyMeasurements hipOnly = new UserBodyMeasurements(175.0, 90.0, null, 85.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("32", GarmentCategory.BOTTOM, hipOnly).sizeEligible());

        // waistCm == null y hipCm == null, usa default 80.0
        UserBodyMeasurements chestOnly = new UserBodyMeasurements(175.0, 90.0, null, null);
        assertTrue(GarmentSizeFitPolicy.evaluate("30", GarmentCategory.BOTTOM, chestOnly).sizeEligible());
    }

    @Test
    @DisplayName("Cuando la categoría es null o no tiene BodyZone, retorna true por omisión")
    void shouldReturnTrueWhenCategoryOrZoneIsNull() {
        UserBodyMeasurements m = new UserBodyMeasurements(175.0, 90.0, 80.0, 90.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("M", null, m).sizeEligible());
    }

    @Test
    @DisplayName("FULL_BODY: Prenda ONE_PIECE (vestido/enterizo) retorna siempre sizeEligible=true")
    void shouldCoverFullBodyOnePiece() {
        UserBodyMeasurements m = new UserBodyMeasurements(170.0, 90.0, 75.0, 95.0);
        assertTrue(GarmentSizeFitPolicy.evaluate("M", GarmentCategory.ONE_PIECE, m).sizeEligible());
    }

    @Test
    @DisplayName("Validación estática de todas las tallas conocidas y casos inválidos")
    void shouldValidateAllKnownSizes() {
        String[] validSizes = {"XS", "S", "M", "L", "XL", "XXL", "28", "30", "32", "34", "36", "38"};
        for (String s : validSizes) {
            assertTrue(GarmentSizeFitPolicy.isValidSize(s));
            assertTrue(GarmentSizeFitPolicy.isValidSize(s.toLowerCase()));
        }
        assertFalse(GarmentSizeFitPolicy.isValidSize(""));
        assertFalse(GarmentSizeFitPolicy.isValidSize("   "));
        assertFalse(GarmentSizeFitPolicy.isValidSize(null));
        assertFalse(GarmentSizeFitPolicy.isValidSize("44"));
        assertFalse(GarmentSizeFitPolicy.isValidSize("XYZ"));
    }
}
