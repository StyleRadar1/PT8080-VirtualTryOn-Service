package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Contexto compartido que transporta los datos de entrada a lo largo de la cadena de validación
 * (Patrón Chain of Responsibility: V01..V05) para la carga de silueta, selección de prenda,
 * verificación de cuota mensual y solicitud de prueba virtual (SR-RF-23 y SR-RF-24).
 */
public class TryOnValidationContext {

    private final UserId userId;
    private final boolean biometricConsentAccepted;
    private final String contentType;
    private final byte[] imageBytes;
    private GarmentId garmentId;
    private GarmentCategory garmentCategory;
    private String garmentImageUrl;
    private boolean garmentVisible = true;
    private UserQuota userQuota;
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

    public TryOnValidationContext(
            UserId userId,
            GarmentId garmentId,
            GarmentCategory garmentCategory,
            String garmentImageUrl,
            boolean garmentVisible,
            boolean biometricConsentAccepted,
            UserQuota userQuota
    ) {
        this.userId = Objects.requireNonNull(userId, "El identificador del usuario no puede ser nulo");
        this.garmentId = garmentId;
        this.garmentCategory = garmentCategory;
        this.garmentImageUrl = garmentImageUrl;
        this.garmentVisible = garmentVisible;
        this.biometricConsentAccepted = biometricConsentAccepted;
        this.userQuota = userQuota;
        this.contentType = null;
        this.imageBytes = null;
    }

    public UserId getUserId() {
        return userId;
    }

    public boolean isBiometricConsentAccepted() {
        return biometricConsentAccepted;
    }

    public boolean hasBiometricConsent() {
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

    public double getFileSizeMb() {
        return getImageSizeBytes() / (1024.0 * 1024.0);
    }

    public GarmentId getGarmentId() {
        return garmentId;
    }

    public void setGarmentId(GarmentId garmentId) {
        this.garmentId = garmentId;
    }

    public GarmentCategory getGarmentCategory() {
        return garmentCategory;
    }

    public void setGarmentCategory(GarmentCategory garmentCategory) {
        this.garmentCategory = garmentCategory;
    }

    public String getGarmentImageUrl() {
        return garmentImageUrl;
    }

    public void setGarmentImageUrl(String garmentImageUrl) {
        this.garmentImageUrl = garmentImageUrl;
    }

    public boolean isGarmentVisible() {
        return garmentVisible;
    }

    public void setGarmentVisible(boolean garmentVisible) {
        this.garmentVisible = garmentVisible;
    }

    public UserQuota getUserQuota() {
        return userQuota;
    }

    public void setUserQuota(UserQuota userQuota) {
        this.userQuota = userQuota;
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
