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
                .withCategory(GarmentCategory.TOPS)
                .withProvider(AiProvider.FASHN_AI)
                .build();
        completedJob.startAiProcessing();
        completedJob.complete(SasSignedUrl.of(canonicalResult), 1200L);

        when(blobStoragePort.generateReadOnlySignedUrl(canonicalResult))
                .thenReturn(SasSignedUrl.of(freshSas));

        TryOnJobResponse response = mapper.toJobResponse(completedJob);

        assertThat(response).isNotNull();
        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.status()).isEqualTo(JobStatus.COMPLETED.name());
        assertThat(response.resultImageUrl()).isEqualTo(freshSas);
        assertThat(response.resultImageUrl()).contains("sig=freshSignature");
        verify(blobStoragePort).generateReadOnlySignedUrl(canonicalResult);
    }

    @Test
    @DisplayName("toJobResponse retorna URL canónica si blobStoragePort es nulo y null si el trabajo aún no tiene resultado")
    void shouldReturnCanonicalUrlWhenBlobStoragePortIsNullAndNullWhenNoResult() {
        VirtualTryOnDtoMapper mapper = new VirtualTryOnDtoMapper(null);
        UUID jobId = UUID.randomUUID();
        String canonicalResult = "https://storage/result.png";

        TryOnJob completedJob = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withProvider(AiProvider.MOCK_LOCAL)
                .build();
        completedJob.complete(SasSignedUrl.of(canonicalResult), 800L);

        TryOnJobResponse completedResponse = mapper.toJobResponse(completedJob);
        assertThat(completedResponse.resultImageUrl()).isEqualTo(canonicalResult);

        TryOnJob queuedJob = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withProvider(AiProvider.MOCK_LOCAL)
                .build();

        TryOnJobResponse queuedResponse = mapper.toJobResponse(queuedJob);
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
                GarmentCategory.TOPS,
                "https://cat.local/shirt.png",
                true,
                now
        );
        GarmentValidationResponse garmentResponse = mapper.toGarmentValidationResponse(garment);
        assertThat(garmentResponse.garmentId()).isEqualTo(garmentId);
        assertThat(garmentResponse.category()).isEqualTo("TOPS");
        assertThat(garmentResponse.eligibleForTryOn()).isTrue();
    }
}

