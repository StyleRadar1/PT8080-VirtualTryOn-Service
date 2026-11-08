package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import edu.dosw.styleradar.virtualtryon.application.dto.request.StartTryOnRequest;
import edu.dosw.styleradar.virtualtryon.application.dto.response.GarmentValidationResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.TryOnJobResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.UserQuotaResponse;
import edu.dosw.styleradar.virtualtryon.application.mapper.in.VirtualTryOnDtoMapper;
import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.doc.VirtualTryOnOpenApiDoc;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.AuthenticatedUser;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptador primario (Driving) REST para el microservicio de Probador Virtual.
 * Implementa la interfaz de documentación OpenAPI VirtualTryOnOpenApiDoc y delega
 * la consulta de cuotas mensuales y elegibilidad de prendas en QueryTryOnJobsService (SR-RF-24).
 */
@RestController
@RequestMapping("/api/v1/try-on")
public class VirtualTryOnRestController implements VirtualTryOnOpenApiDoc {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ROLE_COMPRADOR",
            "ROLE_USER",
            "ROLE_ADMIN",
            "ROLE_ADMIN_STYLERADAR"
    );

    private static final Set<String> ADMIN_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_ADMIN_STYLERADAR"
    );

    private final QueryTryOnJobsService queryTryOnJobsService;
    private final VirtualTryOnDtoMapper dtoMapper;

    public VirtualTryOnRestController(
            QueryTryOnJobsService queryTryOnJobsService,
            VirtualTryOnDtoMapper dtoMapper
    ) {
        this.queryTryOnJobsService = queryTryOnJobsService;
        this.dtoMapper = dtoMapper;
    }

    @Override
    @PostMapping("/jobs")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<TryOnJobResponse> startTryOn(@Valid @RequestBody StartTryOnRequest request) {
        UUID newJobId = UUID.randomUUID();
        TryOnJobResponse response = new TryOnJobResponse(
                newJobId,
                request.userId(),
                request.garmentId(),
                "QUEUED",
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        URI location = URI.create("/api/v1/try-on/jobs/" + newJobId);
        return ResponseEntity.accepted().location(location).body(response);
    }

    @Override
    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<TryOnJobResponse> getTryOnJob(@PathVariable UUID jobId) {
        TryOnJobResponse response = new TryOnJobResponse(
                jobId,
                UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"),
                UUID.fromString("f8e7d6c5-b4a3-2109-8765-43210fedcba9"),
                "COMPLETED",
                "https://styleradar.blob.core.windows.net/renders/render-" + jobId + ".png",
                2100L,
                null,
                Instant.now().minusSeconds(10),
                Instant.now()
        );
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/quotas")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<UserQuotaResponse> getUserQuotas(@RequestParam(required = false) UUID userId) {
        UUID targetUserId = resolveAndVerifyCallerUserId(userId);
        UserQuota quota = queryTryOnJobsService.getUserQuota(UserId.of(targetUserId));
        return ResponseEntity.ok(dtoMapper.toQuotaResponse(quota));
    }

    @Override
    @GetMapping("/garments/{garmentId}/validate")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<GarmentValidationResponse> validateGarmentForTryOn(
            @PathVariable("garmentId") UUID garmentId,
            @RequestParam("userId") UUID userId
    ) {
        UUID targetUserId = resolveAndVerifyCallerUserId(userId);
        CatalogGarment garment = queryTryOnJobsService.validateGarmentEligibility(
                UserId.of(targetUserId),
                GarmentId.of(garmentId)
        );
        return ResponseEntity.ok(dtoMapper.toGarmentValidationResponse(garment));
    }

    @Override
    @DeleteMapping("/personal-photos/{jobId}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<Void> deleteBiometricPhoto(@PathVariable UUID jobId) {
        return ResponseEntity.noContent().build();
    }

    private UUID resolveAndVerifyCallerUserId(UUID requestedUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new InsufficientAuthenticationException(
                    "Se requiere autenticación Bearer JWT para consultar cuotas o validar prendas del probador virtual"
            );
        }

        boolean hasAllowedRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ALLOWED_ROLES::contains);
        if (!hasAllowedRole) {
            throw new AccessDeniedException(
                    "El rol del usuario autenticado no tiene permisos para acceder al Probador Virtual"
            );
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_ROLES::contains);

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            UUID targetUserId = requestedUserId != null ? requestedUserId : authenticatedUser.userId();
            if (!isAdmin && !authenticatedUser.canAccessUser(targetUserId)) {
                throw new AccessDeniedException(
                        "No tiene permisos para consultar cuotas o validar prendas en nombre de otro usuario"
                );
            }
            return targetUserId;
        }

        String principalName = principal instanceof UserDetails userDetails
                ? userDetails.getUsername()
                : authentication.getName();
        try {
            UUID authenticatedUuid = UUID.fromString(principalName);
            UUID targetUserId = requestedUserId != null ? requestedUserId : authenticatedUuid;
            if (!isAdmin && !authenticatedUuid.equals(targetUserId)) {
                throw new AccessDeniedException(
                        "No tiene permisos para consultar cuotas o validar prendas en nombre de otro usuario"
                );
            }
            return targetUserId;
        } catch (IllegalArgumentException ignored) {
            if (isAdmin && requestedUserId != null) {
                return requestedUserId;
            }
            throw new AccessDeniedException(
                    "Identidad de usuario autenticado inválida para validar titularidad en el Probador Virtual"
            );
        }
    }
}
