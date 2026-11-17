package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dosw.styleradar.virtualtryon.application.dto.request.BiometricConsentRequest;
import edu.dosw.styleradar.virtualtryon.application.dto.request.StartTryOnRequest;
import edu.dosw.styleradar.virtualtryon.application.mapper.in.VirtualTryOnDtoMapper;
import edu.dosw.styleradar.virtualtryon.application.service.BiometricConsentService;
import edu.dosw.styleradar.virtualtryon.application.service.CreateTryOnJobService;
import edu.dosw.styleradar.virtualtryon.application.service.DeleteBiometricDataService;
import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.JwtAuthenticationFilter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.JwtTokenProvider;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.SecurityProblemHandler;
import edu.dosw.styleradar.virtualtryon.infrastructure.config.SecurityConfig;
import edu.dosw.styleradar.virtualtryon.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas HTTP de integración para validar la interoperabilidad con identificadores numéricos
 * reales emitidos por ms-auth en todo el ciclo de vida REST: creación de trabajos, consentimiento,
 * fotografías, historial y cuotas, preservando la validación estricta de titularidad (Ley 1581 · SR-RF-23).
 */
@WebMvcTest(controllers = {VirtualTryOnRestController.class, BiometricConsentController.class})
@Import({
        VirtualTryOnDtoMapper.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtTokenProvider.class,
        SecurityProblemHandler.class
})
@ActiveProfiles("test")
class AuthNumericIdHttpIntegrationTest {

    private static final String AUTH_NUMERIC_ID = "45678";
    private static final UUID EXPECTED_CANONICAL_UUID = UserId.parseToUuid(AUTH_NUMERIC_ID);
    private static final String OTHER_NUMERIC_ID = "99999";
    private static final UUID OTHER_CANONICAL_UUID = UserId.parseToUuid(OTHER_NUMERIC_ID);

    private static final UUID SAMPLE_GARMENT_UUID = UUID.fromString("f8e7d6c5-b4a3-2109-8765-43210fedcba9");
    private static final String SAMPLE_PHOTO_URL = "https://styleradarblob.blob.core.windows.net/photos/sample.jpg";

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

    @MockBean
    private BiometricConsentService biometricConsentService;

    @MockBean
    private BlobStoragePort blobStoragePort;

    @BeforeEach
    void setUp() {
        lenient().when(blobStoragePort.generateReadOnlySignedUrl(anyString())).thenAnswer(inv -> {
            String raw = inv.getArgument(0);
            return raw == null ? null : SasSignedUrl.of(raw + "?sig=mock-sas-token");
        });
    }

    private String tokenFor(String subject, String role) {
        return "Bearer " + jwtTokenProvider.generateTokenForSubject(subject, role);
    }

    private TryOnJob sampleJob(UUID userId) {
        return new TryOnJobBuilder()
                .withUserId(UserId.of(userId))
                .withGarmentId(GarmentId.of(SAMPLE_GARMENT_UUID))
                .withCategory(GarmentCategory.TOP)
                .withPhotoUrl(SAMPLE_PHOTO_URL)
                .build();
    }

    private BiometricPhoto samplePhoto(UUID userId) {
        return new BiometricPhoto(
                UUID.randomUUID(),
                UserId.of(userId),
                "image/jpeg",
                800,
                600,
                1024L,
                "silhouette.jpg",
                SasSignedUrl.of("https://styleradarblob.blob.core.windows.net/photos/silhouette.jpg?sig=test"),
                Instant.now()
        );
    }

