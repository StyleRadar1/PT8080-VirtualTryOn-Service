package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

import java.util.List;
import java.util.Objects;

/**
 * Resultado inmutable de evaluar la elegibilidad, visibilidad, categoría compatible,
 * zona corporal y conflictos de reemplazo de una prenda frente a las prendas actualmente
 * seleccionadas para la prueba virtual (RN-01, RN-02, FA-01, FA-02, FA-04 · SR-RF-24).
 */
public record GarmentValidationResult(
        CatalogGarment garment,
        boolean compatible,
        boolean visible,
        BodyZone zone,
        boolean eligibleForTryOn,
        String validationMessage,
        List<GarmentId> replacedGarmentIds
) {

    public GarmentValidationResult {
        Objects.requireNonNull(garment, "La prenda evaluada no puede ser nula");
        replacedGarmentIds = replacedGarmentIds == null ? List.of() : List.copyOf(replacedGarmentIds);
    }
}

