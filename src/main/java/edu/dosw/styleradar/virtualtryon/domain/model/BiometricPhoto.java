package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa la fotografía frontal de silueta del usuario para el probador virtual (SR-RF-23).
 * Almacena los metadatos técnicos validados, la ruta persistente del blob en Azure (sin tokens temporales)
 * y la URL firmada SAS generada en tiempo de lectura para acceso temporal de 60 minutos.
 */
public class BiometricPhoto {

    private final UUID photoId;
    private final UserId userId;
    private final String contentType;
    private final int width;
    private final int height;
    private final long sizeBytes;
    private final String blobPath;
    private final SasSignedUrl photoUrl;
    private final Instant uploadedAt;

    public BiometricPhoto(
            UUID photoId,
            UserId userId,
            String contentType,
            int width,
            int height,
            long sizeBytes,
            String blobPath,
            SasSignedUrl photoUrl,
            Instant uploadedAt
    ) {
        this.photoId = Objects.requireNonNull(photoId, "El identificador de la fotografía no puede ser nulo");
        this.userId = Objects.requireNonNull(userId, "El identificador del usuario no puede ser nulo");
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("El tipo de contenido (contentType) es obligatorio");
        }
        this.contentType = contentType;
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Las dimensiones de la fotografía deben ser positivas");
        }
        this.width = width;
        this.height = height;
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("El tamaño en bytes debe ser mayor a cero");
        }
        this.sizeBytes = sizeBytes;
        this.blobPath = Objects.requireNonNull(blobPath, "La ruta persistida del blob es obligatoria");
        this.photoUrl = Objects.requireNonNull(photoUrl, "La URL firmada de la fotografía es obligatoria");
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "La fecha de carga es obligatoria");
    }

    public BiometricPhoto(
            UUID photoId,
            UserId userId,
            String contentType,
            int width,
            int height,
            long sizeBytes,
            SasSignedUrl photoUrl,
            Instant uploadedAt
    ) {
        this(
                photoId,
                userId,
                contentType,
                width,
                height,
                sizeBytes,
                extractBlobPathFromUrl(photoUrl != null ? photoUrl.value() : ""),
                photoUrl,
                uploadedAt
        );
    }

    public BiometricPhoto withFreshSignedUrl(SasSignedUrl freshUrl) {
        return new BiometricPhoto(photoId, userId, contentType, width, height, sizeBytes, blobPath, freshUrl, uploadedAt);
    }

    private static String extractBlobPathFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        int queryIdx = url.indexOf('?');
        String cleanUrl = queryIdx >= 0 ? url.substring(0, queryIdx) : url;
        int lastSlash = cleanUrl.lastIndexOf('/');
        if (lastSlash >= 0) {
            int secondLast = cleanUrl.lastIndexOf('/', lastSlash - 1);
            if (secondLast >= 0) {
                return cleanUrl.substring(secondLast + 1);
            }
        }
        return cleanUrl;
    }

    public UUID getPhotoId() {
        return photoId;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getContentType() {
        return contentType;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getBlobPath() {
        return blobPath;
    }

    public SasSignedUrl getPhotoUrl() {
        return photoUrl;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
