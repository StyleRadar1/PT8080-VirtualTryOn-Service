package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotEligibleException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentSizeUnavailableException;
import edu.dosw.styleradar.virtualtryon.domain.exception.TryOnSelectionIncompleteException;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentSizeChart;
import edu.dosw.styleradar.virtualtryon.domain.model.SizeMeasurementRange;
import edu.dosw.styleradar.virtualtryon.domain.model.UserBodyMeasurements;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias Exhaustivas - GarmentSizeFitPolicy (SR-RF-24)")
class GarmentSizeFitPolicyTest {

    private GarmentId garmentId;
    private GarmentSizeChart upperBodyChart;
    private GarmentSizeChart lowerBodyChart;
    private GarmentSizeChart fullBodyChart;

    @BeforeEach
    void setUp() {
        garmentId = GarmentId.of(UUID.randomUUID());

        // Tabla para prenda superior (TOP): XS (<88), S (86-94), M (92-102), L (100-110), XL (108-120)
        upperBodyChart = new GarmentSizeChart(
                garmentId,
                Set.of("XS", "S", "M", "L", "XL"),
                Map.of(
                        "XS", SizeMeasurementRange.ofChest(70.0, 87.9),
                        "S", SizeMeasurementRange.ofChest(86.0, 94.0),
                        "M", SizeMeasurementRange.ofChest(92.0, 102.0),
                        "L", SizeMeasurementRange.ofChest(100.0, 110.0),
                        "XL", SizeMeasurementRange.ofChest(108.0, 120.0)
                )
        );

        // Tabla para prenda inferior (BOTTOM): 28/XS, 30/S, 32/M (cintura 80-90, cadera 95-105)
        lowerBodyChart = new GarmentSizeChart(
                garmentId,
                Set.of("28", "30", "32", "34", "S", "M", "L"),
                Map.of(
                        "28", SizeMeasurementRange.ofWaist(70.0, 75.9),
                        "30", SizeMeasurementRange.ofWaist(74.0, 82.0),
                        "32", SizeMeasurementRange.ofWaistAndHip(80.0, 90.0, 95.0, 105.0),
                        "M", SizeMeasurementRange.ofWaistAndHip(80.0, 90.0, 95.0, 105.0)
                )
        );

        // Tabla para cuerpo completo (ONE_PIECE): S (pecho 86-94, cintura 68-76)
        fullBodyChart = new GarmentSizeChart(
                garmentId,
                Set.of("S", "M"),
                Map.of(
                        "S", SizeMeasurementRange.ofFullBody(86.0, 94.0, 68.0, 76.0),
                        "M", SizeMeasurementRange.ofFullBody(92.0, 100.0, 74.0, 82.0)
                )
        );
    }

    @Test
    @DisplayName("Cuando no hay medidas corporales (null), retorna sizeEligible=null y mensaje orientativo (FA-02)")
    void shouldReturnNullWhenMeasurementsAreNull() {
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, null, upperBodyChart);

