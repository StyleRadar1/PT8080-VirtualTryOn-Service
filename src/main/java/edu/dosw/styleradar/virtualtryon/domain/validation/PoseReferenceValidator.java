package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;

import java.awt.image.BufferedImage;

/**
 * Validador V05 (SR-RF-23): Verifica que la fotografía frontal de silueta se encuentre
 * en orientación vertical (alto mayor o igual al ancho) para permitir la superposición de prendas.
 */
public class PoseReferenceValidator extends AbstractTryOnValidatorHandler {

    @Override
    protected void doValidate(TryOnValidationContext context) {
        BufferedImage image = context.getDecodedImage();
        if (image == null) {
            throw new InvalidPhotoException("La imagen debe ser decodificada antes de validar la pose frontal");
        }
        if (image.getHeight() < image.getWidth()) {
            throw new InvalidPhotoException("La fotografía frontal debe estar en orientación vertical");
        }
    }
}

