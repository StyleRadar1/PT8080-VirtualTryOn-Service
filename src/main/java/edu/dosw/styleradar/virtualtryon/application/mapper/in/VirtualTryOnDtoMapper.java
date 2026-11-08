package edu.dosw.styleradar.virtualtryon.application.mapper.in;

import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricConsentResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricPhotoResponse;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import org.springframework.stereotype.Component;

/**
 * Mapper de entrada que transforma entidades del dominio puro hacia DTOs de respuesta REST (SR-RF-23).
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
}

