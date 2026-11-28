package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotEligibleException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.model.BodyZone;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentValidationResult;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

import java.util.List;
import java.util.Objects;

/**
 * Política de dominio pura que evalúa la elegibilidad y las reglas de reemplazo por zona corporal
 * para una prenda candidata frente a prendas ya seleccionadas (RN-01, RN-02, FA-01, FA-02, FA-04).
 */
public final class GarmentSelectionPolicy {

    private GarmentSelectionPolicy() {
    }

    /**
     * Evalúa una prenda candidata contra las prendas ya seleccionadas sin lanzar excepción
     * cuando resulta incompatible o no disponible, retornando el detalle completo de validación.
     */
    public static GarmentValidationResult evaluate(
            CatalogGarment candidate,
            List<CatalogGarment> selectedGarments
    ) {
        return evaluate(candidate, selectedGarments, null, null, null, true);
    }

    /**
     * Evalúa una prenda candidata, conflictos de zona, ajuste orientativo de talla y saldo de cuota (SR-RF-24).
     */
    public static GarmentValidationResult evaluate(
            CatalogGarment candidate,
            List<CatalogGarment> selectedGarments,
            String selectedSize,
            Boolean sizeEligible,
            String fitMessage,
            boolean quotaAvailable
    ) {
        Objects.requireNonNull(candidate, "La prenda candidata no puede ser nula");

        GarmentCategory category = candidate.getCategory();
        boolean compatible = category != null && category.isCompatibleWithTryOn();
        BodyZone zone = compatible ? category.getBodyZone() : null;
        boolean visible = candidate.isVisible();
        boolean eligibleForTryOn = compatible && visible;

        List<GarmentId> replacedGarmentIds = eligibleForTryOn
                ? computeReplacedGarmentIds(candidate.getGarmentId(), zone, selectedGarments)
                : List.of();

        String validationMessage = buildValidationMessage(
                category,
                compatible,
                visible,
                zone,
                replacedGarmentIds
        );

        return new GarmentValidationResult(
                candidate,
                compatible,
                visible,
                zone,
                eligibleForTryOn,
                validationMessage,
                replacedGarmentIds,
                selectedSize,
                sizeEligible,
                fitMessage,
                quotaAvailable
        );
    }

    /**
     * Valida de forma estricta una prenda candidata para ejecución de prueba virtual,
     * lanzando {@link InvalidGarmentException} cuando infringe RN-01 o FA-01/FA-02.
     */
    public static void assertEligible(CatalogGarment candidate) {
        GarmentValidationResult result = evaluate(candidate, List.of());
        if (!result.eligibleForTryOn()) {
            throw new GarmentNotEligibleException("La prenda no está disponible o no admite prueba virtual.");
        }
    }

    private static List<GarmentId> computeReplacedGarmentIds(
            GarmentId candidateId,
            BodyZone candidateZone,
            List<CatalogGarment> selectedGarments
    ) {
        if (candidateZone == null || selectedGarments == null || selectedGarments.isEmpty()) {
            return List.of();
        }
        return selectedGarments.stream()
                .filter(Objects::nonNull)
                .filter(existing -> !existing.getGarmentId().equals(candidateId))
                .filter(existing -> {
                    GarmentCategory existingCat = existing.getCategory();
                    BodyZone existingZone = (existingCat != null && existingCat.isCompatibleWithTryOn())
                            ? existingCat.getBodyZone()
                            : null;
                    return candidateZone.conflictsWith(existingZone);
                })
                .map(CatalogGarment::getGarmentId)
                .toList();
    }

    private static String buildValidationMessage(
            GarmentCategory category,
            boolean compatible,
            boolean visible,
            BodyZone zone,
            List<GarmentId> replacedGarmentIds
    ) {
        if (!compatible) {
            String catName = category != null ? category.name() : "DESCONOCIDA";
            return "La prenda no es compatible con el probador virtual: la categoría "
                    + catName + " no admite superposición (solo TOP, BOTTOM, ONE_PIECE y OUTERWEAR).";
        }
        if (!visible) {
            return "La prenda no está disponible en el catálogo en este momento.";
        }
        if (!replacedGarmentIds.isEmpty()) {
            if (zone == BodyZone.FULL_BODY) {
                return "Prenda de cuerpo completo compatible; al seleccionarla reemplazará "
                        + replacedGarmentIds.size()
                        + " prenda(s) previamente seleccionada(s) (RN-02).";
            }
            return "Prenda compatible en zona " + zone.name()
                    + "; reemplazará " + replacedGarmentIds.size()
                    + " prenda(s) previa(s) en conflicto (FA-04).";
        }
        return "Prenda compatible y disponible para prueba virtual en zona " + zone.name() + ".";
    }
}

