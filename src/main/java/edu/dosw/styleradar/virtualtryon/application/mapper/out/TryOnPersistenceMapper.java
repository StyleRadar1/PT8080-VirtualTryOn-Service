package edu.dosw.styleradar.virtualtryon.application.mapper.out;

import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Mapper de salida que convierte entidades del dominio hacia entidades JPA de persistencia y viceversa (SR-RF-23).
 * Persiste únicamente la ruta del blob en la base de datos relacional para evitar la persistencia de tokens SAS temporales.
 */
@Component
public class TryOnPersistenceMapper {

    public BiometricConsentJpaEntity toConsentJpaEntity(BiometricConsent domain) {
        String purposesCsv = String.join(",", domain.getPurposes());
        return new BiometricConsentJpaEntity(
                domain.getConsentId(),
                domain.getUserId().value(),
                domain.getTermsVersion(),
                domain.isAccepted(),
                purposesCsv,
                domain.getConsentedAt(),
                domain.getRevokedAt(),
                domain.getIpAddress()
        );
    }

    public BiometricConsent toConsentDomain(BiometricConsentJpaEntity entity) {
        List<String> purposes = (entity.getPurposes() == null || entity.getPurposes().isBlank())
                ? List.of()
                : Arrays.asList(entity.getPurposes().split(","));
        return new BiometricConsent(
                entity.getId(),
                UserId.of(entity.getUserId()),
                entity.getTermsVersion(),
                entity.isAccepted(),
                purposes,
                entity.getConsentedAt(),
                entity.getRevokedAt(),
                entity.getIpAddress()
        );
    }

    public BiometricPhotoJpaEntity toPhotoJpaEntity(BiometricPhoto domain) {
        return new BiometricPhotoJpaEntity(
                domain.getPhotoId(),
                domain.getUserId().value(),
                domain.getContentType(),
                domain.getWidth(),
                domain.getHeight(),
                domain.getSizeBytes(),
                domain.getBlobPath(),
                domain.getUploadedAt()
        );
    }

    public BiometricPhoto toPhotoDomain(BiometricPhotoJpaEntity entity) {
        String persistedBlobPath = entity.getPhotoUrl();
        String cleanBlobPath = normalizePersistedBlobPath(persistedBlobPath);
        String safeUrl = "https://blob.storage.local/biometric-silhouettes/" + cleanBlobPath;
        return new BiometricPhoto(
                entity.getId(),
                UserId.of(entity.getUserId()),
                entity.getContentType(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getSizeBytes(),
                cleanBlobPath,
                SasSignedUrl.of(safeUrl),
                entity.getUploadedAt()
        );
    }

    /**
     * Normaliza referencias antiguas almacenadas en la base de datos eliminando parámetros
     * query SAS (?se=...&sig=...) y extrayendo únicamente la ruta canónica relativa del blob
     * (ej: userId/silhouette-UUID.png), preservando todos los segmentos del usuario.
     */
    public static String normalizePersistedBlobPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }
        String path = rawPath.trim();
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }
        boolean wasHttp = path.startsWith("http://") || path.startsWith("https://");
        if (wasHttp) {
            try {
                URI uri = URI.create(path);
                String uriPath = uri.getPath();
                if (uriPath != null && !uriPath.isBlank()) {
                    path = uriPath;
                }
            } catch (Exception ignored) {
            }
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.startsWith("biometric-silhouettes/")) {
            return path.substring("biometric-silhouettes/".length());
        }
        if (wasHttp) {
            int firstSlash = path.indexOf('/');
            if (firstSlash >= 0 && firstSlash < path.length() - 1) {
                return path.substring(firstSlash + 1);
            }
        }
        return path;
    }
}