        assertNotNull(result);
        assertEquals("M", result.normalizedSize());
        assertNull(result.sizeEligible(), "sizeEligible debe ser null cuando no hay medidas corporales");
        assertEquals(GarmentSizeFitPolicy.MSG_FIT_DATA_UNAVAILABLE, result.fitMessage());
    }

    @Test
    @DisplayName("Cuando no hay tabla de medidas en Catálogo (null), retorna sizeEligible=null (FA-02)")
    void shouldReturnNullWhenSizeChartIsNull() {
        UserBodyMeasurements measurements = new UserBodyMeasurements(175.0, 96.0, 80.0, 95.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, measurements, null);

        assertNotNull(result);
        assertEquals("M", result.normalizedSize());
        assertNull(result.sizeEligible(), "sizeEligible debe ser null cuando no hay tabla de catálogo");
        assertEquals(GarmentSizeFitPolicy.MSG_FIT_DATA_UNAVAILABLE, result.fitMessage());
    }

    @Test
    @DisplayName("Sobrecarga de 3 argumentos sin sizeChart delega a sizeChart=null y retorna FA-02")
    void shouldReturnNullWithThreeArgOverload() {
        UserBodyMeasurements measurements = new UserBodyMeasurements(175.0, 96.0, 80.0, 95.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, measurements);

        assertNotNull(result);
        assertNull(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_FIT_DATA_UNAVAILABLE, result.fitMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Cuando la talla seleccionada está en blanco o vacía, retorna sizeEligible=null y mensaje de talla requerida")
    void shouldReturnSizeRequiredWhenSelectedSizeIsBlank(String blankSize) {
        UserBodyMeasurements measurements = new UserBodyMeasurements(175.0, 98.0, 82.0, 96.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate(blankSize, GarmentCategory.TOP, measurements, upperBodyChart);

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
                GarmentSizeFitPolicy.evaluate(null, GarmentCategory.TOP, measurements, upperBodyChart);

        assertNotNull(result);
        assertNull(result.normalizedSize());
        assertNull(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_REQUIRED, result.fitMessage());
    }

    @Test
    @DisplayName("UPPER_BODY: Compara estrictamente medida de pecho; si falta pecho, retorna null (sin falsos positivos)")
    void shouldEvaluateUpperBodyWithPertinentChestMeasurementOnly() {
        // Usuario solo con cadera y cintura (pecho null): NO debe inferir 95cm; debe retornar null
        UserBodyMeasurements onlyWaistAndHip = new UserBodyMeasurements(170.0, null, 75.0, 95.0);
        GarmentSizeFitPolicy.SizeFitEvaluation noChestResult =
                GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, onlyWaistAndHip, upperBodyChart);
        assertNull(noChestResult.sizeEligible(), "Sin medida de pecho debe retornar null para prenda superior");
        assertEquals(GarmentSizeFitPolicy.MSG_FIT_DATA_UNAVAILABLE, noChestResult.fitMessage());

        // Pecho compatible (95cm en rango M: 92-102) -> true
        UserBodyMeasurements fitM = new UserBodyMeasurements(175.0, 95.0, 80.0, 95.0);
        GarmentSizeFitPolicy.SizeFitEvaluation fitResult =
                GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, fitM, upperBodyChart);
        assertTrue(fitResult.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_FIT_SUCCESS, fitResult.fitMessage());

        // Pecho incompatible (108cm en talla M: 92-102) -> false (FA-03)
        UserBodyMeasurements unfitM = new UserBodyMeasurements(175.0, 108.0, 80.0, 95.0);
        GarmentSizeFitPolicy.SizeFitEvaluation unfitResult =
                GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, unfitM, upperBodyChart);
        assertFalse(unfitResult.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_FIT_WARNING, unfitResult.fitMessage());
    }

    @Test
    @DisplayName("LOWER_BODY: Compara cintura y/o cadera pertinentes; si faltan datos comparables retorna null")
    void shouldEvaluateLowerBodyWithPertinentWaistOrHipMeasurements() {
        // Talla 30 solo restringe cintura (74-82cm)
        UserBodyMeasurements fitWaist = new UserBodyMeasurements(170.0, null, 78.0, null);
        GarmentSizeFitPolicy.SizeFitEvaluation fit30 =
                GarmentSizeFitPolicy.evaluate("30", GarmentCategory.BOTTOM, fitWaist, lowerBodyChart);
        assertTrue(fit30.sizeEligible());

        UserBodyMeasurements unfitWaist = new UserBodyMeasurements(170.0, null, 90.0, null);
        GarmentSizeFitPolicy.SizeFitEvaluation unfit30 =
                GarmentSizeFitPolicy.evaluate("30", GarmentCategory.BOTTOM, unfitWaist, lowerBodyChart);
        assertFalse(unfit30.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_FIT_WARNING, unfit30.fitMessage());

        // Talla 32 restringe cintura (80-90) y cadera (95-105). Si cadera no cumple -> false
        UserBodyMeasurements waistOkHipBad = new UserBodyMeasurements(170.0, 90.0, 85.0, 115.0);
        GarmentSizeFitPolicy.SizeFitEvaluation hipBadResult =
                GarmentSizeFitPolicy.evaluate("32", GarmentCategory.BOTTOM, waistOkHipBad, lowerBodyChart);
        assertFalse(hipBadResult.sizeEligible());

        // Si falta cintura en talla que la requiere -> null
        UserBodyMeasurements missingWaist = new UserBodyMeasurements(170.0, 90.0, null, 100.0);
        GarmentSizeFitPolicy.SizeFitEvaluation missingWaistResult =
                GarmentSizeFitPolicy.evaluate("32", GarmentCategory.BOTTOM, missingWaist, lowerBodyChart);
        assertNull(missingWaistResult.sizeEligible());

        // Si falta cadera en talla que la requiere -> null
        UserBodyMeasurements missingHip = new UserBodyMeasurements(170.0, 90.0, 85.0, null);
        GarmentSizeFitPolicy.SizeFitEvaluation missingHipResult =
                GarmentSizeFitPolicy.evaluate("32", GarmentCategory.BOTTOM, missingHip, lowerBodyChart);
        assertNull(missingHipResult.sizeEligible());
    }

    @Test
    @DisplayName("FULL_BODY: Compara pecho y cintura del vestido; nunca retorna true a ciegas")
    void shouldEvaluateFullBodyWithoutBlindApproval() {
        // Vestido S requiere pecho 86-94 y cintura 68-76
        UserBodyMeasurements fitDress = new UserBodyMeasurements(165.0, 90.0, 72.0, 92.0);
        GarmentSizeFitPolicy.SizeFitEvaluation fitResult =
                GarmentSizeFitPolicy.evaluate("S", GarmentCategory.ONE_PIECE, fitDress, fullBodyChart);
        assertTrue(fitResult.sizeEligible());

        // Pecho bueno pero cintura excede (82cm en rango 68-76) -> false
        UserBodyMeasurements waistTooLarge = new UserBodyMeasurements(165.0, 90.0, 82.0, 92.0);
        GarmentSizeFitPolicy.SizeFitEvaluation badWaistResult =
                GarmentSizeFitPolicy.evaluate("S", GarmentCategory.ONE_PIECE, waistTooLarge, fullBodyChart);
        assertFalse(badWaistResult.sizeEligible());

        // Pecho excede (102cm en rango 86-94) -> false
        UserBodyMeasurements chestTooLarge = new UserBodyMeasurements(165.0, 102.0, 72.0, 92.0);
        GarmentSizeFitPolicy.SizeFitEvaluation badChestResult =
                GarmentSizeFitPolicy.evaluate("S", GarmentCategory.ONE_PIECE, chestTooLarge, fullBodyChart);
        assertFalse(badChestResult.sizeEligible());

        // Si falta pecho o cintura -> null (datos insuficientes)
        UserBodyMeasurements missingChest = new UserBodyMeasurements(165.0, null, 72.0, 92.0);
        assertNull(GarmentSizeFitPolicy.evaluate("S", GarmentCategory.ONE_PIECE, missingChest, fullBodyChart).sizeEligible());

        UserBodyMeasurements missingWaist = new UserBodyMeasurements(165.0, 90.0, null, 92.0);
        assertNull(GarmentSizeFitPolicy.evaluate("S", GarmentCategory.ONE_PIECE, missingWaist, fullBodyChart).sizeEligible());
    }

    @Test
    @DisplayName("Talla no configurada en la tabla de medidas del catálogo -> retorna sizeEligible=null")
    void shouldReturnNullWhenSizeNotFoundInChart() {
        UserBodyMeasurements measurements = new UserBodyMeasurements(175.0, 95.0, 80.0, 95.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("XXL", GarmentCategory.TOP, measurements, upperBodyChart);

        assertNotNull(result);
        assertNull(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_FIT_DATA_UNAVAILABLE, result.fitMessage());
    }

    @Test
    @DisplayName("Categoría nula o sin zona anatómica -> retorna sizeEligible=null")
    void shouldReturnNullWhenCategoryOrZoneIsNull() {
        UserBodyMeasurements measurements = new UserBodyMeasurements(175.0, 95.0, 80.0, 95.0);
        assertNull(GarmentSizeFitPolicy.evaluate("M", null, measurements, upperBodyChart).sizeEligible());
    }

    @Test
    @DisplayName("isValidSize: Verifica correctamente tallas válidas, inválidas, vacías y nulas")
    void shouldValidateKnownSizes() {
        assertTrue(GarmentSizeFitPolicy.isValidSize("S"));
        assertTrue(GarmentSizeFitPolicy.isValidSize("m"));
        assertTrue(GarmentSizeFitPolicy.isValidSize("XL"));
        assertTrue(GarmentSizeFitPolicy.isValidSize("32"));

        assertFalse(GarmentSizeFitPolicy.isValidSize("INVALID"));
        assertFalse(GarmentSizeFitPolicy.isValidSize(""));
        assertFalse(GarmentSizeFitPolicy.isValidSize("   "));
        assertFalse(GarmentSizeFitPolicy.isValidSize(null));
    }

    @Test
    @DisplayName("Cobertura exhaustiva de excepciones y Value Objects de SR-RF-24")
    void testExceptionsAndValueObjectsCoverage() {
        // GarmentNotEligibleException
        GarmentNotEligibleException ex1 = new GarmentNotEligibleException();
        assertEquals("GARMENT_NOT_ELIGIBLE", ex1.getErrorCode());
        assertEquals(GarmentNotEligibleException.DEFAULT_MESSAGE, ex1.getMessage());

        GarmentNotEligibleException ex2 = new GarmentNotEligibleException("Mensaje personalizado");
        assertEquals("Mensaje personalizado", ex2.getMessage());

        GarmentNotEligibleException ex3 = new GarmentNotEligibleException("   ");
        assertEquals(GarmentNotEligibleException.DEFAULT_MESSAGE, ex3.getMessage());

        GarmentNotEligibleException ex4 = new GarmentNotEligibleException(UUID.randomUUID());
        assertEquals(GarmentNotEligibleException.DEFAULT_MESSAGE, ex4.getMessage());

        // GarmentSizeUnavailableException
        GarmentSizeUnavailableException unavailableEx1 = new GarmentSizeUnavailableException("XL");
        assertEquals("TRYON_SIZE_UNAVAILABLE", unavailableEx1.getErrorCode());
        assertTrue(unavailableEx1.getMessage().contains("XL"));

        GarmentSizeUnavailableException unavailableEx2 = new GarmentSizeUnavailableException("XXL", "Talla no disponible");
        assertEquals("Talla no disponible", unavailableEx2.getMessage());

        GarmentSizeUnavailableException unavailableEx3 = new GarmentSizeUnavailableException("S", "   ");
        assertTrue(unavailableEx3.getMessage().contains("S"));

        // TryOnSelectionIncompleteException
        TryOnSelectionIncompleteException incompleteEx1 = new TryOnSelectionIncompleteException();
        assertEquals("TRYON_SELECTION_INCOMPLETE", incompleteEx1.getErrorCode());
        assertEquals(TryOnSelectionIncompleteException.DEFAULT_MESSAGE, incompleteEx1.getMessage());

        TryOnSelectionIncompleteException incompleteEx2 = new TryOnSelectionIncompleteException("Incompleto");
        assertEquals("Incompleto", incompleteEx2.getMessage());

        TryOnSelectionIncompleteException incompleteEx3 = new TryOnSelectionIncompleteException(null);
        assertEquals(TryOnSelectionIncompleteException.DEFAULT_MESSAGE, incompleteEx3.getMessage());

        // SizeMeasurementRange y GarmentSizeChart
        SizeMeasurementRange range = new SizeMeasurementRange(90.0, 96.0, 75.0, 80.0, 95.0, 100.0);
        assertTrue(range.hasChestRange());
        assertTrue(range.hasWaistRange());
        assertTrue(range.hasHipRange());

        assertTrue(range.chestFits(93.0));
        assertFalse(range.chestFits(88.0));
        assertFalse(range.chestFits(98.0));
        assertFalse(range.chestFits(null));

        assertTrue(range.waistFits(78.0));
        assertFalse(range.waistFits(70.0));
        assertFalse(range.waistFits(85.0));
        assertFalse(range.waistFits(null));

        assertTrue(range.hipFits(97.0));
        assertFalse(range.hipFits(90.0));
        assertFalse(range.hipFits(105.0));
        assertFalse(range.hipFits(null));

        SizeMeasurementRange rangeChestOnly = SizeMeasurementRange.ofChest(90.0, 95.0);
        assertNotNull(rangeChestOnly);
        assertNull(rangeChestOnly.minWaistCm());
        assertFalse(rangeChestOnly.hasWaistRange());
        assertFalse(rangeChestOnly.waistFits(70.0));

        SizeMeasurementRange rangeWaistOnly = SizeMeasurementRange.ofWaist(70.0, 75.0);
        assertNotNull(rangeWaistOnly);
        assertNull(rangeWaistOnly.minChestCm());
        assertFalse(rangeWaistOnly.hasChestRange());
        assertFalse(rangeWaistOnly.chestFits(90.0));

        SizeMeasurementRange rangeWaistHip = SizeMeasurementRange.ofWaistAndHip(70.0, 75.0, 90.0, 95.0);
        assertNotNull(rangeWaistHip);
        assertNull(rangeWaistHip.minChestCm());

        SizeMeasurementRange rangeFull = SizeMeasurementRange.ofFullBody(90.0, 95.0, 70.0, 75.0);
        assertNotNull(rangeFull);
        assertNull(rangeFull.minHipCm());
        assertFalse(rangeFull.hasHipRange());
        assertFalse(rangeFull.hipFits(90.0));

        GarmentSizeChart chart = new GarmentSizeChart(garmentId, Set.of("S", "M"), Map.of("S", range));
        assertTrue(chart.offersSize("S"));
        assertTrue(chart.offersSize("m"));
        assertFalse(chart.offersSize("L"));
        assertFalse(chart.offersSize(null));
        assertFalse(chart.offersSize("   "));
        assertEquals(range, chart.getMeasurementRange("S"));
        assertNull(chart.getMeasurementRange("XXL"));
        assertNull(chart.getMeasurementRange(null));
        assertNull(chart.getMeasurementRange("   "));

        GarmentSizeChart emptyChart = new GarmentSizeChart(garmentId, null, null);
        assertFalse(emptyChart.hasExplicitAvailableSizes());
        assertFalse(emptyChart.offersSize("S"));
        assertNull(emptyChart.getMeasurementRange("S"));

        GarmentSizeChart explicitEmptyChart = new GarmentSizeChart(garmentId, Set.of(), null);
        assertTrue(explicitEmptyChart.hasExplicitAvailableSizes());
        assertFalse(explicitEmptyChart.offersSize("S"));
    }
}
