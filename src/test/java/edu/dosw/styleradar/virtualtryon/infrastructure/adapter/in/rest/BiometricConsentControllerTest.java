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
import edu.dosw.styleradar.virtualtryon.infrastructure.exception.GlobalExceptionHandler;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
 * Pruebas de contrato REST para BiometricConsentController (SR-RF-23).
 */
@WebMvcTest(controllers = BiometricConsentController.class)
@Import({VirtualTryOnDtoMapper.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class BiometricConsentControllerTest {

    private static final UUID USER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BiometricConsentService biometricConsentService;

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
                SasSignedUrl.of("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/pose.png"),
                Instant.parse("2026-11-07T22:05:00Z")
        );
    }

    @Test
    @DisplayName("HU SR-RF-23 (POST /api/v1/try-on/consents): Dado un request válido de consentimiento Ley 1581, Cuando se envía, Entonces responde 201 Created")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_UUID.toString()))
                .andExpect(jsonPath("$.validForInference").value(true));
    }

    @Test
    @DisplayName("HU SR-RF-23 (GET y DELETE /api/v1/try-on/consents/{userId}): Dado un usuario con consentimiento, Cuando se consulta o revoca, Entonces responde 200 OK")
    void shouldGetAndRevokeBiometricConsent() throws Exception {
        when(biometricConsentService.getConsentByUserId(eq(UserId.of(USER_UUID))))
                .thenReturn(sampleConsent(null));
        when(biometricConsentService.revokeConsent(eq(UserId.of(USER_UUID))))
                .thenReturn(sampleConsent(Instant.parse("2026-11-07T22:10:00Z")));

        mockMvc.perform(get("/api/v1/try-on/consents/{userId}", USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validForInference").value(true));

        mockMvc.perform(delete("/api/v1/try-on/consents/{userId}", USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validForInference").value(false));
    }

    @Test
    @DisplayName("HU SR-RF-23 (POST /api/v1/try-on/photos): Dado un archivo multipart vertical válido, Cuando se carga, Entonces responde 201 Created con dimensiones y URL firmada")
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
                        .param("biometricConsent", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(400))
                .andExpect(jsonPath("$.height").value(600))
                .andExpect(jsonPath("$.contentType").value("image/png"));

        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .param("userId", USER_UUID.toString()))
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
                        .param("biometricConsent", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CONSENT_REQUIRED"));

        when(biometricConsentService.uploadPhoto(eq(UserId.of(USER_UUID)), eq(true), any(), any()))
                .thenThrow(new InvalidPhotoException("La fotografía debe medir al menos 300x400 píxeles"));

        mockMvc.perform(multipart("/api/v1/try-on/photos")
                        .file(file)
                        .param("userId", USER_UUID.toString())
                        .param("biometricConsent", "true"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PHOTO"));
    }

    @Test
    @DisplayName("HU SR-RF-23 (GET /api/v1/try-on/photos/{userId}): Dado un usuario con o sin fotografía, Cuando se consulta, Entonces responde 200 OK o 404 Not Found")
    void shouldGetBiometricPhotoOrReturnNotFound() throws Exception {
        when(biometricConsentService.getPhotoByUserId(eq(UserId.of(USER_UUID))))
                .thenReturn(samplePhoto())
                .thenThrow(new JobNotFoundException(USER_UUID));

        mockMvc.perform(get("/api/v1/try-on/photos/{userId}", USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_UUID.toString()));

        mockMvc.perform(get("/api/v1/try-on/photos/{userId}", USER_UUID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("HU SR-RF-23 (Manejo IOException en Multipart): Dado un MultipartFile que lanza IOException al leer bytes, Cuando se invoca uploadPhoto, Entonces lanza InvalidPhotoException")
    void shouldHandleIoExceptionWhenReadingMultipartBytes() throws Exception {
        BiometricConsentController controller = new BiometricConsentController(
                biometricConsentService,
                new VirtualTryOnDtoMapper()
        );
        MultipartFile brokenFile = mock(MultipartFile.class);
        when(brokenFile.getBytes()).thenThrow(new IOException("Fallo de lectura"));

        assertThatThrownBy(() -> controller.uploadPhoto(USER_UUID, true, brokenFile))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("No fue posible procesar los bytes");
    }
}

