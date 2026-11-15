package edu.dosw.styleradar.virtualtryon.application.mapper.in;

import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricConsentResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricPhotoResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.GarmentValidationResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.TryOnJobResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.UserQuotaResponse;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualTryOnDtoMapperTest {

    @Mock
    private BlobStoragePort blobStoragePort;

    @Test
    @DisplayName("GET /jobs/{id}: toJobResponse genera un SAS temporal fresco cuando existe BlobStoragePort")
    void shouldGenerateFreshSasSignedUrlWhenResultImageUrlIsPresentAndBlobStoragePortConfigured() {
        VirtualTryOnDtoMapper mapper = new VirtualTryOnDtoMapper(blobStoragePort);
        UUID jobId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        String canonicalResult = "https://storage.blob.core.windows.net/tryon-results/user/render-1.png";
        String freshSas = canonicalResult + "?se=2026-11-09T20%3A00%3A00Z&sp=r&spr=https&sv=2024-11-04&sr=b&sig=freshSignature";

        TryOnJob completedJob = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(userId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOP)
                .withProvider(AiProvider.FASHN_AI)
                .build();
        completedJob.startAiProcessing();
        completedJob.complete(SasSignedUrl.of(canonicalResult), 1200L);

        String freshPhotoSas = "https://storage/photo.png?sig=freshPhotoSig";
        when(blobStoragePort.generateReadOnlySignedUrl("https://storage/photo.png"))
                .thenReturn(SasSignedUrl.of(freshPhotoSas));
        when(blobStoragePort.generateReadOnlySignedUrl(canonicalResult))
                .thenReturn(SasSignedUrl.of(freshSas));

        TryOnJobResponse response = mapper.toJobResponse(completedJob);

        assertThat(response).isNotNull();
        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.status()).isEqualTo(JobStatus.COMPLETED.name());
        assertThat(response.photoUrl()).isEqualTo(freshPhotoSas);
        assertThat(response.resultImageUrl()).isEqualTo(freshSas);
        assertThat(response.resultImageUrl()).contains("sig=freshSignature");
        assertThat(response.executionDurationMs()).isEqualTo(1200L);
        assertThat(response.completedAt()).isNotNull();
        verify(blobStoragePort).generateReadOnlySignedUrl("https://storage/photo.png");
        verify(blobStoragePort).generateReadOnlySignedUrl(canonicalResult);
    }

    @Test
    @DisplayName("toJobResponse omite URLs sin firmar si blobStoragePort es nulo y retorna null si aún no hay resultado")
    void shouldOmitUnsignedUrlsWhenBlobStoragePortIsNullAndReturnNullWhenNoResult() {
        VirtualTryOnDtoMapper mapper = new VirtualTryOnDtoMapper(null);
        UUID jobId = UUID.randomUUID();
        String canonicalResult = "https://storage/result.png";

        TryOnJob completedJob = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOP)
                .withProvider(AiProvider.MOCK_LOCAL)
                .build();
        completedJob.complete(SasSignedUrl.of(canonicalResult), 800L);

        TryOnJobResponse completedResponse = mapper.toJobResponse(completedJob);
        assertThat(completedResponse.photoUrl()).isNull();
        assertThat(completedResponse.resultImageUrl()).isNull();

        TryOnJob queuedJob = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOP)
                .withProvider(AiProvider.MOCK_LOCAL)
                .build();

        TryOnJobResponse queuedResponse = mapper.toJobResponse(queuedJob);
        assertThat(queuedResponse.photoUrl()).isNull();
        assertThat(queuedResponse.resultImageUrl()).isNull();
    }

    @Test
    @DisplayName("toConsentResponse mapea correctamente todos los atributos del consentimiento")
    void shouldMapConsentResponseCorrectly() {
        VirtualTryOnDtoMapper mapper = new VirtualTryOnDtoMapper();
        UUID consentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        BiometricConsent consent = new BiometricConsent(
                consentId,
                UserId.of(userId),
                "v1.0",
                true,
                List.of("biometrics", "tryon"),
                now,
                null,
                "192.168.1.1"
        );

        BiometricConsentResponse response = mapper.toConsentResponse(consent);
        assertThat(response.consentId()).isEqualTo(consentId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.termsVersion()).isEqualTo("v1.0");
        assertThat(response.accepted()).isTrue();
        assertThat(response.validForInference()).isTrue();
        assertThat(response.purposes()).containsExactly("biometrics", "tryon");
        assertThat(response.consentedAt()).isEqualTo(now);
        assertThat(response.revokedAt()).isNull();
    }

    @Test
    @DisplayName("toPhotoResponse, toQuotaResponse y toGarmentValidationResponse mapean adecuadamente sus respectivos modelos")
    void shouldMapOtherResponsesCorrectly() {
        VirtualTryOnDtoMapper mapper = new VirtualTryOnDtoMapper();
        UUID photoId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        BiometricPhoto photo = new BiometricPhoto(
                photoId,
                UserId.of(userId),
                "image/png",
                1080,
                1920,
                204800L,
                "user/silhouette.png",
                SasSignedUrl.of("https://storage/user/silhouette.png?sig=abc"),
                now
        );
        BiometricPhotoResponse photoResponse = mapper.toPhotoResponse(photo);
        assertThat(photoResponse.photoId()).isEqualTo(photoId);
        assertThat(photoResponse.width()).isEqualTo(1080);
        assertThat(photoResponse.height()).isEqualTo(1920);

        UserQuota quota = new UserQuota(
                UUID.randomUUID(),
                UserId.of(userId),
                10,
                3,
                BillingPeriod.of("2026-11"),
                true,
                now
        );
        UserQuotaResponse quotaResponse = mapper.toQuotaResponse(quota);
        assertThat(quotaResponse.userId()).isEqualTo(userId);
        assertThat(quotaResponse.monthlyLimit()).isEqualTo(10);
        assertThat(quotaResponse.usedCount()).isEqualTo(3);
        assertThat(quotaResponse.remainingCount()).isEqualTo(7);

        UUID garmentId = UUID.randomUUID();
        CatalogGarment garment = new CatalogGarment(
                GarmentId.of(garmentId),
                "Camisa Lino",
                GarmentCategory.TOP,
                "https://cat.local/shirt.png",
                true,
                now
        );
        GarmentValidationResponse garmentResponse = mapper.toGarmentValidationResponse(garment);
        assertThat(garmentResponse.garmentId()).isEqualTo(garmentId);
        assertThat(garmentResponse.category()).isEqualTo("TOP");
        assertThat(garmentResponse.compatible()).isTrue();
        assertThat(garmentResponse.visible()).isTrue();
        assertThat(garmentResponse.zone()).isEqualTo("UPPER_BODY");
        assertThat(garmentResponse.eligibleForTryOn()).isTrue();
        assertThat(garmentResponse.replacedGarmentIds()).isEmpty();
    }

    @Test
    @DisplayName("toJobResponse calcula estimatedDurationSeconds en QUEUED y PROCESSING, y retorna null en COMPLETED y FAILED")
    void shouldCalculateEstimatedCompletionAtForActiveJobs() {
        VirtualTryOnDtoMapper mapper = new VirtualTryOnDtoMapper(null, java.time.Duration.ofSeconds(25));

        TryOnJob queuedJob = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOP)
                .withProvider(AiProvider.FASHN_AI)
                .build();

        TryOnJobResponse queuedResponse = mapper.toJobResponse(queuedJob);
        assertThat(queuedResponse.estimatedDurationSeconds()).isEqualTo(25);
        assertThat(queuedResponse.photoUrl()).isNull();
        assertThat(queuedResponse.completedAt()).isNull();

        queuedJob.startAiProcessing();
        TryOnJobResponse processingResponse = mapper.toJobResponse(queuedJob);
        assertThat(processingResponse.estimatedDurationSeconds()).isEqualTo(25);

        queuedJob.complete(SasSignedUrl.of("https://storage/result.png"), 1000L);
        TryOnJobResponse completedResponse = mapper.toJobResponse(queuedJob);
        assertThat(completedResponse.estimatedDurationSeconds()).isNull();
        assertThat(completedResponse.executionDurationMs()).isEqualTo(1000L);
        assertThat(completedResponse.completedAt()).isNotNull();

        TryOnJob failedJob = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOP)
                .withProvider(AiProvider.FASHN_AI)
                .build();
        failedJob.markFailed("Error de inferencia");
        TryOnJobResponse failedResponse = mapper.toJobResponse(failedJob);
        assertThat(failedResponse.estimatedDurationSeconds()).isNull();
    }

    @Test
    @DisplayName("toJobResponse omite URLs sin firmar cuando generateReadOnlySignedUrl retorna null o URL sin firma SAS válida")
    void shouldOmitUnsignedUrlsWhenSasGenerationReturnsNullOrUnsigned() {
        VirtualTryOnDtoMapper mapper = new VirtualTryOnDtoMapper(blobStoragePort);

        TryOnJob completedJob = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOP)
                .withProvider(AiProvider.FASHN_AI)
                .build();
        completedJob.complete(SasSignedUrl.of("https://storage/result.png"), 900L);

        when(blobStoragePort.generateReadOnlySignedUrl("https://storage/photo.png")).thenReturn(null);
        when(blobStoragePort.generateReadOnlySignedUrl("https://storage/result.png"))
                .thenReturn(SasSignedUrl.of("https://storage/result.png?sig="));

        TryOnJobResponse response = mapper.toJobResponse(completedJob);
        assertThat(response.photoUrl()).isNull();
        assertThat(response.resultImageUrl()).isNull();

        // Acepta parámetros SAS válidos (?sp=r&sr=b y ?sas=...) y rechaza ?foo=bar
        when(blobStoragePort.generateReadOnlySignedUrl("https://storage/photo.png"))
                .thenReturn(SasSignedUrl.of("https://storage/photo.png?sp=r&sr=b"));
        when(blobStoragePort.generateReadOnlySignedUrl("https://storage/result.png"))
                .thenReturn(SasSignedUrl.of("https://storage/result.png?foo=bar"));

        TryOnJobResponse partialResponse = mapper.toJobResponse(completedJob);
        assertThat(partialResponse.photoUrl()).isEqualTo("https://storage/photo.png?sp=r&sr=b");
        assertThat(partialResponse.resultImageUrl()).isNull();

        when(blobStoragePort.generateReadOnlySignedUrl("https://storage/result.png"))
                .thenReturn(SasSignedUrl.of("https://storage/result.png?sas=tok123"));
        assertThat(mapper.toJobResponse(completedJob).resultImageUrl())
                .isEqualTo("https://storage/result.png?sas=tok123");
    }

    @Test
    @DisplayName("toJobResponse maneja de forma controlada excepciones al generar firma SAS sin exponer referencias de almacenamiento")
    void shouldHandleExceptionDuringSasGenerationWithoutLeakingStorageReferences() {
        VirtualTryOnDtoMapper mapper = new VirtualTryOnDtoMapper(blobStoragePort);

        TryOnJob completedJob = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOP)
                .withProvider(AiProvider.FASHN_AI)
                .build();
        completedJob.complete(SasSignedUrl.of("https://storage/result.png"), 900L);

        when(blobStoragePort.generateReadOnlySignedUrl("https://storage/photo.png"))
                .thenThrow(new IllegalStateException("Azure SAS key unavailable"));
        when(blobStoragePort.generateReadOnlySignedUrl("https://storage/result.png"))
                .thenReturn(SasSignedUrl.of("https://storage/result.png?sig=signedOk"));

        TryOnJobResponse response = mapper.toJobResponse(completedJob);
        assertThat(response.photoUrl()).isNull();
        assertThat(response.resultImageUrl()).isEqualTo("https://storage/result.png?sig=signedOk");

        completedJob.purgeBiometricPhoto();
        TryOnJobResponse purgedResponse = mapper.toJobResponse(completedJob);
        assertThat(purgedResponse.photoUrl()).isNull();
    }

    @Test
    @DisplayName("toPhotoResponse firma referencias biométricas sin SAS y omite la URL si falla la firma")
    void shouldSignOrOmitUnsignedBiometricPhotoUrlSafely() {
        UUID photoId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        Instant now = Instant.now();

        BiometricPhoto unsignedPhoto = new BiometricPhoto(
                photoId,
                userId,
                "image/png",
                1080,
                1920,
                204800L,
                "user/silhouette.png",
                SasSignedUrl.of("https://blob.storage.local/biometric-silhouettes/user/silhouette.png"),
                now
        );

        VirtualTryOnDtoMapper mapperWithoutPort = new VirtualTryOnDtoMapper(null);
        assertThat(mapperWithoutPort.toPhotoResponse(unsignedPhoto).photoUrl()).isNull();

        VirtualTryOnDtoMapper mapperWithPort = new VirtualTryOnDtoMapper(blobStoragePort);
        when(blobStoragePort.generateReadOnlySignedUrl("user/silhouette.png"))
                .thenReturn(SasSignedUrl.of("https://storage/user/silhouette.png?sig=bioSas"));
        assertThat(mapperWithPort.toPhotoResponse(unsignedPhoto).photoUrl())
                .isEqualTo("https://storage/user/silhouette.png?sig=bioSas");

        when(blobStoragePort.generateReadOnlySignedUrl("user/silhouette.png"))
                .thenReturn(SasSignedUrl.of("https://storage/user/silhouette.png"));
        assertThat(mapperWithPort.toPhotoResponse(unsignedPhoto).photoUrl()).isNull();
        verify(blobStoragePort, never()).generateReadOnlySignedUrl("https://blob.storage.local/biometric-silhouettes/user/silhouette.png");
    }
}

