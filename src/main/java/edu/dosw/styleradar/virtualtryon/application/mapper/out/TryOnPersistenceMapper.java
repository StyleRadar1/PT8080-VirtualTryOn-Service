package edu.dosw.styleradar.virtualtryon.application.mapper.out;

import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.JobAuditLogJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnJobJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Mapper de salida que convierte entidades del dominio hacia entidades JPA de persistencia y viceversa (SR-RF-23 a SR-RF-25).
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

    public UserQuotaJpaEntity toQuotaJpaEntity(UserQuota domain) {
        return new UserQuotaJpaEntity(
                domain.getId(),
                domain.getUserId().value(),
                domain.getMonthlyLimit(),
                domain.getUsedCount(),
                domain.getBillingPeriod().getValue(),
                domain.isRadarPlus(),
                domain.getLastResetAt()
        );
    }

    public UserQuota toQuotaDomain(UserQuotaJpaEntity entity) {
        return new UserQuota(
                entity.getId(),
                UserId.of(entity.getUserId()),
                entity.getMonthlyLimit(),
                entity.getUsedCount(),
                BillingPeriod.of(entity.getBillingPeriod()),
                entity.isRadarPlus(),
                entity.getLastResetAt()
        );
    }

    public CatalogGarmentJpaEntity toCatalogGarmentJpaEntity(CatalogGarment domain) {
        return new CatalogGarmentJpaEntity(
                domain.getGarmentId().value(),
                domain.getName(),
                domain.getCategory().name(),
                domain.getImageUrl(),
                domain.isVisible(),
                domain.getUpdatedAt()
        );
    }

    public CatalogGarment toCatalogGarmentDomain(CatalogGarmentJpaEntity entity) {
        return new CatalogGarment(
                GarmentId.of(entity.getGarmentId()),
                entity.getName(),
                GarmentCategory.fromRawValue(entity.getCategory()),
                entity.getImageUrl(),
                entity.isVisible(),
                entity.getUpdatedAt()
        );
    }

    public TryOnJobJpaEntity toJobJpaEntity(TryOnJob domain) {
        String cleanPhotoUrl = domain.getPhotoUrl() != null ? domain.getPhotoUrl().split("\\?", 2)[0] : null;
        String cleanResultUrl = domain.getResultImageUrl() != null && domain.getResultImageUrl().value() != null
                ? domain.getResultImageUrl().value().split("\\?", 2)[0]
                : null;
        return new TryOnJobJpaEntity(
                domain.getId(),
                domain.getUserId().value(),
                domain.getGarmentId().value(),
                cleanPhotoUrl,
                domain.getGarmentCategory().name(),
                domain.getGarmentImageUrl(),
                domain.getStatus().name(),
                cleanResultUrl,
                domain.getAiProvider().name(),
                domain.getExecutionTimeMs(),
                domain.getErrorMessage(),
                domain.isPhotoPurged(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }

    public TryOnJob toJobDomain(TryOnJobJpaEntity entity) {
        SasSignedUrl resultUrl = (entity.getResultImageUrl() == null || entity.getResultImageUrl().isBlank())
                ? null
                : SasSignedUrl.of(entity.getResultImageUrl());
        GarmentCategory category = entity.getGarmentCategory() == null
                ? GarmentCategory.TOPS
                : GarmentCategory.fromRawValue(entity.getGarmentCategory());
        AiProvider provider = entity.getAiProvider() == null
                ? AiProvider.FASHN_AI
                : AiProvider.fromConfigValue(entity.getAiProvider());
        return new TryOnJob(
                entity.getId(),
                UserId.of(entity.getUserId()),
                GarmentId.of(entity.getGarmentId()),
                entity.getPhotoUrl(),
                category,
                entity.getGarmentImageUrl(),
                JobStatus.valueOf(entity.getStatus()),
                resultUrl,
                provider,
                entity.getExecutionTimeMs(),
                entity.getErrorMessage(),
                entity.isPhotoPurged(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public JobAuditLogJpaEntity toAuditLogJpaEntity(JobAuditLog domain) {
        return new JobAuditLogJpaEntity(
                domain.getId(),
                domain.getJobId(),
                domain.getPreviousStatus() != null ? domain.getPreviousStatus().name() : null,
                domain.getNewStatus().name(),
                domain.getTransitionReason(),
                domain.getOccurredAt()
        );
    }

    public JobAuditLog toAuditLogDomain(JobAuditLogJpaEntity entity) {
        JobStatus previous = (entity.getPreviousStatus() == null || entity.getPreviousStatus().isBlank())
                ? null
                : JobStatus.valueOf(entity.getPreviousStatus());
        return new JobAuditLog(
                entity.getId(),
                entity.getJobId(),
                previous,
                JobStatus.valueOf(entity.getNewStatus()),
                entity.getTransitionReason(),
                entity.getOccurredAt()
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
