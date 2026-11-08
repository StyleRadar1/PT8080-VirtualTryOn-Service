package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.BiometricConsentService;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.validation.BiometricConsentValidator;
import edu.dosw.styleradar.virtualtryon.domain.validation.ImageFormatResolutionValidator;
import edu.dosw.styleradar.virtualtryon.domain.validation.PoseReferenceValidator;
import edu.dosw.styleradar.virtualtryon.domain.validation.TryOnValidationContext;
import edu.dosw.styleradar.virtualtryon.domain.validation.TryOnValidatorHandler;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implementación del servicio de aplicación para la gestión de consentimiento biométrico (Ley 1581)
 * y la carga validada de la fotografía frontal del usuario (SR-RF-23) aplicando el patrón
 * Chain of Responsibility (BiometricConsentValidator -> ImageFormatResolutionValidator -> PoseReferenceValidator).
 */
@Service
public class BiometricConsentServiceImpl implements BiometricConsentService {

    private static final Logger log = LoggerFactory.getLogger(BiometricConsentServiceImpl.class);
    private static final String DEFAULT_TERMS_VERSION = "v1.0-LEY1581-2026";

    private final BiometricDataRepositoryPort biometricDataRepositoryPort;
    private final BlobStoragePort blobStoragePort;
    private final TryOnValidatorHandler photoValidationChain;

    public BiometricConsentServiceImpl(
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            BlobStoragePort blobStoragePort
    ) {
        this.biometricDataRepositoryPort = biometricDataRepositoryPort;
        this.blobStoragePort = blobStoragePort;
        this.photoValidationChain = buildPhotoValidationChain();
    }

    private static TryOnValidatorHandler buildPhotoValidationChain() {
        BiometricConsentValidator consentValidator = new BiometricConsentValidator();
        ImageFormatResolutionValidator formatValidator = new ImageFormatResolutionValidator();
        PoseReferenceValidator poseValidator = new PoseReferenceValidator();

        consentValidator.setNext(formatValidator).setNext(poseValidator);
        return consentValidator;
    }

    @Override
    public BiometricConsent grantConsent(
            UserId userId,
            String termsVersion,
            boolean accepted,
            List<String> purposes,
            String ipAddress
    ) {
        if (!accepted) {
            throw new ConsentRequiredException(userId.value());
        }
        BiometricConsent consent = new BiometricConsent(
                UUID.randomUUID(),
                userId,
                termsVersion,
                true,
                purposes,
                Instant.now(),
                null,
                ipAddress
        );
        BiometricConsent saved = biometricDataRepositoryPort.saveConsent(consent);
        log.info("Consentimiento biométrico registrado para el usuario {} con versión {}", userId.value(), termsVersion);
        return saved;
    }

    @Override
    public BiometricConsent getConsentByUserId(UserId userId) {
        return biometricDataRepositoryPort.findConsentByUserId(userId)
                .orElseThrow(() -> new ConsentRequiredException(userId.value()));
    }

    @Override
    public BiometricConsent revokeConsent(UserId userId) {
        BiometricConsent existing = getConsentByUserId(userId);
        existing.revoke(Instant.now());
        BiometricConsent updated = biometricDataRepositoryPort.saveConsent(existing);
        log.info("Consentimiento biométrico revocado para el usuario {}", userId.value());
        return updated;
    }

    @Override
    public BiometricPhoto uploadPhoto(
            UserId userId,
            boolean biometricConsentAccepted,
            String contentType,
            byte[] photoBytes
    ) {
        boolean hasPersistedValidConsent = biometricDataRepositoryPort.findConsentByUserId(userId)
                .map(BiometricConsent::isValidForInference)
                .orElse(false);

        boolean effectiveConsent = biometricConsentAccepted || hasPersistedValidConsent;

        TryOnValidationContext validationContext = new TryOnValidationContext(
                userId,
                effectiveConsent,
                contentType,
                photoBytes
        );
        photoValidationChain.validate(validationContext);

        if (!hasPersistedValidConsent) {
            BiometricConsent autoRegisteredConsent = new BiometricConsent(
                    UUID.randomUUID(),
                    userId,
                    DEFAULT_TERMS_VERSION,
                    true,
                    List.of("VIRTUAL_TRY_ON_INFERENCE", "TEMPORARY_SILHOUETTE_STORAGE"),
                    Instant.now(),
                    null,
                    "0.0.0.0"
            );
            biometricDataRepositoryPort.saveConsent(autoRegisteredConsent);
        }

        biometricDataRepositoryPort.findPhotoByUserId(userId)
                .ifPresent(previousPhoto -> blobStoragePort.deleteBlob(previousPhoto.getPhotoUrl()));

        SasSignedUrl storedUrl = blobStoragePort.storeBiometricPhoto(userId, contentType, photoBytes);

        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(),
                userId,
                contentType.toLowerCase(),
                validationContext.getImageWidth(),
                validationContext.getImageHeight(),
                validationContext.getImageSizeBytes(),
                storedUrl,
                Instant.now()
        );

        BiometricPhoto savedPhoto = biometricDataRepositoryPort.savePhoto(photo);
        log.info("Fotografía frontal validada y almacenada para el usuario {} ({}x{} px, {} bytes)",
                userId.value(), savedPhoto.getWidth(), savedPhoto.getHeight(), savedPhoto.getSizeBytes());
        return savedPhoto;
    }

    @Override
    public BiometricPhoto getPhotoByUserId(UserId userId) {
        return biometricDataRepositoryPort.findPhotoByUserId(userId)
                .orElseThrow(() -> new JobNotFoundException(userId.value()));
    }
}

