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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptador REST de entrada que expone los endpoints de consentimiento biométrico (Ley 1581)
 * y de carga y validación de la fotografía frontal de silueta del usuario (SR-RF-23),
 * verificando identidad Bearer JWT, permisos de rol y titularidad del recurso biométrico.
 * Admite tanto identificadores canónicos UUID como IDs numéricos emitidos por ms-auth.
 */
@RestController
@RequestMapping("/api/v1/try-on")
public class BiometricConsentController implements BiometricConsentOpenApiDoc {

    private static final Set<String> ALLOWED_BIOMETRIC_ROLES = Set.of(
            "ROLE_COMPRADOR",
            "ROLE_USER",
            "ROLE_ADMIN",
            "ROLE_ADMIN_STYLERADAR",
            "ROLE_ADMIN_TIENDA",
            "COMPRADOR",
            "USER",
            "ADMIN",
            "ADMIN_STYLERADAR",
            "ADMIN_TIENDA"
    );

    private static final Set<String> ADMIN_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_ADMIN_STYLERADAR",
            "ADMIN",
            "ADMIN_STYLERADAR"
    );

    private final BiometricConsentService biometricConsentService;
    private final VirtualTryOnDtoMapper dtoMapper;
    private final List<String> trustedProxies;

    public BiometricConsentController(
            BiometricConsentService biometricConsentService,
            VirtualTryOnDtoMapper dtoMapper
    ) {
        this(biometricConsentService, dtoMapper, List.of("127.0.0.1", "::1"));
    }

    @Autowired
    public BiometricConsentController(
            BiometricConsentService biometricConsentService,
            VirtualTryOnDtoMapper dtoMapper,
            @Value("${styleradar.tryon.trusted-proxies:127.0.0.1,::1}") List<String> trustedProxies
    ) {
        this.biometricConsentService = biometricConsentService;
        this.dtoMapper = dtoMapper;
        this.trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }

    @Override
    @PostMapping("/consents")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR', 'ADMIN_TIENDA')")
    public ResponseEntity<BiometricConsentResponse> grantConsent(@Valid @RequestBody BiometricConsentRequest request) {
        UUID effectiveUserId = resolveAndVerifyCallerUserId(request.userId());
        String effectiveIp = resolveClientIp();
        BiometricConsent consent = biometricConsentService.grantConsent(
                UserId.of(effectiveUserId),
                request.termsVersion(),
                Boolean.TRUE.equals(request.accepted()),
                request.purposes(),
                effectiveIp
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toConsentResponse(consent));
    }

    @Override
    @GetMapping("/consents/{userId}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR', 'ADMIN_TIENDA')")
    public ResponseEntity<BiometricConsentResponse> getConsent(@PathVariable("userId") Object userId) {
        UUID effectiveUserId = resolveAndVerifyCallerUserId(userId);
        BiometricConsent consent = biometricConsentService.getConsentByUserId(UserId.of(effectiveUserId));
        return ResponseEntity.ok(dtoMapper.toConsentResponse(consent));
    }

    @Override
    @DeleteMapping("/consents/{userId}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR', 'ADMIN_TIENDA')")
    public ResponseEntity<BiometricConsentResponse> revokeConsent(@PathVariable("userId") Object userId) {
        UUID effectiveUserId = resolveAndVerifyCallerUserId(userId);
        BiometricConsent revoked = biometricConsentService.revokeConsent(UserId.of(effectiveUserId));
        return ResponseEntity.ok(dtoMapper.toConsentResponse(revoked));
    }

    @Override
    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR', 'ADMIN_TIENDA')")
    public ResponseEntity<BiometricPhotoResponse> uploadPhoto(
            @RequestParam(value = "userId", required = false) Object userId,
            @RequestParam(value = "biometricConsent", required = false, defaultValue = "true") boolean biometricConsent,
            @RequestPart("photo") MultipartFile photo
    ) {
        UUID effectiveUserId = resolveAndVerifyCallerUserId(userId);
        try {
            byte[] bytes = photo == null ? null : photo.getBytes();
            String contentType = photo == null ? null : photo.getContentType();
            BiometricPhoto uploaded = biometricConsentService.uploadPhoto(
                    UserId.of(effectiveUserId),
                    biometricConsent,
                    contentType,
                    bytes,
                    resolveClientIp()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toPhotoResponse(uploaded));
        } catch (IOException ex) {
            throw new InvalidPhotoException("No fue posible procesar los bytes de la fotografía recibida");
        }
    }

    @Override
    @GetMapping("/photos/{userId}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR', 'ADMIN_TIENDA')")
    public ResponseEntity<BiometricPhotoResponse> getPhoto(@PathVariable("userId") Object userId) {
        UUID effectiveUserId = resolveAndVerifyCallerUserId(userId);
        BiometricPhoto photo = biometricConsentService.getPhotoByUserId(UserId.of(effectiveUserId));
        return ResponseEntity.ok(dtoMapper.toPhotoResponse(photo));
    }

    /**
     * Resuelve la IP a registrar. Las cabeceras X-Forwarded-For / X-Real-IP solo se consideran si la
     * conexión directa proviene de un proxy confiable; de lo contrario cualquier cliente podría falsificarlas.
     */
    private String resolveClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (!isIpLiteral(hop)) {
                    break;
                }
                if (!isTrustedProxy(hop)) {
                    return hop;
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && isIpLiteral(realIp.trim()) && !isTrustedProxy(realIp.trim())) {
            return realIp.trim();
        }
        return remoteAddr;
    }

    private static boolean isIpLiteral(String value) {
        if (value == null || value.isEmpty() || value.length() > 45) {
            return false;
        }
        if (value.indexOf(':') >= 0) {
            if (!value.matches("[0-9a-fA-F:.]+")) {
                return false;
            }
        } else if (!value.matches("(\\d{1,3})(\\.\\d{1,3}){3}")) {
            return false;
        }
        try {
            java.net.InetAddress.getByName(value);
            return true;
        } catch (java.net.UnknownHostException ex) {
            return false;
        }
    }

    private boolean isTrustedProxy(String address) {
        if (!isIpLiteral(address)) {
            return false;
        }
        try {
            byte[] candidate = java.net.InetAddress.getByName(address).getAddress();
            for (String entry : trustedProxies) {
                String[] parts = entry.split("/", 2);
                if (!isIpLiteral(parts[0].trim())) {
                    continue;
                }
                byte[] network = java.net.InetAddress.getByName(parts[0].trim()).getAddress();
                if (network.length != candidate.length) {
                    continue;
                }
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : network.length * 8;
                if (prefix >= 0 && prefix <= network.length * 8 && matchesPrefix(candidate, network, prefix)) {
                    return true;
                }
            }
        } catch (java.net.UnknownHostException | NumberFormatException ex) {
            return false;
        }
        return false;
    }

    private static boolean matchesPrefix(byte[] candidate, byte[] network, int prefixBits) {
        int fullBytes = prefixBits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (candidate[i] != network[i]) {
                return false;
            }
        }
        int remaining = prefixBits % 8;
        if (remaining == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remaining);
        return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
    }

    private UUID resolveAndVerifyCallerUserId(Object targetUserIdRaw) {
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

        UUID authenticatedUuid = extractAuthenticatedUserId(authentication);
        UUID targetUserId = UserId.parseToUuid(targetUserIdRaw);
        if (targetUserIdRaw != null && targetUserId == null) {
            throw new AccessDeniedException(
                    "Identificador de usuario inválido para validar titularidad biométrica"
            );
        }

        if (targetUserId == null) {
            return authenticatedUuid;
        }
        if (authenticatedUuid.equals(targetUserId)) {
            return targetUserId;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_ROLES::contains);
        if (isAdmin) {
            return targetUserId;
        }

        throw new AccessDeniedException(
                "No tiene permisos para consultar o modificar los datos biométricos de otro usuario"
        );
    }

    private UUID extractAuthenticatedUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.userId();
        }
        String principalName = principal instanceof UserDetails userDetails
                ? userDetails.getUsername()
                : authentication.getName();
        UUID parsed = UserId.parseToUuid(principalName);
        if (parsed == null) {
            throw new AccessDeniedException(
                    "Identidad de usuario autenticado inválida para validar titularidad biométrica"
            );
        }
        return parsed;
    }
}
