package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotEligibleException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

/**
 * Validador V03 de la cadena de responsabilidad: verifica que la prenda seleccionada exista,
 * sea visible en el catálogo, tenga al menos una fotografía de referencia y pertenezca a una
 * categoría superponible en el Probador Virtual (TOP, BOTTOM, ONE_PIECE, OUTERWEAR) (SR-RF-24 / RN-01 · FA-04).
 */
public class GarmentCategoryValidator extends AbstractTryOnValidatorHandler {

    @Override
    protected void doValidate(TryOnValidationContext context) {
        GarmentId garmentId = context.getGarmentId();
        if (garmentId == null) {
            throw new InvalidGarmentException("El identificador de la prenda es obligatorio para la prueba virtual");
        }

        if (!context.isGarmentVisible()) {
            throw new GarmentNotEligibleException(
                    "La prenda no está disponible o no admite prueba virtual."
            );
        }

        GarmentCategory category = context.getGarmentCategory();
        if (category == null || !category.isCompatibleWithTryOn()) {
            throw new GarmentNotEligibleException(
                    "La prenda no está disponible o no admite prueba virtual."
            );
        }

        String imageUrl = context.getGarmentImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new GarmentNotEligibleException(
                    "La prenda no está disponible o no admite prueba virtual."
            );
        }
    }
}
