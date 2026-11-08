package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Contexto compartido que transporta los datos de entrada a lo largo de la cadena de validación
 * (Patrón Chain of Responsibility) para la carga de silueta y solicitud de prueba virtual.
 */
public class TryOnValidationContext {

    private final UserId userId;
    private final boolean biometricConsentAccepted;
    private final String contentType;
    private final byte[] imageBytes;
    private BufferedImage decodedImage;
    private int imageWidth;
    private int imageHeight;

    public TryOnValidationContext(
            UserId userId,
            boolean biometricConsentAccepted,
            String contentType,
            byte[] imageBytes
    ) {
        this.userId = Objects.requireNonNull(userId, "El identificador del usuario no puede ser nulo");
        this.biometricConsentAccepted = biometricConsentAccepted;
        this.contentType = contentType;
        this.imageBytes = imageBytes == null ? null : imageBytes.clone();
    }

    public UserId getUserId() {
        return userId;
    }

    public boolean isBiometricConsentAccepted() {
        return biometricConsentAccepted;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getImageBytes() {
        return imageBytes == null ? null : imageBytes.clone();
    }

    public int getImageSizeBytes() {
        return imageBytes == null ? 0 : imageBytes.length;
    }

    public BufferedImage getDecodedImage() {
        return decodedImage;
    }

    public void setDecodedImage(BufferedImage decodedImage) {
        this.decodedImage = decodedImage;
        if (decodedImage != null) {
            this.imageWidth = decodedImage.getWidth();
            this.imageHeight = decodedImage.getHeight();
        }
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }
}

