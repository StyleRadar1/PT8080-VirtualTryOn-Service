package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

import java.util.List;
import java.util.Objects;

/**
 * Resultado inmutable de evaluar la elegibilidad, visibilidad, categoría compatible,
 * zona corporal, conflictos de reemplazo, ajuste orientativo de talla y saldo de cuota
 * para el Probador Virtual (RN-01 a RN-04, FA-01 a FA-05 · SR-RF-24).
 */
public record GarmentValidationResult(
        CatalogGarment garment,
        boolean compatible,
        boolean visible,
        BodyZone zone,
        boolean eligibleForTryOn,
        String validationMessage,
        List<GarmentId> replacedGarmentIds,
        String selectedSize,
        Boolean sizeEligible,
        String fitMessage,
        boolean quotaAvailable
) {

    public GarmentValidationResult {
        Objects.requireNonNull(garment, "La prenda evaluada no puede ser nula");
        replacedGarmentIds = replacedGarmentIds == null ? List.of() : List.copyOf(replacedGarmentIds);
    }

    public GarmentValidationResult(
            CatalogGarment garment,
            boolean compatible,
            boolean visible,
            BodyZone zone,
            boolean eligibleForTryOn,
            String validationMessage,
            List<GarmentId> replacedGarmentIds
    ) {
        this(garment, compatible, visible, zone, eligibleForTryOn, validationMessage, replacedGarmentIds, null, null, null, true);
    }
}
