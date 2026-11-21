package edu.dosw.styleradar.virtualtryon.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - UserBodyMeasurements (SR-RF-24)")
class UserBodyMeasurementsTest {

    @Test
    @DisplayName("empty() inicializa todas las medidas en null")
    void shouldCreateEmptyMeasurements() {
        UserBodyMeasurements empty = UserBodyMeasurements.empty();
        assertNotNull(empty);
        assertNull(empty.heightCm());
        assertNull(empty.chestCm());
        assertNull(empty.waistCm());
        assertNull(empty.hipCm());
        assertFalse(empty.hasSufficientMeasurements());
    }

    @Test
    @DisplayName("hasSufficientMeasurements evalúa todas las ramas lógicas")
    void shouldEvaluateHasSufficientMeasurementsBranches() {
        // 1. pecho > 0
        UserBodyMeasurements mChest = new UserBodyMeasurements(175.0, 95.0, null, null);
        assertTrue(mChest.hasSufficientMeasurements());

        // 2. pecho null o <= 0, pero cintura > 0
        UserBodyMeasurements mWaist = new UserBodyMeasurements(null, 0.0, 80.0, null);
        assertTrue(mWaist.hasSufficientMeasurements());

        UserBodyMeasurements mWaistOnly = new UserBodyMeasurements(null, null, 82.0, null);
        assertTrue(mWaistOnly.hasSufficientMeasurements());

        // 3. pecho y cintura null o <= 0, pero cadera > 0
        UserBodyMeasurements mHip = new UserBodyMeasurements(null, -1.0, 0.0, 95.0);
        assertTrue(mHip.hasSufficientMeasurements());

        UserBodyMeasurements mHipOnly = new UserBodyMeasurements(null, null, null, 98.0);
        assertTrue(mHipOnly.hasSufficientMeasurements());

        // 4. Todas null
        UserBodyMeasurements allNull = new UserBodyMeasurements(null, null, null, null);
        assertFalse(allNull.hasSufficientMeasurements());

        // 5. Todas <= 0
        UserBodyMeasurements allZeroOrNegative = new UserBodyMeasurements(0.0, 0.0, -5.0, -10.0);
        assertFalse(allZeroOrNegative.hasSufficientMeasurements());
    }

    @Test
    @DisplayName("Acceso a atributos, equals, hashCode y toString del record")
    void shouldSupportRecordStandardMethods() {
        UserBodyMeasurements m1 = new UserBodyMeasurements(170.0, 90.0, 75.0, 92.0);
        UserBodyMeasurements m2 = new UserBodyMeasurements(170.0, 90.0, 75.0, 92.0);
        UserBodyMeasurements m3 = new UserBodyMeasurements(180.0, 100.0, 85.0, 102.0);

        assertEquals(170.0, m1.heightCm());
        assertEquals(90.0, m1.chestCm());
        assertEquals(75.0, m1.waistCm());
        assertEquals(92.0, m1.hipCm());

        assertEquals(m1, m2);
        assertNotEquals(m1, m3);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertNotNull(m1.toString());
    }
}
