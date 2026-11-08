package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

/**
 * Validador V03 de la cadena de responsabilidad: verifica que la prenda seleccionada exista,
 * sea visible en el catálogo, tenga al menos una fotografía de referencia y pertenezca a una
 * categoría superponible en el Probador Virtual (TOPS, BOTTOMS, ONE_PIECES, OUTERWEAR) (SR-RF-24).
 */
public class GarmentCategoryValidator extends AbstractTryOnValidatorHandler {

    @Override
    protected void doValidate(TryOnValidationContext context) {
        GarmentId garmentId = context.getGarmentId();
        if (garmentId == null) {
            throw new InvalidGarmentException("El identificador de la prenda es obligatorio para la prueba virtual");
        }

        if (!context.isGarmentVisible()) {
            throw new InvalidGarmentException(
                    garmentId.value(),
                    "La prenda no se encuentra visible o aprobada por moderación en el catálogo"
            );
        }

        GarmentCategory category = context.getGarmentCategory();
        if (category == null) {
            throw new InvalidGarmentException(
                    garmentId.value(),
                    "La categoría de la prenda es obligatoria para determinar la región de superposición"
            );
        }

        if (!category.isCompatibleWithTryOn()) {
            throw new InvalidGarmentException(
                    garmentId.value(),
                    "El probador virtual no admite prendas de tipo " + category.name()
            );
        }

        String imageUrl = context.getGarmentImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new InvalidGarmentException(
                    garmentId.value(),
                    "La prenda no tiene fotografías disponibles para el probador virtual"
            );
        }
    }
}
