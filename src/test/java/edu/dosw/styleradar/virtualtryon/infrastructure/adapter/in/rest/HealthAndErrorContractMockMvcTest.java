package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import edu.dosw.styleradar.virtualtryon.application.mapper.in.VirtualTryOnDtoMapper;
import edu.dosw.styleradar.virtualtryon.application.service.CreateTryOnJobService;
import edu.dosw.styleradar.virtualtryon.application.service.DeleteBiometricDataService;
import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobAccessDeniedException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HealthRestController.class, VirtualTryOnRestController.class})
@Import({
        VirtualTryOnDtoMapper.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtTokenProvider.class,
        SecurityProblemHandler.class
})
@ActiveProfiles("test")
class HealthAndErrorContractMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CreateTryOnJobService createTryOnJobService;

    @MockBean
    private QueryTryOnJobsService queryTryOnJobsService;

    @MockBean
    private DeleteBiometricDataService deleteBiometricDataService;

    @MockBean
    private BlobStoragePort blobStoragePort;

    private String buyerToken(UUID userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "BUYER");
    }

    @Test
    @DisplayName("Smoke Test: /api/v1/health debe ser accesible públicamente sin token JWT (200 OK)")
    void shouldAllowPublicAccessToApiV1HealthWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("PT8080-VirtualTryOn-Service"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Smoke Test: /api/v1/try-on/health debe ser accesible públicamente sin token JWT (200 OK)")
    void shouldAllowPublicAccessToTryOnHealthWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/try-on/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("PT8080-VirtualTryOn-Service"));
    }

    @Test
    @DisplayName("Contrato RFC 7807: Solicitud sin JWT retorna 401 con type https://api.styleradar.com/errors/unauthorized")
    void shouldReturnHomologatedErrorTypeOnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/try-on/jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.type").value("https://api.styleradar.com/errors/unauthorized"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Contrato RFC 7807: Parámetro UUID malformado retorna 400 con type https://api.styleradar.com/errors/invalid-parameter")
    void shouldReturnHomologatedErrorTypeOnInvalidParameter() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/try-on/jobs/uuid-invalido-123")
                        .header(HttpHeaders.AUTHORIZATION, buyerToken(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.styleradar.com/errors/invalid-parameter"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("Contrato RFC 7807: Recurso inexistente retorna 404 con type https://api.styleradar.com/errors/job-not-found")
    void shouldReturnHomologatedErrorTypeOnNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(queryTryOnJobsService.getTryOnJob(any(UUID.class), any(), any(Boolean.class)))
                .thenThrow(new JobNotFoundException(jobId));

        mockMvc.perform(get("/api/v1/try-on/jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, buyerToken(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.styleradar.com/errors/job-not-found"))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("Contrato RFC 7807: Violación de titularidad o permisos retorna 403 con type https://api.styleradar.com/errors/forbidden")
    void shouldReturnHomologatedErrorTypeOnForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(queryTryOnJobsService.getTryOnJob(any(UUID.class), any(), any(Boolean.class)))
                .thenThrow(new JobAccessDeniedException("No tiene permisos para acceder a este trabajo"));

        mockMvc.perform(get("/api/v1/try-on/jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, buyerToken(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://api.styleradar.com/errors/forbidden"))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
