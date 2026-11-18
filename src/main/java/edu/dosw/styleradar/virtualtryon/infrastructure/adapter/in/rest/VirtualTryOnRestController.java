package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import edu.dosw.styleradar.virtualtryon.application.dto.request.StartTryOnRequest;
import edu.dosw.styleradar.virtualtryon.application.dto.response.GarmentValidationResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.TryOnJobResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.UserQuotaResponse;
import edu.dosw.styleradar.virtualtryon.application.mapper.in.VirtualTryOnDtoMapper;
import edu.dosw.styleradar.virtualtryon.application.service.CreateTryOnJobService;
import edu.dosw.styleradar.virtualtryon.application.service.DeleteBiometricDataService;
import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentValidationResult;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.doc.VirtualTryOnOpenApiDoc;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptador primario (Driving) REST para el microservicio de Probador Virtual.
 * Implementa la interfaz de documentación OpenAPI VirtualTryOnOpenApiDoc y delega
 * en CreateTryOnJobService, QueryTryOnJobsService y DeleteBiometricDataService (SR-RF-24 a SR-RF-27).
 * Admite de manera flexible identificadores canónicos UUID e identificadores numéricos emitidos por ms-auth.
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

    private final CreateTryOnJobService createTryOnJobService;
    private final QueryTryOnJobsService queryTryOnJobsService;
    private final DeleteBiometricDataService deleteBiometricDataService;
    private final VirtualTryOnDtoMapper dtoMapper;

    @Autowired
    public VirtualTryOnRestController(
            CreateTryOnJobService createTryOnJobService,
            QueryTryOnJobsService queryTryOnJobsService,
            DeleteBiometricDataService deleteBiometricDataService,
            VirtualTryOnDtoMapper dtoMapper
    ) {
        this.createTryOnJobService = createTryOnJobService;
        this.queryTryOnJobsService = queryTryOnJobsService;
        this.deleteBiometricDataService = deleteBiometricDataService;
        this.dtoMapper = dtoMapper;
    }

    public VirtualTryOnRestController(
            CreateTryOnJobService createTryOnJobService,
            QueryTryOnJobsService queryTryOnJobsService,
            VirtualTryOnDtoMapper dtoMapper
    ) {
        this(createTryOnJobService, queryTryOnJobsService, null, dtoMapper);
    }

    @Override
    @PostMapping("/jobs")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<TryOnJobResponse> startTryOn(@Valid @RequestBody StartTryOnRequest request) {
        UUID targetUserId = resolveAndVerifyCallerUserId(request.userId());
        UUID garmentUuid = GarmentId.parseToUuid(request.garmentId());
        if (garmentUuid == null) {
            throw new IllegalArgumentException("El identificador de la prenda (garmentId) no es válido");
        }
        TryOnJob createdJob = createTryOnJobService.createTryOnJob(
                UserId.of(targetUserId),
                GarmentId.of(garmentUuid),
                request.photoUrl(),
                Boolean.TRUE.equals(request.biometricConsent())
        );
        TryOnJobResponse response = dtoMapper.toJobResponse(createdJob);
        URI location = URI.create("/api/v1/try-on/jobs/" + createdJob.getId());
        return ResponseEntity.accepted().location(location).body(response);
    }

    @Override
    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<TryOnJobResponse> getTryOnJob(@PathVariable UUID jobId) {
        UUID callerUserId = resolveAndVerifyCallerUserId(null);
        boolean callerIsAdmin = isAdminCaller();
        TryOnJob job = queryTryOnJobsService.getTryOnJob(jobId, UserId.of(callerUserId), callerIsAdmin);
        return ResponseEntity.ok(dtoMapper.toJobResponse(job));
    }

    @Override
    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<List<TryOnJobResponse>> getUserTryOnHistory(@RequestParam(required = false) Object userId) {
        UUID targetUserId = resolveAndVerifyCallerUserId(userId);
        List<TryOnJobResponse> history = queryTryOnJobsService.getJobHistory(UserId.of(targetUserId)).stream()
                .map(dtoMapper::toJobResponse)
                .toList();
        return ResponseEntity.ok(history);
    }

    @Override
    @GetMapping("/quotas")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<UserQuotaResponse> getUserQuotas(@RequestParam(required = false) Object userId) {
        UUID targetUserId = resolveAndVerifyCallerUserId(userId);
        UserQuota quota = queryTryOnJobsService.getUserQuota(UserId.of(targetUserId));
        return ResponseEntity.ok(dtoMapper.toQuotaResponse(quota));
    }

    @Override
    @GetMapping("/garments/{garmentId}/validate")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<GarmentValidationResponse> validateGarmentForTryOn(
            @PathVariable("garmentId") Object garmentId,
            @RequestParam(value = "userId", required = false) Object userId,
            @RequestParam(value = "selectedGarmentIds", required = false) List<?> selectedGarmentIds
    ) {
        UUID targetUserId = resolveAndVerifyCallerUserId(userId);
        UUID garmentUuid = GarmentId.parseToUuid(garmentId);
        if (garmentUuid == null) {
            throw new IllegalArgumentException("El identificador de la prenda no es válido: " + garmentId);
        }
        List<GarmentId> selectedGarments = (selectedGarmentIds == null || selectedGarmentIds.isEmpty())
                ? List.of()
                : selectedGarmentIds.stream()
                        .map(GarmentId::parseToUuid)
                        .filter(java.util.Objects::nonNull)
                        .map(GarmentId::of)
                        .toList();

        GarmentValidationResult result = selectedGarments.isEmpty()
                ? queryTryOnJobsService.validateGarmentEligibility(
                        UserId.of(targetUserId),
                        GarmentId.of(garmentUuid)
                )
                : queryTryOnJobsService.validateGarmentEligibility(
                        UserId.of(targetUserId),
                        GarmentId.of(garmentUuid),
                        selectedGarments
                );
        return ResponseEntity.ok(dtoMapper.toGarmentValidationResponse(result));
    }

    @Override
    @DeleteMapping("/personal-photos/{jobId}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'USER', 'ADMIN', 'ADMIN_STYLERADAR')")
    public ResponseEntity<Void> deleteBiometricPhoto(@PathVariable UUID jobId) {
        UUID callerUserId = resolveAndVerifyCallerUserId(null);
        boolean isAdmin = isAdminCaller();
        deleteBiometricDataService.deleteBiometricPhoto(jobId, UserId.of(callerUserId), isAdmin);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveAndVerifyCallerUserId(Object requestedUserIdRaw) {
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

        UUID requestedUserId = UserId.parseToUuid(requestedUserIdRaw);
        if (requestedUserIdRaw != null && requestedUserId == null) {
            throw new AccessDeniedException(
                    "Identificador de usuario solicitado inválido para validar titularidad"
            );
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            UUID targetUserId = requestedUserId != null ? requestedUserId : authenticatedUser.userId();
            if (!isAdmin && !authenticatedUser.canAccessUser(targetUserId)) {
                throw new AccessDeniedException(
                        "No tiene permisos para operar en el Probador Virtual en nombre de otro usuario"
                );
            }
            return targetUserId;
        }

        String principalName = principal instanceof UserDetails userDetails
                ? userDetails.getUsername()
                : authentication.getName();
        UUID authenticatedUuid = UserId.parseToUuid(principalName);
        if (authenticatedUuid == null) {
            if (isAdmin && requestedUserId != null) {
                return requestedUserId;
            }
            throw new AccessDeniedException(
                    "Identidad de usuario autenticado inválida para validar titularidad en el Probador Virtual"
            );
        }
        UUID targetUserId = requestedUserId != null ? requestedUserId : authenticatedUuid;
        if (!isAdmin && !authenticatedUuid.equals(targetUserId)) {
            throw new AccessDeniedException(
                    "No tiene permisos para operar en el Probador Virtual en nombre de otro usuario"
            );
        }
        return targetUserId;
    }

    private boolean isAdminCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_ROLES::contains);
    }
}
