package edu.dosw.styleradar.virtualtryon.application.mapper.in;

import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricConsentResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricPhotoResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.GarmentValidationResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.TryOnJobResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.UserQuotaResponse;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Mapper de entrada que transforma entidades del dominio puro hacia DTOs de respuesta REST (SR-RF-23 a SR-RF-25).
 */
@Component
public class VirtualTryOnDtoMapper {

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
                job.getResultImageUrl() != null ? job.getResultImageUrl().value() : null,
                job.getExecutionTimeMs(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
