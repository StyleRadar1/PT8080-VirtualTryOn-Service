package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;

import java.awt.image.BufferedImage;

/**
 * Validador V05 (SR-RF-23): Verifica que la fotografía del usuario cumpla las condiciones
 * geométricas y espaciales de postura frontal y cuerpo completo para la superposición virtual:
 * <ul>
 *   <li><b>1. Orientación vertical estricta:</b> El alto de la imagen debe ser mayor que el ancho.</li>
 *   <li><b>2. Proporción de cuerpo completo (Aspect Ratio):</b> La relación {@code alto / ancho} debe
 *       encontrarse en el rango {@code [1.20, 2.60]} para descartar retratos cuadrados de rostro/busto
 *       o recortes deformados.</li>
 *   <li><b>3. Presencia de silueta en las tres zonas anatómicas y postura frontal centrada:</b>
 *       Analiza el contraste figura-fondo en la zona superior (cabeza/hombros: 10%–30%),
 *       zona media (torso: 30%–65%) y zona inferior (cadera/piernas: 65%–90%), así como la
 *       simetría bilateral del centro de masa horizontal respecto al eje central del encuadre.</li>
 * </ul>
 */
public class PoseReferenceValidator extends AbstractTryOnValidatorHandler {

    public static final double MIN_FULL_BODY_ASPECT_RATIO = 1.20;
    public static final double MAX_FULL_BODY_ASPECT_RATIO = 2.60;
    public static final double MIN_ZONE_SILHOUETTE_CONTRAST = 8.0;
    public static final double MAX_HORIZONTAL_CENTER_DEVIATION = 0.20;

    @Override
    protected void doValidate(TryOnValidationContext context) {
        BufferedImage image = context.getDecodedImage();
        if (image == null) {
            throw new InvalidPhotoException("La imagen debe ser decodificada antes de validar la pose frontal");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (height <= width) {
            throw new InvalidPhotoException("La fotografía frontal debe estar en orientación vertical");
        }

        double aspectRatio = (double) height / width;
        if (aspectRatio < MIN_FULL_BODY_ASPECT_RATIO || aspectRatio > MAX_FULL_BODY_ASPECT_RATIO) {
            throw new InvalidPhotoException(
                    "La fotografía debe presentar una proporción vertical de cuerpo completo (relación alto/ancho entre 1.20 y 2.60)"
            );
        }

        validateFrontalFullBodySilhouette(image, width, height);
    }

    private void validateFrontalFullBodySilhouette(BufferedImage image, int width, int height) {
        double backgroundLuminance = estimateLateralBackgroundLuminance(image, width, height);

        double headZoneContrast = computeCentralZoneContrast(image, width,
                (int) (height * 0.10), (int) (height * 0.30), backgroundLuminance);
        double torsoZoneContrast = computeCentralZoneContrast(image, width,
                (int) (height * 0.30), (int) (height * 0.65), backgroundLuminance);
        double legsZoneContrast = computeCentralZoneContrast(image, width,
                (int) (height * 0.65), (int) (height * 0.90), backgroundLuminance);

        if (headZoneContrast < MIN_ZONE_SILHOUETTE_CONTRAST
                || torsoZoneContrast < MIN_ZONE_SILHOUETTE_CONTRAST
                || legsZoneContrast < MIN_ZONE_SILHOUETTE_CONTRAST) {
            throw new InvalidPhotoException(
                    "La fotografía debe contener una silueta frontal de cuerpo completo distinguible del fondo en las zonas superior, media e inferior"
            );
        }

        double normalizedCenterX = computeSilhouetteHorizontalCenter(image, width, height, backgroundLuminance);
        if (Math.abs(normalizedCenterX - 0.50) > MAX_HORIZONTAL_CENTER_DEVIATION) {
            throw new InvalidPhotoException(
                    "La silueta debe encontrarse en postura frontal y centrada dentro del encuadre"
            );
        }
    }

    private double estimateLateralBackgroundLuminance(BufferedImage image, int width, int height) {
        int leftMarginEnd = Math.max(1, (int) (width * 0.10));
        int rightMarginStart = Math.min(width - 1, (int) (width * 0.90));
        int stepY = Math.max(1, height / 40);
        int stepX = Math.max(1, leftMarginEnd / 4);

        double sum = 0.0;
        int count = 0;

        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < leftMarginEnd; x += stepX) {
                sum += pixelLuminance(image.getRGB(x, y));
                count++;
            }
            for (int x = rightMarginStart; x < width; x += stepX) {
                sum += pixelLuminance(image.getRGB(x, y));
                count++;
            }
        }
        return sum / Math.max(1, count);
    }

    private double computeCentralZoneContrast(
            BufferedImage image,
            int width,
            int startY,
            int endY,
            double backgroundLuminance
    ) {
        int startX = (int) (width * 0.25);
        int endX = (int) (width * 0.75);
        int stepX = Math.max(1, (endX - startX) / 20);
        int stepY = Math.max(1, (endY - startY) / 15);

        double contrastSum = 0.0;
        int samples = 0;

        for (int y = startY; y < endY; y += stepY) {
            for (int x = startX; x < endX; x += stepX) {
                double lum = pixelLuminance(image.getRGB(x, y));
                contrastSum += Math.abs(lum - backgroundLuminance);
                samples++;
            }
        }
        return contrastSum / Math.max(1, samples);
    }

    private double computeSilhouetteHorizontalCenter(
            BufferedImage image,
            int width,
            int height,
            double backgroundLuminance
    ) {
        int startY = (int) (height * 0.10);
        int endY = (int) (height * 0.90);
        int stepX = Math.max(1, width / 40);
        int stepY = Math.max(1, (endY - startY) / 40);

        double weightedXSum = 0.0;
        double weightSum = 0.0;

        for (int y = startY; y < endY; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                double diff = Math.abs(pixelLuminance(image.getRGB(x, y)) - backgroundLuminance);
                if (diff >= MIN_ZONE_SILHOUETTE_CONTRAST) {
                    weightedXSum += ((double) x / width) * diff;
                    weightSum += diff;
                }
            }
        }
        return weightedXSum / weightSum;
    }

    private static double pixelLuminance(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }
}
