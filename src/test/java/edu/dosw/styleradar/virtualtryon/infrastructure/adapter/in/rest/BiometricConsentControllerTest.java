package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dosw.styleradar.virtualtryon.application.dto.request.BiometricConsentRequest;
import edu.dosw.styleradar.virtualtryon.application.mapper.in.VirtualTryOnDtoMapper;
import edu.dosw.styleradar.virtualtryon.application.service.BiometricConsentService;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.AuthenticatedUser;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.JwtAuthenticationFilter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.JwtTokenProvider;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.SecurityProblemHandler;
import edu.dosw.styleradar.virtualtryon.infrastructure.config.SecurityConfig;
import edu.dosw.styleradar.virtualtryon.infrastructure.exception.GlobalExceptionHandler;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de contrato REST, autenticación JWT y validación de titularidad/permisos
 * para BiometricConsentController (SR-RF-23).
 */
@WebMvcTest(controllers = BiometricConsentController.class)
@Import({
        VirtualTryOnDtoMapper.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtTokenProvider.class,
        SecurityProblemHandler.class
})
@ActiveProfiles("test")
class BiometricConsentControllerTest {

    private static final UUID USER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID OTHER_USER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private BiometricConsentService biometricConsentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String bearerToken(UUID userId, String role) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, role);
    }

    private BiometricConsent sampleConsent(Instant revokedAt) {
        return new BiometricConsent(
                UUID.fromString("7f8e9d10-1234-4a5b-8c9d-0123456789ab"),
                UserId.of(USER_UUID),
                "v1.0-LEY1581-2026",
                true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"),
                Instant.parse("2026-11-07T22:00:00Z"),
                revokedAt,
                "190.24.112.45"
        );
    }

    private BiometricPhoto samplePhoto() {
        return new BiometricPhoto(
                UUID.fromString("9a8b7c6d-5432-4f1e-9d8c-76543210fedc"),
                UserId.of(USER_UUID),
                "image/png",
                400,
                600,
                2048L,
                "pose.png",
                SasSignedUrl.of("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/pose.png"),
                Instant.parse("2026-11-07T22:05:00Z")
        );
    }

    @Test
    @DisplayName("HU SR-RF-23 (POST /api/v1/try-on/consents): Dado un usuario COMPRADOR autenticado con JWT propio, Cuando otorga consentimiento Ley 1581, Entonces responde 201 Created")
    void shouldGrantBiometricConsent() throws Exception {
        when(biometricConsentService.grantConsent(any(), any(), anyBoolean(), any(), any()))
                .thenReturn(sampleConsent(null));

        BiometricConsentRequest request = new BiometricConsentRequest(
                USER_UUID,
                "v1.0-LEY1581-2026",
                true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"),
                "190.24.112.45"
        );

        mockMvc.perform(post("/api/v1/try-on/consents")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Frame-Options", "DENY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Security-Policy", "default-src 'self'"))
                .andExpect(jsonPath("$.userId").value(USER_UUID.toString()))
                .andExpect(jsonPath("$.validForInference").value(true));
    }

    @Test
    @DisplayName("HU SR-RF-23 (GET y DELETE /api/v1/try-on/consents/{userId}): Dado un usuario autenticado propietario o ADMIN, Cuando consulta o revoca su consentimiento, Entonces responde 200 OK")
    void shouldGetAndRevokeBiometricConsent() throws Exception {
        when(biometricConsentService.getConsentByUserId(eq(UserId.of(USER_UUID))))
                .thenReturn(sampleConsent(null));
        when(biometricConsentService.revokeConsent(eq(UserId.of(USER_UUID))))
                .thenReturn(sampleConsent(Instant.parse("2026-11-07T22:10:00Z")));

        mockMvc.perform(get("/api/v1/try-on/consents/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validForInference").value(true));

        mockMvc.perform(delete("/api/v1/try-on/consents/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(OTHER_USER_UUID, "ADMIN_STYLERADAR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validForInference").value(false));
    }

    @Test
    @DisplayName("HU SR-RF-23 (Seguridad 401 y 403): Cuando se invocan endpoints biométricos sin JWT, con JWT inválido o intentando acceder a otro usuario, Entonces responde 401 Unauthorized o 403 Forbidden")
    void shouldRejectUnauthenticatedOrUnauthorizedBiometricRequests() throws Exception {
        // 1. Sin header Authorization -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/try-on/consents/{userId}", USER_UUID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        // 2. Con header Bearer mal formado -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/try-on/consents/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        // 3. Con token JWT adulterado/inválido -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/try-on/photos/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token.invalido.firma"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        // 4. Usuario autenticado intentando consultar el consentimiento de OTRO usuario -> 403 Forbidden
        mockMvc.perform(get("/api/v1/try-on/consents/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(OTHER_USER_UUID, "COMPRADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // 5. Usuario con rol no permitido (ej. VISITANTE) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/try-on/consents/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "VISITANTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("HU SR-RF-23 (POST /api/v1/try-on/photos): Dado un archivo multipart vertical válido y JWT del propietario, Cuando se carga, Entonces responde 201 Created")
    void shouldUploadBiometricPhoto() throws Exception {
        when(biometricConsentService.uploadPhoto(eq(UserId.of(USER_UUID)), eq(true), any(), any()))
                .thenReturn(samplePhoto());

        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "pose.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(file)
                        .param("userId", USER_UUID.toString())
                        .param("biometricConsent", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(400))
                .andExpect(jsonPath("$.height").value(600))
                .andExpect(jsonPath("$.contentType").value("image/png"));

        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .param("userId", USER_UUID.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_REQUEST_PART"));
    }

    @Test
    @DisplayName("HU SR-RF-23 (POST /api/v1/try-on/photos - Errores 400 y 422): Dada una carga sin consentimiento o con imagen inválida, Cuando se procesa, Entonces responde ProblemDetail 400 o 422")
    void shouldReturnProblemDetailOnConsentOrInvalidPhoto() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "bad.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2}
        );

        when(biometricConsentService.uploadPhoto(eq(UserId.of(USER_UUID)), eq(false), any(), any()))
                .thenThrow(new ConsentRequiredException(USER_UUID));

        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(file)
                        .param("userId", USER_UUID.toString())
                        .param("biometricConsent", "false")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CONSENT_REQUIRED"));

        when(biometricConsentService.uploadPhoto(eq(UserId.of(USER_UUID)), eq(true), any(), any()))
                .thenThrow(new InvalidPhotoException("La fotografía debe medir al menos 300x400 píxeles"));

        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(file)
                        .param("userId", USER_UUID.toString())
                        .param("biometricConsent", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PHOTO"));
    }

    @Test
    @DisplayName("HU SR-RF-23 (GET /api/v1/try-on/photos/{userId}): Dado un usuario autenticado con o sin fotografía, Cuando se consulta, Entonces responde 200 OK o 404 Not Found")
    void shouldGetBiometricPhotoOrReturnNotFound() throws Exception {
        when(biometricConsentService.getPhotoByUserId(eq(UserId.of(USER_UUID))))
                .thenReturn(samplePhoto())
                .thenThrow(new JobNotFoundException(USER_UUID));

        mockMvc.perform(get("/api/v1/try-on/photos/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_UUID.toString()));

        mockMvc.perform(get("/api/v1/try-on/photos/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("HU SR-RF-23 (Validaciones directas de Controlador y JWT): Valida ramas de IOException, tipos de Principal y validación de JwtTokenProvider/AuthenticatedUser")
    void shouldValidateDirectControllerOwnershipAndJwtProviderBranches() throws Exception {
        BiometricConsentController controller = new BiometricConsentController(
                biometricConsentService,
                new VirtualTryOnDtoMapper()
        );

        // Sin autenticación en SecurityContext -> InsufficientAuthenticationException
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> controller.getConsent(USER_UUID))
                .isInstanceOf(InsufficientAuthenticationException.class);

        // Con rol inválido en invocación directa -> AccessDeniedException
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(USER_UUID, "ROLE_GUEST"),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
                )
        );
        assertThatThrownBy(() -> controller.getConsent(USER_UUID))
                .isInstanceOf(AccessDeniedException.class);

        // Con UserDetails cuyo username es UUID coincidente y fallo IOException en MultipartFile
        User springUser = new User(USER_UUID.toString(), "", List.of(new SimpleGrantedAuthority("ROLE_COMPRADOR")));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(springUser, null, springUser.getAuthorities())
        );
        MultipartFile brokenFile = mock(MultipartFile.class);
        when(brokenFile.getBytes()).thenThrow(new IOException("Fallo de lectura"));
        assertThatThrownBy(() -> controller.uploadPhoto(USER_UUID, true, brokenFile))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("No fue posible procesar los bytes");

        // Con UserDetails cuyo username es otro UUID -> AccessDeniedException
        assertThatThrownBy(() -> controller.getConsent(OTHER_USER_UUID))
                .isInstanceOf(AccessDeniedException.class);

        // Con Principal String que no es UUID -> AccessDeniedException
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "non-uuid-username",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_COMPRADOR"))
                )
        );
        assertThatThrownBy(() -> controller.getConsent(USER_UUID))
                .isInstanceOf(AccessDeniedException.class);

        // Pruebas de invariantes en AuthenticatedUser y JwtTokenProvider
        AuthenticatedUser adminUser = new AuthenticatedUser(USER_UUID, "ROLE_ADMIN");
        assertThat(adminUser.canAccessUser(OTHER_USER_UUID)).isTrue();
        assertThat(adminUser.canAccessUser(null)).isFalse();
        assertThatThrownBy(() -> new AuthenticatedUser(USER_UUID, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        String validSecret = "U3R5bGVSYWRhclZpcnR1YWxUcnlPblNlY3JldEtleTIwMjZQb3J0YWw4MDgwU2Vj";
        assertThatThrownBy(() -> new JwtTokenProvider(null, Duration.ofMinutes(10), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenProvider("   ", Duration.ofMinutes(10), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenProvider("@@@not-base64@@@", Duration.ofMinutes(10), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenProvider("c2hvcnQ=", Duration.ofMinutes(10), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenProvider(validSecret, Duration.ZERO, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> jwtTokenProvider.generateToken(null, "COMPRADOR"))
                .isInstanceOf(IllegalArgumentException.class);

        // Token con rol nulo asigna COMPRADOR por defecto
        String defaultRoleToken = jwtTokenProvider.generateToken(USER_UUID, null);
        AuthenticatedUser extracted = jwtTokenProvider.validateAndExtractUser(defaultRoleToken);
        assertThat(extracted.role()).isEqualTo("COMPRADOR");

        // Token expirado lanza JwtException
        Clock pastClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        JwtTokenProvider pastProvider = new JwtTokenProvider(validSecret, Duration.ofSeconds(5), pastClock);
        String expiredToken = pastProvider.generateToken(USER_UUID, "COMPRADOR");
        assertThatThrownBy(() -> jwtTokenProvider.validateAndExtractUser(expiredToken))
                .isInstanceOf(JwtException.class);
    }
}
