package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import edu.dosw.styleradar.virtualtryon.application.dto.request.BiometricConsentRequest;
import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricConsentResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricPhotoResponse;
import edu.dosw.styleradar.virtualtryon.application.mapper.in.VirtualTryOnDtoMapper;
import edu.dosw.styleradar.virtualtryon.application.service.BiometricConsentService;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.doc.BiometricConsentOpenApiDoc;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Adaptador REST de entrada que expone los endpoints de consentimiento biométrico (Ley 1581)
 * y de carga y validación de la fotografía frontal de silueta del usuario (SR-RF-23).
 */
@RestController
public class BiometricConsentController implements BiometricConsentOpenApiDoc {

    private final BiometricConsentService biometricConsentService;
    private final VirtualTryOnDtoMapper dtoMapper;

    public BiometricConsentController(
            BiometricConsentService biometricConsentService,
            VirtualTryOnDtoMapper dtoMapper
    ) {
        this.biometricConsentService = biometricConsentService;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public ResponseEntity<BiometricConsentResponse> grantConsent(BiometricConsentRequest request) {
        BiometricConsent consent = biometricConsentService.grantConsent(
                UserId.of(request.userId()),
                request.termsVersion(),
                Boolean.TRUE.equals(request.accepted()),
                request.purposes(),
                request.ipAddress()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toConsentResponse(consent));
    }

    @Override
    public ResponseEntity<BiometricConsentResponse> getConsent(UUID userId) {
        BiometricConsent consent = biometricConsentService.getConsentByUserId(UserId.of(userId));
        return ResponseEntity.ok(dtoMapper.toConsentResponse(consent));
    }

    @Override
    public ResponseEntity<BiometricConsentResponse> revokeConsent(UUID userId) {
        BiometricConsent revoked = biometricConsentService.revokeConsent(UserId.of(userId));
        return ResponseEntity.ok(dtoMapper.toConsentResponse(revoked));
    }

    @Override
    public ResponseEntity<BiometricPhotoResponse> uploadPhoto(
            UUID userId,
            boolean biometricConsent,
            MultipartFile photo
    ) {
        try {
            byte[] bytes = photo == null ? null : photo.getBytes();
            String contentType = photo == null ? null : photo.getContentType();
            BiometricPhoto uploaded = biometricConsentService.uploadPhoto(
                    UserId.of(userId),
                    biometricConsent,
                    contentType,
                    bytes
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toPhotoResponse(uploaded));
        } catch (IOException ex) {
            throw new InvalidPhotoException("No fue posible procesar los bytes de la fotografía recibida");
        }
    }

    @Override
    public ResponseEntity<BiometricPhotoResponse> getPhoto(UUID userId) {
        BiometricPhoto photo = biometricConsentService.getPhotoByUserId(UserId.of(userId));
        return ResponseEntity.ok(dtoMapper.toPhotoResponse(photo));
    }
}

