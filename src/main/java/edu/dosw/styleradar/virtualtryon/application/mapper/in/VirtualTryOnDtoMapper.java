package edu.dosw.styleradar.virtualtryon.application.mapper.in;

import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricConsentResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricPhotoResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.GarmentValidationResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.TryOnJobResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.UserQuotaResponse;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Mapper de entrada que transforma entidades del dominio puro hacia DTOs de respuesta REST (SR-RF-23 a SR-RF-26).
 */
@Component
public class VirtualTryOnDtoMapper {

    private final BlobStoragePort blobStoragePort;
    private final Duration estimatedProcessingDuration;

    public VirtualTryOnDtoMapper() {
        this(null, Duration.ofSeconds(30));
    }

    public VirtualTryOnDtoMapper(BlobStoragePort blobStoragePort) {
        this(blobStoragePort, Duration.ofSeconds(30));
    }

    @Autowired(required = false)
    public VirtualTryOnDtoMapper(
            @Autowired(required = false) BlobStoragePort blobStoragePort,
            @Value("${styleradar.tryon.estimated-processing-duration:PT30S}") Duration estimatedProcessingDuration
    ) {
        this.blobStoragePort = blobStoragePort;
        this.estimatedProcessingDuration = estimatedProcessingDuration != null ? estimatedProcessingDuration : Duration.ofSeconds(30);
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
                photo.getPhotoUrl().value(),
                photo.getUploadedAt()
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

    public GarmentValidationResponse toGarmentValidationResponse(CatalogGarment garment) {
        return new GarmentValidationResponse(
                garment.getGarmentId().value(),
                garment.getName(),
                garment.getCategory().name(),
                garment.getCategory().getFashnCategoryCode(),
                garment.getImageUrl(),
                garment.isEligibleForTryOn(),
                garment.getUpdatedAt()
        );
    }

    public TryOnJobResponse toJobResponse(TryOnJob job) {
        return new TryOnJobResponse(
                job.getId(),
                job.getUserId().value(),
                job.getGarmentId().value(),
                job.getStatus().name(),
                temporaryResultUrl(job),
                job.getExecutionTimeMs(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                resolveEstimatedCompletion(job)
        );
    }

    private Instant resolveEstimatedCompletion(TryOnJob job) {
        if (job.getStatus() != JobStatus.QUEUED && job.getStatus() != JobStatus.PROCESSING) {
            return null;
        }
        Instant baseTime = job.getCreatedAt() != null ? job.getCreatedAt() : Instant.now();
        Duration duration = estimatedProcessingDuration != null ? estimatedProcessingDuration : Duration.ofSeconds(30);
        return baseTime.plus(duration);
    }

    private String temporaryResultUrl(TryOnJob job) {
        if (job.getResultImageUrl() == null) return null;
        if (blobStoragePort == null) return job.getResultImageUrl().value();
        return blobStoragePort.generateReadOnlySignedUrl(job.getResultImageUrl().value()).value();
    }
}
