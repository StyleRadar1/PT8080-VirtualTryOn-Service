package edu.dosw.styleradar.virtualtryon.application.mapper.out;

import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import org.springframework.stereotype.Component;

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
        String safeBlobPath = persistedBlobPath == null ? "" : persistedBlobPath;
        String safeUrl = (persistedBlobPath != null && (persistedBlobPath.startsWith("http://") || persistedBlobPath.startsWith("https://")))
                ? persistedBlobPath
                : "https://blob.storage.local/biometric-silhouettes/" + safeBlobPath;
        return new BiometricPhoto(
                entity.getId(),
                UserId.of(entity.getUserId()),
                entity.getContentType(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getSizeBytes(),
                safeBlobPath,
                SasSignedUrl.of(safeUrl),
                entity.getUploadedAt()
        );
    }
}
