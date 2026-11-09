package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dosw.styleradar.virtualtryon.application.dto.request.StartTryOnRequest;
import edu.dosw.styleradar.virtualtryon.application.mapper.in.VirtualTryOnDtoMapper;
import edu.dosw.styleradar.virtualtryon.application.service.CreateTryOnJobService;
import edu.dosw.styleradar.virtualtryon.application.service.DeleteBiometricDataService;
import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobAccessDeniedException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.JwtAuthenticationFilter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.JwtTokenProvider;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.SecurityProblemHandler;
import edu.dosw.styleradar.virtualtryon.infrastructure.config.SecurityConfig;
import edu.dosw.styleradar.virtualtryon.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de API (Capa de Adaptador REST / Controladores Web) mediante MockMvc con seguridad JWT real activa.
 * Valida contratos HTTP, cabeceras, autenticación 401, autorización/titularidad 403 y códigos de respuesta (SR-RF-24).
 */
@WebMvcTest(controllers = VirtualTryOnRestController.class)
@Import({
        VirtualTryOnDtoMapper.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtTokenProvider.class,
        SecurityProblemHandler.class
})
@ActiveProfiles("test")
class VirtualTryOnRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CreateTryOnJobService createTryOnJobService;

    @MockBean
    private QueryTryOnJobsService queryTryOnJobsService;

    @MockBean
    private DeleteBiometricDataService deleteBiometricDataService;

    private String bearerTokenFor(UUID userId, String role) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, role);
    }

    @Test
    @DisplayName("HU SR-RF-25 (V04): Dado una solicitud válida con foto y prenda compatible, Cuando se envía POST /api/v1/try-on/jobs, Entonces responde 202 Accepted con cabecera Location y estado del job")
    void testStartTryOnSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID garmentId = UUID.randomUUID();
        String photoUrl = "https://styleradar.blob.core.windows.net/photos/user-test.jpg";
        StartTryOnRequest request = new StartTryOnRequest(
                userId,
                garmentId,
                photoUrl,
                true
        );

        TryOnJob createdJob = new TryOnJobBuilder()
                .withUserId(UserId.of(userId))
                .withGarmentId(GarmentId.of(garmentId))
                .withCategory(GarmentCategory.TOPS)
                .withPhotoUrl(photoUrl)
                .build();
        when(createTryOnJobService.createTryOnJob(
                UserId.of(userId),
                GarmentId.of(garmentId),
                photoUrl,
                true
        )).thenReturn(createdJob);

        mockMvc.perform(post("/api/v1/try-on/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.jobId", is(createdJob.getId().toString())))
                .andExpect(jsonPath("$.status", is("QUEUED")));
    }

    @Test
    @DisplayName("HU SR-RF-23 (V03): Dado un cuerpo de solicitud vacío sin campos obligatorios, Cuando se envía POST /api/v1/try-on/jobs, Entonces responde 400 Bad Request con ProblemDetail de validación")
    void testStartTryOnInvalidRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/try-on/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Error de Validación de Entrada")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.invalidFields", notNullValue()));
    }

    @Test
    @DisplayName("HU SR-RF-26 (V05): Dado un jobId existente de un job completado o historial del usuario, Cuando se envía GET /api/v1/try-on/jobs/{id} y GET /api/v1/try-on/jobs, Entonces responde 200 OK con estado COMPLETED y URL temporal firmada SAS")
    void testGetTryOnJob() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        TryOnJob completedJob = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(UserId.of(userId))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withCategory(GarmentCategory.TOPS)
                .withPhotoUrl("https://styleradar.blob.core.windows.net/photos/user-test.jpg")
                .build();
        completedJob.complete(
                SasSignedUrl.of("https://styleradar.blob.core.windows.net/renders/render-" + jobId + ".png?sr=b&sp=r&spr=https"),
                2100L
        );

        when(queryTryOnJobsService.getJobStatus(eq(jobId), eq(UserId.of(userId)), anyBoolean())).thenReturn(completedJob);
        when(queryTryOnJobsService.getTryOnJob(eq(jobId), eq(UserId.of(userId)), anyBoolean())).thenReturn(completedJob);
        when(queryTryOnJobsService.getJobHistory(UserId.of(userId))).thenReturn(List.of(completedJob));

        mockMvc.perform(get("/api/v1/try-on/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(jobId.toString())))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.resultImageUrl", notNullValue()));

        mockMvc.perform(get("/api/v1/try-on/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId", is(jobId.toString())))
                .andExpect(jsonPath("$[0].status", is("COMPLETED")));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado un usuario autenticado con userId propio, Cuando se envía GET /api/v1/try-on/quotas, Entonces responde 200 OK con cuotas consumidas y saldo restante desde QueryTryOnJobsService")
    void testGetUserQuotas() throws Exception {
        UUID userId = UUID.randomUUID();
        UserQuota quota = new UserQuota(
                UUID.randomUUID(),
                UserId.of(userId),
                10,
                3,
                BillingPeriod.of("2026-11"),
                false,
                Instant.now()
        );
        when(queryTryOnJobsService.getUserQuota(UserId.of(userId))).thenReturn(quota);

        mockMvc.perform(get("/api/v1/try-on/quotas")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .param("userId", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userId.toString())))
                .andExpect(jsonPath("$.monthlyLimit", is(10)))
                .andExpect(jsonPath("$.usedCount", is(3)))
                .andExpect(jsonPath("$.remainingCount", is(7)))
                .andExpect(jsonPath("$.billingPeriod", is("2026-11")));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado un usuario sin userId explícito en la URL, Cuando se envía GET /api/v1/try-on/quotas, Entonces toma el userId del token JWT autenticado y responde 200 OK")
    void testGetUserQuotasDefaultAuthenticatedUser() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UserQuota quota = UserQuota.createDefault(UserId.of(authenticatedUserId), BillingPeriod.of("2026-11"));
        when(queryTryOnJobsService.getUserQuota(UserId.of(authenticatedUserId))).thenReturn(quota);

        mockMvc.perform(get("/api/v1/try-on/quotas")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(authenticatedUserId, "COMPRADOR"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(authenticatedUserId.toString())))
                .andExpect(jsonPath("$.remainingCount", is(10)));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dada una prenda compatible y cuota disponible, Cuando se envía GET /api/v1/try-on/garments/{garmentId}/validate, Entonces responde 200 OK con GarmentValidationResponse")
    void testValidateGarmentForTryOnSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID garmentId = UUID.randomUUID();
        CatalogGarment garment = new CatalogGarment(
                GarmentId.of(garmentId),
                "Chaqueta Denim Clásica",
                GarmentCategory.TOPS,
                "https://styleradar.blob.core.windows.net/catalog/denim.png",
                true,
                Instant.now()
        );
        when(queryTryOnJobsService.validateGarmentEligibility(UserId.of(userId), GarmentId.of(garmentId)))
                .thenReturn(garment);

        mockMvc.perform(get("/api/v1/try-on/garments/{garmentId}/validate", garmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .param("userId", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.garmentId", is(garmentId.toString())))
                .andExpect(jsonPath("$.category", is("TOPS")))
                .andExpect(jsonPath("$.fashnCategoryCode", is("tops")))
                .andExpect(jsonPath("$.eligibleForTryOn", is(true)));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dada prenda incompatible o cuota agotada, Cuando se envía GET /api/v1/try-on/garments/{garmentId}/validate, Entonces responde 400 Bad Request o 429 Too Many Requests con ProblemDetail")
    void testValidateGarmentForTryOnFailures() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID incompatibleGarmentId = UUID.randomUUID();
        UUID quotaExhaustedGarmentId = UUID.randomUUID();

        when(queryTryOnJobsService.validateGarmentEligibility(UserId.of(userId), GarmentId.of(incompatibleGarmentId)))
                .thenThrow(new InvalidGarmentException(incompatibleGarmentId, "El probador virtual no admite prendas de tipo FOOTWEAR"));
        when(queryTryOnJobsService.validateGarmentEligibility(UserId.of(userId), GarmentId.of(quotaExhaustedGarmentId)))
                .thenThrow(new QuotaExceededException(userId, 10, 10));

        mockMvc.perform(get("/api/v1/try-on/garments/{garmentId}/validate", incompatibleGarmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .param("userId", userId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_GARMENT")));

        mockMvc.perform(get("/api/v1/try-on/garments/{garmentId}/validate", quotaExhaustedGarmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .param("userId", userId.toString()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode", is("QUOTA_EXCEEDED")));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado usuario no autenticado o intentando consultar cuota de otro usuario sin ser ADMIN, Cuando invoca /quotas o /garments/{id}/validate, Entonces responde 401 o 403")
    void testQuotasAndGarmentValidationSecurityAndOwnership() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        UUID garmentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/try-on/quotas").param("userId", authenticatedUserId.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));

        mockMvc.perform(get("/api/v1/try-on/quotas")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(authenticatedUserId, "COMPRADOR"))
                        .param("userId", anotherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));

        mockMvc.perform(get("/api/v1/try-on/garments/{garmentId}/validate", garmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(authenticatedUserId, "COMPRADOR"))
                        .param("userId", anotherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));

        // ADMIN puede consultar cuotas y validar prendas de cualquier usuario
        UserQuota adminTargetQuota = UserQuota.createDefault(UserId.of(anotherUserId), BillingPeriod.of("2026-11"));
        when(queryTryOnJobsService.getUserQuota(UserId.of(anotherUserId))).thenReturn(adminTargetQuota);
        mockMvc.perform(get("/api/v1/try-on/quotas")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(authenticatedUserId, "ADMIN"))
                        .param("userId", anotherUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(anotherUserId.toString())));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado invocación directa del controlador con UserDetails, sin autenticación o con rol inválido, Cuando se invocan los endpoints, Entonces valida todos los caminos de seguridad y titularidad")
    void testDirectControllerPrincipalBranches() {
        VirtualTryOnRestController directController = new VirtualTryOnRestController(
                createTryOnJobService,
                queryTryOnJobsService,
                new VirtualTryOnDtoMapper()
        );
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserQuota quota = UserQuota.createDefault(UserId.of(userId), BillingPeriod.of("2026-11"));
        when(queryTryOnJobsService.getUserQuota(UserId.of(userId))).thenReturn(quota);

        try {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.security.authentication.InsufficientAuthenticationException.class,
                    () -> directController.getUserQuotas(userId)
            );

            // Rol no permitido
            var badRoleAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    userId.toString(), null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_GUEST"))
            );
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(badRoleAuth);
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.security.access.AccessDeniedException.class,
                    () -> directController.getUserQuotas(userId)
            );

            // UserDetails con UUID propio y sin parámetro userId
            var userDetails = org.springframework.security.core.userdetails.User.withUsername(userId.toString())
                    .password("N/A")
                    .authorities("ROLE_USER")
                    .build();
            var validUserDetailsAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(validUserDetailsAuth);
            org.junit.jupiter.api.Assertions.assertEquals(200, directController.getUserQuotas(null).getStatusCode().value());

            // UserDetails intentando consultar otro usuario sin ser ADMIN
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.security.access.AccessDeniedException.class,
                    () -> directController.getUserQuotas(otherUserId)
            );

            // Principal con nombre no-UUID y no-admin -> AccessDeniedException
            var invalidNameAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "not-a-uuid", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_COMPRADOR"))
            );
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(invalidNameAuth);
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.security.access.AccessDeniedException.class,
                    () -> directController.getUserQuotas(userId)
            );

            // Principal con nombre no-UUID pero con rol ADMIN y requestedUserId explícito -> 200 OK
            var adminNameAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "admin-service", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(adminNameAuth);
            org.junit.jupiter.api.Assertions.assertEquals(200, directController.getUserQuotas(userId).getStatusCode().value());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("HU SR-RF-27 (V05): Dado un jobId de foto corporal existente, Cuando se envía DELETE /api/v1/try-on/personal-photos/{id}, Entonces responde 204 No Content para purga física definitiva (Ley 1581)")
    void testDeleteBiometricPhoto() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/try-on/personal-photos/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR")))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(deleteBiometricDataService)
                .deleteBiometricPhoto(jobId, UserId.of(userId), false);
    }

    @Test
    @DisplayName("HU SR-RF-25: Consulta de trabajo por un usuario que no es el propietario ni administrador retorna 403 Forbidden sin exponer información biométrica")
    void testGetTryOnJobForbiddenForNonOwnerNonAdmin() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID otherUserJobId = UUID.randomUUID();

        when(queryTryOnJobsService.getTryOnJob(eq(otherUserJobId), eq(UserId.of(authenticatedUserId)), eq(false)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar este trabajo"));

        mockMvc.perform(get("/api/v1/try-on/jobs/{jobId}", otherUserJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(authenticatedUserId, "COMPRADOR"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")))
                .andExpect(jsonPath("$.photoUrl").doesNotExist())
                .andExpect(jsonPath("$.resultImageUrl").doesNotExist());
    }

    @Test
    @DisplayName("HU SR-RF-26: Dado un jobId en estado PROCESSING, Cuando se envía GET /api/v1/try-on/jobs/{id}, Entonces responde 200 OK con estado PROCESSING y estimatedCompletionAt")
    void testGetTryOnJobProcessingWithEstimatedCompletion() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        TryOnJob processingJob = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(UserId.of(userId))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withCategory(GarmentCategory.TOPS)
                .withPhotoUrl("https://styleradar.blob.core.windows.net/photos/user-test.jpg")
                .build();
        processingJob.startAiProcessing();

        when(queryTryOnJobsService.getTryOnJob(eq(jobId), eq(UserId.of(userId)), anyBoolean()))
                .thenReturn(processingJob);

        mockMvc.perform(get("/api/v1/try-on/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(jobId.toString())))
                .andExpect(jsonPath("$.status", is("PROCESSING")))
                .andExpect(jsonPath("$.estimatedCompletionAt", notNullValue()))
                .andExpect(jsonPath("$.resultImageUrl").doesNotExist());
    }

    @Test
    @DisplayName("HU SR-RF-26: Dado un jobId en estado FAILED, Cuando se envía GET /api/v1/try-on/jobs/{id}, Entonces responde 200 OK con estado FAILED y errorMessage descriptivo")
    void testGetTryOnJobFailedWithErrorMessage() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        TryOnJob failedJob = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(UserId.of(userId))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withCategory(GarmentCategory.TOPS)
                .withPhotoUrl("https://styleradar.blob.core.windows.net/photos/user-test.jpg")
                .build();
        failedJob.markFailed("Pose corporal incompatible con la prenda seleccionada");

        when(queryTryOnJobsService.getTryOnJob(eq(jobId), eq(UserId.of(userId)), anyBoolean()))
                .thenReturn(failedJob);

        mockMvc.perform(get("/api/v1/try-on/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(jobId.toString())))
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", is("Pose corporal incompatible con la prenda seleccionada")))
                .andExpect(jsonPath("$.resultImageUrl").doesNotExist());
    }

    @Test
    @DisplayName("HU SR-RF-26: Dado un jobId inexistente, Cuando se envía GET /api/v1/try-on/jobs/{id}, Entonces responde 404 Not Found con ProblemDetail estándar")
    void testGetTryOnJobNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID nonExistentJobId = UUID.randomUUID();

        when(queryTryOnJobsService.getTryOnJob(eq(nonExistentJobId), eq(UserId.of(userId)), anyBoolean()))
                .thenThrow(new JobNotFoundException(nonExistentJobId));

        mockMvc.perform(get("/api/v1/try-on/jobs/{jobId}", nonExistentJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId, "COMPRADOR"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.errorCode", is("JOB_NOT_FOUND")))
                .andExpect(jsonPath("$.title", is("Trabajo de Probador Virtual No Encontrado")));
    }

    @Test
    @DisplayName("HU SR-RF-26: Dado un usuario no propietario ni admin, Cuando el servicio lanza JobAccessDeniedException, Entonces responde 403 Forbidden")
    void testGetTryOnJobWithDomainAccessDeniedException() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID otherUserJobId = UUID.randomUUID();

        when(queryTryOnJobsService.getTryOnJob(eq(otherUserJobId), eq(UserId.of(authenticatedUserId)), anyBoolean()))
                .thenThrow(new JobAccessDeniedException("El usuario autenticado no tiene permisos para acceder al trabajo"));

        mockMvc.perform(get("/api/v1/try-on/jobs/{jobId}", otherUserJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(authenticatedUserId, "COMPRADOR"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")))
                .andExpect(jsonPath("$.title", is("Acceso Denegado")));
    }
}
