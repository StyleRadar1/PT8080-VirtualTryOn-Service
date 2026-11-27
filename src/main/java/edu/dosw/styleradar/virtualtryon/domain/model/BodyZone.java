package edu.dosw.styleradar.virtualtryon.domain.model;

/**
 * Zonas corporales soportadas en la composición de pruebas virtuales (RN-02 / FA-04, SR-RF-24).
 */
public enum BodyZone {

    UPPER_BODY,
    LOWER_BODY,
    FULL_BODY;

    /**
     * Determina si seleccionar una prenda en esta zona entra en conflicto con otra zona
     * previamente seleccionada y debe reemplazarla. Una prenda de cuerpo completo ({@code FULL_BODY})
     * reemplaza tanto prendas superiores ({@code UPPER_BODY}) como inferiores ({@code LOWER_BODY})
     * y viceversa; dos prendas en la misma zona también se reemplazan mutuamente.
     */
    public boolean conflictsWith(BodyZone other) {
        if (other == null) {
            return false;
        }
        if (this == FULL_BODY || other == FULL_BODY) {
            return true;
        }
        return this == other;
    }
}