    @Test
    @DisplayName("1. POST /api/v1/try-on/jobs sin userId en body: Debe inferir el ID numérico del JWT y responder 202 Accepted")
    void shouldCreateJobInferringUserIdFromAuthNumericJwt() throws Exception {
        TryOnJob mockJob = sampleJob(EXPECTED_CANONICAL_UUID);
        when(createTryOnJobService.createTryOnJob(
                eq(UserId.of(EXPECTED_CANONICAL_UUID)),
                eq(GarmentId.of(SAMPLE_GARMENT_UUID)),
                eq(SAMPLE_PHOTO_URL),
                eq(true)
        )).thenReturn(mockJob);

        String jsonPayload = """
                {
                    "garmentId": "%s",
                    "photoUrl": "%s",
                    "biometricConsent": true
                }
                """.formatted(SAMPLE_GARMENT_UUID, SAMPLE_PHOTO_URL);

        mockMvc.perform(post("/api/v1/try-on/jobs")
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.userId").value(EXPECTED_CANONICAL_UUID.toString()));

        verify(createTryOnJobService).createTryOnJob(
                eq(UserId.of(EXPECTED_CANONICAL_UUID)),
                eq(GarmentId.of(SAMPLE_GARMENT_UUID)),
                eq(SAMPLE_PHOTO_URL),
                eq(true)
        );
    }

    @Test
    @DisplayName("2. POST /api/v1/try-on/jobs con userId numérico explícito: Debe normalizarlo y responder 202 Accepted si coincide con el JWT")
    void shouldCreateJobWithExplicitNumericUserIdMatchingJwt() throws Exception {
        TryOnJob mockJob = sampleJob(EXPECTED_CANONICAL_UUID);
        when(createTryOnJobService.createTryOnJob(any(), any(), any(), anyBoolean())).thenReturn(mockJob);

        String jsonPayload = """
                {
                    "userId": 45678,
                    "garmentId": "%s",
                    "photoUrl": "%s",
                    "biometricConsent": true
                }
                """.formatted(SAMPLE_GARMENT_UUID, SAMPLE_PHOTO_URL);

        mockMvc.perform(post("/api/v1/try-on/jobs")
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.userId").value(EXPECTED_CANONICAL_UUID.toString()));
    }

    @Test
    @DisplayName("3. POST /api/v1/try-on/jobs con userId de otro usuario: Debe rechazar con 403 Forbidden por violación de titularidad")
    void shouldRejectJobCreationWhenNumericUserIdDoesNotMatchCaller() throws Exception {
        String jsonPayload = """
                {
                    "userId": 99999,
                    "garmentId": "%s",
                    "photoUrl": "%s",
                    "biometricConsent": true
                }
                """.formatted(SAMPLE_GARMENT_UUID, SAMPLE_PHOTO_URL);

        mockMvc.perform(post("/api/v1/try-on/jobs")
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. POST /api/v1/try-on/jobs con userId de otro usuario pero rol ADMIN_STYLERADAR: Debe permitirlo con 202 Accepted")
    void shouldAllowJobCreationForOtherUserWhenCallerIsAdmin() throws Exception {
        TryOnJob mockJob = sampleJob(OTHER_CANONICAL_UUID);
        when(createTryOnJobService.createTryOnJob(
                eq(UserId.of(OTHER_CANONICAL_UUID)),
                eq(GarmentId.of(SAMPLE_GARMENT_UUID)),
                anyString(),
                anyBoolean()
        )).thenReturn(mockJob);

        String jsonPayload = """
                {
                    "userId": 99999,
                    "garmentId": "%s",
                    "photoUrl": "%s",
                    "biometricConsent": true
                }
                """.formatted(SAMPLE_GARMENT_UUID, SAMPLE_PHOTO_URL);

        mockMvc.perform(post("/api/v1/try-on/jobs")
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "ADMIN_STYLERADAR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("5. Consentimiento biométrico: POST, GET y DELETE con ID numérico de Auth en ruta y body")
    void shouldManageConsentWithNumericAuthIds() throws Exception {
        BiometricConsent mockConsent = new BiometricConsent(
                UUID.randomUUID(),
                UserId.of(EXPECTED_CANONICAL_UUID),
                "v1.0-LEY1581-2026",
                true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"),
                Instant.now(),
                null,
                "190.24.112.45"
        );
        when(biometricConsentService.grantConsent(any(), any(), anyBoolean(), any(), any())).thenReturn(mockConsent);
        when(biometricConsentService.getConsentByUserId(eq(UserId.of(EXPECTED_CANONICAL_UUID)))).thenReturn(mockConsent);
        when(biometricConsentService.revokeConsent(eq(UserId.of(EXPECTED_CANONICAL_UUID)))).thenReturn(mockConsent);

        // POST /consents con userId numérico
        String consentPayload = """
                {
                    "userId": 45678,
                    "termsVersion": "v1.0-LEY1581-2026",
                    "accepted": true,
                    "purposes": ["VIRTUAL_TRY_ON_INFERENCE"]
                }
                """;
        mockMvc.perform(post("/api/v1/try-on/consents")
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consentPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(EXPECTED_CANONICAL_UUID.toString()));

        // GET /consents/45678 (titular propio) -> 200 OK
        mockMvc.perform(get("/api/v1/try-on/consents/{userId}", AUTH_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(EXPECTED_CANONICAL_UUID.toString()));

        // GET /consents/99999 (otro usuario) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/try-on/consents/{userId}", OTHER_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isForbidden());

        // DELETE /consents/45678 -> 200 OK
        mockMvc.perform(delete("/api/v1/try-on/consents/{userId}", AUTH_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("6. Fotografías biométricas: POST y GET con ID numérico y validación de titularidad")
    void shouldManagePhotosWithNumericAuthIds() throws Exception {
        BiometricPhoto mockPhoto = samplePhoto(EXPECTED_CANONICAL_UUID);
        when(biometricConsentService.uploadPhoto(eq(UserId.of(EXPECTED_CANONICAL_UUID)), anyBoolean(), any(), any(), any()))
                .thenReturn(mockPhoto);
        when(biometricConsentService.getPhotoByUserId(eq(UserId.of(EXPECTED_CANONICAL_UUID))))
                .thenReturn(mockPhoto);

        MockMultipartFile file = new MockMultipartFile(
                "photo", "front.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        // POST /photos con userId numérico propio -> 201 Created
        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(file)
                        .param("userId", AUTH_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(EXPECTED_CANONICAL_UUID.toString()));

        // POST /photos con userId de otro usuario -> 403 Forbidden
        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(file)
                        .param("userId", OTHER_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isForbidden());

        // GET /photos/45678 -> 200 OK
        mockMvc.perform(get("/api/v1/try-on/photos/{userId}", AUTH_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(EXPECTED_CANONICAL_UUID.toString()));

        // GET /photos/99999 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/try-on/photos/{userId}", OTHER_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7. Historial y Cuotas: GET /jobs y GET /quotas con ID numérico de Auth en query params")
    void shouldQueryJobsHistoryAndQuotasWithNumericAuthIds() throws Exception {
        when(queryTryOnJobsService.getJobHistory(eq(UserId.of(EXPECTED_CANONICAL_UUID))))
                .thenReturn(List.of());

        UserQuota quota = UserQuota.createDefault(UserId.of(EXPECTED_CANONICAL_UUID), BillingPeriod.current());
        when(queryTryOnJobsService.getUserQuota(eq(UserId.of(EXPECTED_CANONICAL_UUID))))
                .thenReturn(quota);

        // GET /jobs?userId=45678 -> 200 OK
        mockMvc.perform(get("/api/v1/try-on/jobs")
                        .param("userId", AUTH_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isOk());

        // GET /jobs (sin parámetro userId) -> 200 OK
        mockMvc.perform(get("/api/v1/try-on/jobs")
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isOk());

        // GET /jobs?userId=99999 (otro usuario) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/try-on/jobs")
                        .param("userId", OTHER_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isForbidden());

        // GET /quotas?userId=45678 -> 200 OK
        mockMvc.perform(get("/api/v1/try-on/quotas")
                        .param("userId", AUTH_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(EXPECTED_CANONICAL_UUID.toString()));

        // GET /quotas (sin parámetro userId) -> 200 OK
        mockMvc.perform(get("/api/v1/try-on/quotas")
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(EXPECTED_CANONICAL_UUID.toString()));

        // GET /quotas?userId=99999 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/try-on/quotas")
                        .param("userId", OTHER_NUMERIC_ID)
                        .header("Authorization", tokenFor(AUTH_NUMERIC_ID, "COMPRADOR")))
                .andExpect(status().isForbidden());
    }
}
