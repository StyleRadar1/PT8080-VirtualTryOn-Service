package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Validador V04 (SR-RF-23): Verifica el formato MIME admitido (JPEG o PNG), el peso máximo (5 MB),
 * la decodificación íntegra de la imagen y la resolución mínima de 300x400 píxeles.
 */
public class ImageFormatResolutionValidator extends AbstractTryOnValidatorHandler {

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    public static final int MIN_WIDTH = 300;
    public static final int MIN_HEIGHT = 400;

    @Override
    protected void doValidate(TryOnValidationContext context) {
        byte[] bytes = context.getImageBytes();
        if (bytes == null || bytes.length == 0) {
            throw new InvalidPhotoException("La fotografía no puede estar vacía");
        }
        String contentType = context.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidPhotoException("La fotografía debe ser JPEG o PNG");
        }
        if (bytes.length > MAX_SIZE_BYTES) {
            throw new InvalidPhotoException("La fotografía no puede superar 5 MB");
        }
        BufferedImage decoded = decodeImage(bytes);
        if (decoded == null) {
            throw new InvalidPhotoException("El archivo no es una imagen válida");
        }
        if (decoded.getWidth() < MIN_WIDTH || decoded.getHeight() < MIN_HEIGHT) {
            throw new InvalidPhotoException(
                    "La fotografía debe medir al menos " + MIN_WIDTH + "x" + MIN_HEIGHT + " píxeles"
            );
        }
        context.setDecodedImage(decoded);
    }

    private BufferedImage decodeImage(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            return null;
        }
    }
}

