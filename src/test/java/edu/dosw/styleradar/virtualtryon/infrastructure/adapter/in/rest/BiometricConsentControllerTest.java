package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dosw.styleradar.virtualtryon.application.dto.request.BiometricConsentRequest;
import edu.dosw.styleradar.virtualtryon.application.mapper.in.VirtualTryOnDtoMapper;
import edu.dosw.styleradar.virtualtryon.application.service.BiometricConsentService;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.BiometricPhotoNotFoundException;
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
import io.jsonwebtoken.Jwts;
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

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
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
@WebMvcTest(controllers = BiometricConsentController.class,
        properties = "styleradar.tryon.trusted-proxies=127.0.0.1,::1,10.0.0.0/8")
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
                        .string("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'"))
                .andExpect(jsonPath("$.userId").value(USER_UUID.toString()))
                .andExpect(jsonPath("$.validForInference").value(true));
    }

    @Test
    @DisplayName("HU SR-RF-23 (POST /api/v1/try-on/consents): Cuando el cuerpo omite userId, se infiere del JWT sub y responde 201 Created")
    void shouldGrantBiometricConsentInferringUserIdFromJwt() throws Exception {
        when(biometricConsentService.grantConsent(eq(UserId.of(USER_UUID)), any(), anyBoolean(), any(), any()))
                .thenReturn(sampleConsent(null));

        BiometricConsentRequest requestWithoutUserId = new BiometricConsentRequest(
                null,
                "v1.0-LEY1581-2026",
                true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"),
                null
        );

        mockMvc.perform(post("/api/v1/try-on/consents")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestWithoutUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_UUID.toString()));
    }

    @Test
    @DisplayName("SR-RF-23 / Seguridad: Usuario con rol ADMIN_TIENDA tiene permisos para operar sobre datos biométricos")
    void shouldAllowAdminTiendaRole() throws Exception {
        when(biometricConsentService.getConsentByUserId(eq(UserId.of(OTHER_USER_UUID))))
                .thenReturn(sampleConsent(null));

        mockMvc.perform(get("/api/v1/try-on/consents/" + OTHER_USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "ADMIN_TIENDA")))
                .andExpect(status().isOk());
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
        when(biometricConsentService.uploadPhoto(eq(UserId.of(USER_UUID)), eq(true), any(), any(), eq("203.0.113.10")))
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
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(400))
                .andExpect(jsonPath("$.height").value(600))
                .andExpect(jsonPath("$.contentType").value("image/png"));

        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .param("userId", USER_UUID.toString())
                        .param("biometricConsent", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_REQUEST_PART"));

        // Parámetro biometricConsent opcional con valor por defecto true (SR-RF-23 homologación de contrato)
        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(file)
                        .param("userId", USER_UUID.toString())
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(400));

        // Parámetro userId opcional deducido automáticamente de la identidad JWT del usuario autenticado
        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(file)
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(400));
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

        when(biometricConsentService.uploadPhoto(eq(UserId.of(USER_UUID)), eq(false), any(), any(), any()))
                .thenThrow(new ConsentRequiredException(USER_UUID));

        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(file)
                        .param("userId", USER_UUID.toString())
                        .param("biometricConsent", "false")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CONSENT_REQUIRED"));

        when(biometricConsentService.uploadPhoto(eq(UserId.of(USER_UUID)), eq(true), any(), any(), any()))
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
                .thenThrow(new BiometricPhotoNotFoundException(USER_UUID));

        mockMvc.perform(get("/api/v1/try-on/photos/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_UUID.toString()));

        mockMvc.perform(get("/api/v1/try-on/photos/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BIOMETRIC_PHOTO_NOT_FOUND"));
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

        // Token multi-rol ("roles": ["USER", "ADMIN"]) con sub numérico + claim userId UUID
        byte[] decodedSecret = Base64.getDecoder().decode(validSecret);
        SecretKeySpec signingKey = new SecretKeySpec(decodedSecret, "HmacSHA256");
        Instant now = Instant.now();
        String multiClaimJwt = Jwts.builder()
                .subject("98765")
                .claim("userId", USER_UUID.toString())
                .claim("roles", List.of("ROLE_USER", "ADMIN"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        AuthenticatedUser multiClaimUser = jwtTokenProvider.validateAndExtractUser(multiClaimJwt);
        assertThat(multiClaimUser.userId()).isEqualTo(USER_UUID);
        assertThat(multiClaimUser.roles()).containsExactly("USER", "ADMIN");
        assertThat(multiClaimUser.canAccessUser(OTHER_USER_UUID)).isTrue();

        // Token CSV en "roles" sin role individual también es soportado
        String csvRolesJwt = Jwts.builder()
                .subject(USER_UUID.toString())
                .claim("roles", "comprador, role_admin_styleradar")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        AuthenticatedUser csvUser = jwtTokenProvider.validateAndExtractUser(csvRolesJwt);
        assertThat(csvUser.roles()).containsExactly("COMPRADOR", "ADMIN_STYLERADAR");

        // SR-RF-23: Token con claim user_id snake_case y authorities (formato OAuth2 / Spring Security)
        String snakeUserIdJwt = Jwts.builder()
                .subject("auth0|usuario-externo")
                .claim("user_id", USER_UUID.toString())
                .claim("authorities", List.of("ROLE_COMPRADOR", "ROLE_USER"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        AuthenticatedUser snakeUser = jwtTokenProvider.validateAndExtractUser(snakeUserIdJwt);
        assertThat(snakeUser.userId()).isEqualTo(USER_UUID);
        assertThat(snakeUser.roles()).contains("COMPRADOR", "USER");

        // SR-RF-23: Token con claim id y scope separado por espacios
        String scopeJwt = Jwts.builder()
                .subject("usuario-con-scope")
                .claim("id", USER_UUID.toString())
                .claim("scope", "ROLE_COMPRADOR ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        AuthenticatedUser scopeUser = jwtTokenProvider.validateAndExtractUser(scopeJwt);
        assertThat(scopeUser.userId()).isEqualTo(USER_UUID);
        assertThat(scopeUser.roles()).contains("COMPRADOR", "ADMIN");

        // Token sin roles o con roles vacíos lanza JwtException y HTTP responde 401
        String noRoleJwt = Jwts.builder()
                .subject(USER_UUID.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        assertThatThrownBy(() -> jwtTokenProvider.validateAndExtractUser(noRoleJwt))
                .isInstanceOf(JwtException.class);
        mockMvc.perform(get("/api/v1/try-on/consents/{userId}", USER_UUID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + noRoleJwt))
                .andExpect(status().isUnauthorized());

        // /actuator/metrics restringido a ADMIN/ADMIN_STYLERADAR (401 sin token, 403 para COMPRADOR)
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + multiClaimJwt))
                .andExpect(status().is(org.hamcrest.Matchers.not(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(401),
                        org.hamcrest.Matchers.is(403)
                ))));

        // Fase B extra: grantConsent resuelve IP desde infraestructura del servidor (X-Real-IP) e ignora ipAddress falso enviado por el cliente
        when(biometricConsentService.grantConsent(any(), any(), anyBoolean(), any(), eq("198.51.100.77")))
                .thenReturn(sampleConsent(null));
        BiometricConsentRequest spoofedIpRequest = new BiometricConsentRequest(
                USER_UUID, "v1.0-LEY1581-2026", true, List.of("VIRTUAL_TRY_ON_INFERENCE"), "203.0.113.199"
        );
        mockMvc.perform(post("/api/v1/try-on/consents")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR"))
                        .header("X-Real-IP", "198.51.100.77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spoofedIpRequest)))
                .andExpect(status().isCreated());

        when(biometricConsentService.uploadPhoto(eq(UserId.of(USER_UUID)), eq(true), any(), any(), eq("198.51.100.88")))
                .thenReturn(samplePhoto());
        MockMultipartFile validPngStub = new MockMultipartFile("photo", "p.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2});
        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(validPngStub)
                        .param("userId", USER_UUID.toString())
                        .param("biometricConsent", "true")
                        .header("X-Forwarded-For", "   ")
                        .header("X-Real-IP", "198.51.100.88")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isCreated());

        when(biometricConsentService.uploadPhoto(eq(UserId.of(USER_UUID)), eq(true), any(), any(), eq("127.0.0.1")))
                .thenReturn(samplePhoto());
        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(validPngStub)
                        .param("userId", USER_UUID.toString())
                        .param("biometricConsent", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR")))
                .andExpect(status().isCreated());

        // Invocación directa uploadPhoto con photo=null y RequestContextHolder vacío
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        BiometricConsentController directCtrl = new BiometricConsentController(biometricConsentService, new VirtualTryOnDtoMapper());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(USER_UUID, "ADMIN_STYLERADAR"),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN_STYLERADAR"))
                )
        );
        when(biometricConsentService.uploadPhoto(eq(UserId.of(OTHER_USER_UUID)), eq(true), eq(null), eq(null), eq(null)))
                .thenReturn(samplePhoto());
        assertThat(directCtrl.uploadPhoto(OTHER_USER_UUID, true, null).getStatusCode().value()).isEqualTo(201);

        // Cobertura de AuthenticatedUser y JwtTokenProvider (branches de validación JWT y roles)
        AuthenticatedUser nullPrimaryRole = new AuthenticatedUser(USER_UUID, null, List.of("ADMIN_STYLERADAR"));
        assertThat(nullPrimaryRole.role()).isEqualTo("ADMIN_STYLERADAR");
        assertThat(nullPrimaryRole.canAccessUser(OTHER_USER_UUID)).isTrue();
        assertThat(new AuthenticatedUser(USER_UUID, "COMPRADOR", null).roles()).containsExactly("COMPRADOR");
        assertThatThrownBy(() -> new AuthenticatedUser(USER_UUID, null, List.of("  ")))
                .isInstanceOf(IllegalArgumentException.class);

        SecretKeySpec hs384Key = new SecretKeySpec(decodedSecret, "HmacSHA384");
        String wrongAlgJwt = Jwts.builder().subject(USER_UUID.toString()).claim("role", "COMPRADOR")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(hs384Key, Jwts.SIG.HS384).compact();
        assertThatThrownBy(() -> jwtTokenProvider.validateAndExtractUser(wrongAlgJwt)).isInstanceOf(JwtException.class);

        String futureIatJwt = Jwts.builder().subject(USER_UUID.toString()).claim("role", "COMPRADOR")
                .issuedAt(Date.from(now.plusSeconds(300))).expiration(Date.from(now.plusSeconds(600)))
                .signWith(signingKey, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> jwtTokenProvider.validateAndExtractUser(futureIatJwt)).isInstanceOf(JwtException.class);

        String missingIatJwt = Jwts.builder().subject(USER_UUID.toString()).claim("role", "COMPRADOR")
                .expiration(Date.from(now.plusSeconds(300))).signWith(signingKey, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> jwtTokenProvider.validateAndExtractUser(missingIatJwt)).isInstanceOf(JwtException.class);

        String missingSubjectJwt = Jwts.builder().claim("userId", "not-a-uuid").claim("role", "COMPRADOR")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> jwtTokenProvider.validateAndExtractUser(missingSubjectJwt)).isInstanceOf(JwtException.class);

        String numericSubjectJwt = Jwts.builder().subject("12345").claim("role", "COMPRADOR")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256).compact();
        assertThat(jwtTokenProvider.validateAndExtractUser(numericSubjectJwt).userId()).isNotNull();

        // Validaciones avanzadas de claims JWT: authorities como CSV, scope como colección/string, claims user_id/id
        UUID altUuid = UUID.randomUUID();
        String authoritiesCsvJwt = Jwts.builder().subject(USER_UUID.toString())
                .claim("authorities", "ROLE_COMPRADOR,ROLE_ADMIN_STYLERADAR")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256).compact();
        assertThat(jwtTokenProvider.validateAndExtractUser(authoritiesCsvJwt).roles())
                .contains("COMPRADOR", "ADMIN_STYLERADAR");

        String scopeCollectionJwt = Jwts.builder().subject("john_doe")
                .claim("user_id", "not-a-uuid")
                .claim("id", altUuid.toString())
                .claim("scope", List.of("ROLE_COMPRADOR", "ROLE_USER"))
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256).compact();
        AuthenticatedUser scopeCollUser = jwtTokenProvider.validateAndExtractUser(scopeCollectionJwt);
        assertThat(scopeCollUser.userId()).isEqualTo(altUuid);
        assertThat(scopeCollUser.roles()).contains("COMPRADOR", "USER");

        String scopeStringJwt = Jwts.builder().subject(USER_UUID.toString())
                .claim("scope", "ROLE_COMPRADOR ROLE_VENTAS")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256).compact();
        assertThat(jwtTokenProvider.validateAndExtractUser(scopeStringJwt).roles()).contains("COMPRADOR", "VENTAS");

        // Cobertura completa de resolveAndVerifyCallerUserId con UserDetails y roles
        when(biometricConsentService.getConsentByUserId(eq(UserId.of(USER_UUID)))).thenReturn(sampleConsent(null));
        when(biometricConsentService.getConsentByUserId(eq(UserId.of(OTHER_USER_UUID)))).thenReturn(sampleConsent(null));

        // 1. targetUserId == null -> infiere ID del usuario autenticado
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(USER_UUID, "COMPRADOR"), null,
                        List.of(new SimpleGrantedAuthority("ROLE_COMPRADOR"))
                )
        );
        assertThat(directCtrl.getConsent(null).getStatusCode().value()).isEqualTo(200);

        // 2. UserDetails como principal para usuario comprador consultando su propio consentimiento
        org.springframework.security.core.userdetails.UserDetails buyerDetails =
                User.withUsername(USER_UUID.toString()).password("pwd").roles("COMPRADOR").build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(buyerDetails, null, buyerDetails.getAuthorities())
        );
        assertThat(directCtrl.getConsent(USER_UUID).getStatusCode().value()).isEqualTo(200);

        // 3. UserDetails como principal no-admin consultando consentimiento ajeno -> 403
        assertThatThrownBy(() -> directCtrl.getConsent(OTHER_USER_UUID)).isInstanceOf(AccessDeniedException.class);

        // 4. UserDetails como principal ADMIN consultando consentimiento ajeno -> 200
        org.springframework.security.core.userdetails.UserDetails adminDetails =
                User.withUsername(USER_UUID.toString()).password("pwd").roles("ADMIN_STYLERADAR").build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(adminDetails, null, adminDetails.getAuthorities())
        );
        assertThat(directCtrl.getConsent(OTHER_USER_UUID).getStatusCode().value()).isEqualTo(200);

        // 5. UserDetails con nombre de usuario no UUID -> AccessDeniedException
        org.springframework.security.core.userdetails.UserDetails invalidIdDetails =
                User.withUsername("not-a-valid-uuid").password("pwd").roles("ADMIN_STYLERADAR").build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(invalidIdDetails, null, invalidIdDetails.getAuthorities())
        );
        assertThatThrownBy(() -> directCtrl.getConsent(USER_UUID)).isInstanceOf(AccessDeniedException.class);

        // 6. Usuario sin rol permitido
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(USER_UUID, "GUEST"), null,
                        List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
                )
        );
        assertThatThrownBy(() -> directCtrl.getConsent(USER_UUID)).isInstanceOf(AccessDeniedException.class);

        String badSubjectJwt = Jwts.builder().subject("not-a-uuid-abc").claim("role", "COMPRADOR")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> jwtTokenProvider.validateAndExtractUser(badSubjectJwt)).isInstanceOf(JwtException.class);
    }

    private String uploadAndCaptureRegisteredIp(String remoteAddr, String forwardedFor, String realIp) throws Exception {
        org.mockito.Mockito.reset(biometricConsentService);
        when(biometricConsentService.uploadPhoto(any(), anyBoolean(), any(), any(), any())).thenReturn(samplePhoto());
        MockMultipartFile file = new MockMultipartFile("photo", "p.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2});
        var request = multipart("/api/v1/try-on/photos")
                .file(file)
                .param("userId", USER_UUID.toString())
                .param("biometricConsent", "true")
                .with(r -> {
                    r.setRemoteAddr(remoteAddr);
                    return r;
                })
                .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_UUID, "COMPRADOR"));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        if (realIp != null) {
            request.header("X-Real-IP", realIp);
        }
        mockMvc.perform(request).andExpect(status().isCreated());
        org.mockito.ArgumentCaptor<String> ipCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(biometricConsentService)
                .uploadPhoto(any(), anyBoolean(), any(), any(), ipCaptor.capture());
        return ipCaptor.getValue();
    }

    @Test
    @DisplayName("Seguridad IP: cabeceras X-Forwarded-For / X-Real-IP solo se aceptan desde proxies confiables y se validan como IP literal")
    void shouldOnlyTrustForwardedHeadersFromTrustedProxies() throws Exception {
        // Cliente directo (no proxy) falsifica cabeceras -> se ignoran y se usa la IP de conexión
        assertThat(uploadAndCaptureRegisteredIp("198.51.100.20", "1.2.3.4", "5.6.7.8")).isEqualTo("198.51.100.20");

        // Proxy confiable (loopback): primer hop no confiable desde la derecha
        assertThat(uploadAndCaptureRegisteredIp("127.0.0.1", "203.0.113.10", null)).isEqualTo("203.0.113.10");

        // Cliente intenta anteponer una IP falsa; se toma el hop más a la derecha no confiable
        assertThat(uploadAndCaptureRegisteredIp("127.0.0.1", "6.6.6.6, 203.0.113.10, 127.0.0.1", null))
                .isEqualTo("203.0.113.10");

        // Hop inválido (texto/hostname/inyección) -> se descarta X-Forwarded-For y se usa X-Real-IP válida
        assertThat(uploadAndCaptureRegisteredIp("127.0.0.1", "evil.example.com", "198.51.100.77")).isEqualTo("198.51.100.77");
        assertThat(uploadAndCaptureRegisteredIp("127.0.0.1", "999.1.1.1", "198.51.100.78")).isEqualTo("198.51.100.78");

        // X-Real-IP inválida o ausente -> IP de conexión
        assertThat(uploadAndCaptureRegisteredIp("127.0.0.1", null, "not-an-ip")).isEqualTo("127.0.0.1");
        assertThat(uploadAndCaptureRegisteredIp("127.0.0.1", "   ", null)).isEqualTo("127.0.0.1");

        // IPv6 válida desde proxy confiable
        assertThat(uploadAndCaptureRegisteredIp("::1", "2001:db8::1", null)).isEqualTo("2001:db8::1");
    }

    @Test
    @DisplayName("Seguridad IP: proxies confiables configurables por CIDR")
    void shouldHonorConfiguredTrustedProxyCidrRanges() throws Exception {
        BiometricConsentController controller = new BiometricConsentController(
                biometricConsentService, new VirtualTryOnDtoMapper(), List.of("10.0.0.0/8", "invalido/xx", "fd00::/8", "  ")
        );
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(USER_UUID, "ADMIN_STYLERADAR"), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN_STYLERADAR")))
        );
        when(biometricConsentService.uploadPhoto(any(), anyBoolean(), any(), any(), any())).thenReturn(samplePhoto());
        org.mockito.ArgumentCaptor<String> ipCaptor = org.mockito.ArgumentCaptor.forClass(String.class);

        String[][] cases = {
                {"10.20.30.40", "203.0.113.5", "203.0.113.5"},   // dentro del CIDR -> confía
                {"11.0.0.1", "203.0.113.5", "11.0.0.1"},         // fuera del CIDR -> ignora cabecera
                {"fd00::5", "2001:db8::9", "2001:db8::9"},       // IPv6 en CIDR
                {"10.0.0.1", "10.9.9.9", "10.0.0.1"}             // todos los hops son proxies -> conexión directa
        };
        for (String[] c : cases) {
            org.springframework.mock.web.MockHttpServletRequest req = new org.springframework.mock.web.MockHttpServletRequest();
            req.setRemoteAddr(c[0]);
            req.addHeader("X-Forwarded-For", c[1]);
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(req));
            controller.uploadPhoto(USER_UUID, true, new MockMultipartFile("photo", "p.png", "image/png", new byte[]{1}));
        }
        org.mockito.Mockito.verify(biometricConsentService, org.mockito.Mockito.times(cases.length))
                .uploadPhoto(any(), anyBoolean(), any(), any(), ipCaptor.capture());
        for (int i = 0; i < cases.length; i++) {
            assertThat(ipCaptor.getAllValues().get(i)).as("caso %d", i).isEqualTo(cases[i][2]);
        }
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }
}
