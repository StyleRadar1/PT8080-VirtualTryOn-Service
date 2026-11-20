package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.UserBodyMeasurements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - GarmentSizeFitPolicy (SR-RF-24)")
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
    @DisplayName("Cuando las medidas corporales coinciden con la talla M (pecho 98 cm), retorna sizeEligible=true")
    void shouldReturnTrueWhenMeasurementsFitMediumSize() {
        UserBodyMeasurements measurements = new UserBodyMeasurements(175.0, 98.0, 82.0, 96.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("M", GarmentCategory.TOP, measurements);

        assertNotNull(result);
        assertEquals("M", result.normalizedSize());
        assertTrue(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_FIT_SUCCESS, result.fitMessage());
    }

    @Test
    @DisplayName("Cuando las medidas corporales coinciden con talla S (case-insensitive 's', pecho 90 cm), retorna sizeEligible=true")
    void shouldReturnTrueWhenMeasurementsFitSmallCaseInsensitive() {
        UserBodyMeasurements measurements = new UserBodyMeasurements(168.0, 90.0, 76.0, 90.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("s", GarmentCategory.TOP, measurements);

        assertNotNull(result);
        assertEquals("S", result.normalizedSize());
        assertTrue(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_FIT_SUCCESS, result.fitMessage());
    }

    @Test
    @DisplayName("Cuando el usuario tiene pecho de 112 cm intentando seleccionar S, retorna sizeEligible=false y advertencia")
    void shouldReturnFalseWhenGarmentIsTooSmall() {
        UserBodyMeasurements measurements = new UserBodyMeasurements(182.0, 112.0, 94.0, 106.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("S", GarmentCategory.TOP, measurements);

        assertNotNull(result);
        assertEquals("S", result.normalizedSize());
        assertFalse(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_FIT_WARNING, result.fitMessage());
    }

    @Test
    @DisplayName("Cuando se evalúa prenda LOWER_BODY (pantalón) talla 32 con cintura 84 cm, retorna sizeEligible=true")
    void shouldReturnTrueForLowerBodyFit() {
        UserBodyMeasurements measurements = new UserBodyMeasurements(178.0, 96.0, 84.0, 98.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("32", GarmentCategory.BOTTOM, measurements);

        assertNotNull(result);
        assertEquals("32", result.normalizedSize());
        assertTrue(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_FIT_SUCCESS, result.fitMessage());
    }

    @Test
    @DisplayName("Cuando se evalúa prenda ONE_PIECE (FULL_BODY), siempre retorna sizeEligible=true")
    void shouldReturnTrueForFullBodyFit() {
        UserBodyMeasurements measurements = new UserBodyMeasurements(170.0, 92.0, 78.0, 94.0);
        GarmentSizeFitPolicy.SizeFitEvaluation result =
                GarmentSizeFitPolicy.evaluate("M", GarmentCategory.ONE_PIECE, measurements);

        assertNotNull(result);
        assertTrue(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_FIT_SUCCESS, result.fitMessage());
    }

    @Test
    @DisplayName("Validación estática de tallas conocidas")
    void shouldValidateKnownSizesCorrectly() {
        assertTrue(GarmentSizeFitPolicy.isValidSize("S"));
        assertTrue(GarmentSizeFitPolicy.isValidSize("m"));
        assertTrue(GarmentSizeFitPolicy.isValidSize("32"));
        assertFalse(GarmentSizeFitPolicy.isValidSize("UNKNOWN_SIZE"));
        assertFalse(GarmentSizeFitPolicy.isValidSize(null));
        assertFalse(GarmentSizeFitPolicy.isValidSize(" "));
    }
}
