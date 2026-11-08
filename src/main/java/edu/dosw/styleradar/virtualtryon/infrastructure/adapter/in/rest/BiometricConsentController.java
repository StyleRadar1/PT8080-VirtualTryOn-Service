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
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptador REST de entrada que expone los endpoints de consentimiento biométrico (Ley 1581)
 * y de carga y validación de la fotografía frontal de silueta del usuario (SR-RF-23),
 * verificando identidad Bearer JWT, permisos de rol y titularidad del recurso biométrico.
 */
@RestController
@RequestMapping("/api/v1/try-on")
public class BiometricConsentController implements BiometricConsentOpenApiDoc {

    private static final Set<String> ALLOWED_BIOMETRIC_ROLES = Set.of(
            "ROLE_COMPRADOR",
            "ROLE_USER",
            "ROLE_ADMIN",
            "ROLE_ADMIN_STYLERADAR"
    );

    private static final Set<String> ADMIN_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_ADMIN_STYLERADAR"
    );

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
    @PostMapping("/consents")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<BiometricConsentResponse> grantConsent(@Valid @RequestBody BiometricConsentRequest request) {
        verifyCallerIdentityAndPermissions(request.userId());
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
    @GetMapping("/consents/{userId}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<BiometricConsentResponse> getConsent(@PathVariable("userId") UUID userId) {
        verifyCallerIdentityAndPermissions(userId);
        BiometricConsent consent = biometricConsentService.getConsentByUserId(UserId.of(userId));
        return ResponseEntity.ok(dtoMapper.toConsentResponse(consent));
    }

    @Override
    @DeleteMapping("/consents/{userId}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<BiometricConsentResponse> revokeConsent(@PathVariable("userId") UUID userId) {
        verifyCallerIdentityAndPermissions(userId);
        BiometricConsent revoked = biometricConsentService.revokeConsent(UserId.of(userId));
        return ResponseEntity.ok(dtoMapper.toConsentResponse(revoked));
    }

    @Override
    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<BiometricPhotoResponse> uploadPhoto(
            @RequestParam("userId") UUID userId,
            @RequestParam(value = "biometricConsent", defaultValue = "true") boolean biometricConsent,
            @RequestPart("photo") MultipartFile photo
    ) {
        verifyCallerIdentityAndPermissions(userId);
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
    @GetMapping("/photos/{userId}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<BiometricPhotoResponse> getPhoto(@PathVariable("userId") UUID userId) {
        verifyCallerIdentityAndPermissions(userId);
        BiometricPhoto photo = biometricConsentService.getPhotoByUserId(UserId.of(userId));
        return ResponseEntity.ok(dtoMapper.toPhotoResponse(photo));
    }

    private void verifyCallerIdentityAndPermissions(UUID targetUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new InsufficientAuthenticationException(
                    "Se requiere autenticación Bearer JWT para operar sobre información biométrica"
            );
        }

        boolean hasAllowedRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ALLOWED_BIOMETRIC_ROLES::contains);
        if (!hasAllowedRole) {
            throw new AccessDeniedException(
                    "El rol del usuario autenticado no tiene permisos para operar sobre datos biométricos"
            );
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_ROLES::contains);
        if (isAdmin) {
            return;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            if (!authenticatedUser.canAccessUser(targetUserId)) {
                throw new AccessDeniedException(
                        "No tiene permisos para consultar o modificar los datos biométricos de otro usuario"
                );
            }
            return;
        }

        String principalName = principal instanceof UserDetails userDetails
                ? userDetails.getUsername()
                : authentication.getName();
        try {
            UUID authenticatedUuid = UUID.fromString(principalName);
            if (!authenticatedUuid.equals(targetUserId)) {
                throw new AccessDeniedException(
                        "No tiene permisos para consultar o modificar los datos biométricos de otro usuario"
                );
            }
        } catch (IllegalArgumentException ignored) {
            throw new AccessDeniedException(
                    "Identidad de usuario autenticado inválida para validar titularidad biométrica"
            );
        }
    }
}
