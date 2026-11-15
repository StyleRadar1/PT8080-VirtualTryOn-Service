package edu.dosw.styleradar.virtualtryon.application.mapper.in;

import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricConsentResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricPhotoResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.GarmentValidationResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.TryOnJobResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.UserQuotaResponse;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentValidationResult;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.validation.GarmentSelectionPolicy;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mapper de entrada que transforma entidades del dominio puro hacia DTOs de respuesta REST (SR-RF-23 a SR-RF-26).
 */
@Component
public class VirtualTryOnDtoMapper {

    private static final Logger log = LoggerFactory.getLogger(VirtualTryOnDtoMapper.class);
    private static final Duration DEFAULT_ESTIMATED_DURATION = Duration.ofSeconds(25);

    private final BlobStoragePort blobStoragePort;
    private final Duration estimatedProcessingDuration;

    public VirtualTryOnDtoMapper() {
        this(null, DEFAULT_ESTIMATED_DURATION);
    }

    public VirtualTryOnDtoMapper(BlobStoragePort blobStoragePort) {
        this(blobStoragePort, DEFAULT_ESTIMATED_DURATION);
    }

    @Autowired(required = false)
    public VirtualTryOnDtoMapper(
            @Autowired(required = false) BlobStoragePort blobStoragePort,
            @Value("${styleradar.tryon.estimated-processing-duration:PT25S}") Duration estimatedProcessingDuration
    ) {
        this.blobStoragePort = blobStoragePort;
        this.estimatedProcessingDuration = estimatedProcessingDuration != null
                ? estimatedProcessingDuration
                : DEFAULT_ESTIMATED_DURATION;
    }

    public BiometricConsentResponse toConsentResponse(BiometricConsent consent) {
        return new BiometricConsentResponse(
                consent.getConsentId(),
                consent.getUserId().value(),
                consent.getTermsVersion(),
                consent.isAccepted(),
                consent.isValidForInference(),
                consent.getPurposes(),
                consent.getConsentedAt(),
                consent.getRevokedAt()
        );
    }

    public BiometricPhotoResponse toPhotoResponse(BiometricPhoto photo) {
        return new BiometricPhotoResponse(
                photo.getPhotoId(),
                photo.getUserId().value(),
                photo.getContentType(),
                photo.getWidth(),
                photo.getHeight(),
                photo.getSizeBytes(),
                resolveBiometricPhotoUrl(photo),
                photo.getUploadedAt(),
                photo.isActive()
        );
    }

    public UserQuotaResponse toQuotaResponse(UserQuota quota) {
        return new UserQuotaResponse(
                quota.getUserId().value(),
                quota.getMonthlyLimit(),
                quota.getUsedCount(),
                quota.getRemainingCount(),
                quota.getBillingPeriod().getValue(),
                Instant.now()
        );
    }

    public GarmentValidationResponse toGarmentValidationResponse(GarmentValidationResult result) {
        CatalogGarment garment = result.garment();
        return new GarmentValidationResponse(
                garment.getGarmentId().value(),
                garment.getName(),
                garment.getCategory().name(),
                garment.getCategory().getFashnCategoryCode(),
                garment.getImageUrl(),
                result.compatible(),
                result.visible(),
                result.zone() != null ? result.zone().name() : null,
                result.eligibleForTryOn(),
                result.validationMessage(),
                result.replacedGarmentIds().stream().map(GarmentId::value).toList(),
                garment.getUpdatedAt()
        );
    }

    public GarmentValidationResponse toGarmentValidationResponse(CatalogGarment garment) {
        return toGarmentValidationResponse(GarmentSelectionPolicy.evaluate(garment, List.of()));
    }

    public TryOnJobResponse toJobResponse(TryOnJob job) {
        return new TryOnJobResponse(
                job.getId(),
                job.getUserId().value(),
                job.getGarmentId().value(),
                job.getStatus().name(),
                temporaryPhotoUrl(job),
                temporaryResultUrl(job),
                job.getExecutionTimeMs(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt(),
                resolveEstimatedDurationSeconds(job)
        );
    }

    private Integer resolveEstimatedDurationSeconds(TryOnJob job) {
        if (job.getStatus() != JobStatus.QUEUED && job.getStatus() != JobStatus.PROCESSING) {
            return null;
        }
        Duration duration = estimatedProcessingDuration != null ? estimatedProcessingDuration : DEFAULT_ESTIMATED_DURATION;
        return (int) Math.max(1L, duration.toSeconds());
    }

    private String temporaryPhotoUrl(TryOnJob job) {
        if (job.isPhotoPurged() || job.getPhotoUrl() == null || job.getPhotoUrl().isBlank()) {
            return null;
        }
        return signReadOnlyUrl(job.getPhotoUrl(), job.getId(), "photoUrl");
    }

    private String temporaryResultUrl(TryOnJob job) {
        if (job.getResultImageUrl() == null || job.getResultImageUrl().value() == null
                || job.getResultImageUrl().value().isBlank()) {
            return null;
        }
        return signReadOnlyUrl(job.getResultImageUrl().value(), job.getId(), "resultImageUrl");
    }

    private String resolveBiometricPhotoUrl(BiometricPhoto photo) {
        String currentUrl = photo.getPhotoUrl() != null ? photo.getPhotoUrl().value() : null;
        if (hasSasSignature(currentUrl)) {
            return currentUrl;
        }
        String storageReference = (photo.getBlobPath() != null && !photo.getBlobPath().isBlank())
                ? photo.getBlobPath()
                : currentUrl;
        return signReadOnlyUrl(storageReference, photo.getPhotoId(), "biometricPhotoUrl");
    }

    private String signReadOnlyUrl(String rawStorageReference, UUID resourceId, String fieldName) {
        if (rawStorageReference == null || rawStorageReference.isBlank()) {
            return null;
        }
        if (blobStoragePort == null) {
            log.warn("Omitiendo referencia sin firmar en {} para recurso {} porque BlobStoragePort no está disponible",
                    fieldName, resourceId);
            return null;
        }
        try {
            SasSignedUrl signed = blobStoragePort.generateReadOnlySignedUrl(rawStorageReference);
            String signedValue = signed != null ? signed.value() : null;
            if (!hasSasSignature(signedValue)) {
                log.warn("No se generó firma SAS válida en {} para recurso {}; se omite la URL sin firmar",
                        fieldName, resourceId);
                return null;
            }
            return signedValue;
        } catch (RuntimeException ex) {
            log.warn("Error al generar firma SAS en {} para recurso {}: {}; se omite la referencia de almacenamiento",
                    fieldName, resourceId, ex.getMessage());
            return null;
        }
    }

    private boolean hasSasSignature(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        int queryIndex = url.indexOf('?');
        if (queryIndex < 0 || queryIndex == url.length() - 1) {
            return false;
        }
        String query = url.substring(queryIndex + 1);
        return hasNonEmptyParam(query, "sig")
                || (hasNonEmptyParam(query, "sp") && hasNonEmptyParam(query, "sr"))
                || hasNonEmptyParam(query, "sas");
    }

    private boolean hasNonEmptyParam(String query, String paramName) {
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && paramName.equals(pair.substring(0, eq)) && eq < pair.length() - 1) {
                return true;
            }
        }
        return false;
    }
}
