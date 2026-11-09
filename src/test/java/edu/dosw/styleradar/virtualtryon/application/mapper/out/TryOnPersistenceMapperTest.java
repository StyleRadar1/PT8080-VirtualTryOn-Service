package edu.dosw.styleradar.virtualtryon.application.mapper.out;

import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnOutfit;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.JobAuditLogJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnJobJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnOutfitJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TryOnPersistenceMapperTest {

    private final TryOnPersistenceMapper mapper = new TryOnPersistenceMapper();

    @Test
    @DisplayName("Debe mapear consentimiento biométrico bidireccionalmente incluyendo propósitos nulos, vacíos y poblados")
    void shouldMapConsentBidirectionally() {
        UUID consentId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        Instant now = Instant.now();

        BiometricConsent domain = new BiometricConsent(
                consentId,
                userId,
                "v1.0",
                true,
                List.of("biometrics", "tryon"),
                now,
                null,
                "127.0.0.1"
        );

        BiometricConsentJpaEntity entity = mapper.toConsentJpaEntity(domain);
        assertThat(entity.getPurposes()).isEqualTo("biometrics,tryon");
        assertThat(entity.getUserId()).isEqualTo(userId.value());

        BiometricConsent fromEntity = mapper.toConsentDomain(entity);
        assertThat(fromEntity.getPurposes()).containsExactly("biometrics", "tryon");

        // Casos de propósitos nulos y vacíos
        BiometricConsentJpaEntity nullPurposesEntity = new BiometricConsentJpaEntity(
                consentId, userId.value(), "v1.0", true, null, now, null, "127.0.0.1"
        );
        assertThat(mapper.toConsentDomain(nullPurposesEntity).getPurposes()).isEmpty();

        BiometricConsentJpaEntity blankPurposesEntity = new BiometricConsentJpaEntity(
                consentId, userId.value(), "v1.0", true, "   ", now, null, "127.0.0.1"
        );
        assertThat(mapper.toConsentDomain(blankPurposesEntity).getPurposes()).isEmpty();
    }

    @Test
    @DisplayName("Debe mapear fotografía biométrica bidireccionalmente normalizando el blobPath")
    void shouldMapPhotoBidirectionally() {
        UUID photoId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        Instant now = Instant.now();

        BiometricPhoto domain = new BiometricPhoto(
                photoId,
                userId,
                "image/png",
                400,
                600,
                1024L,
                userId.value() + "/photo.png",
                SasSignedUrl.of("https://blob.storage.local/biometric-silhouettes/" + userId.value() + "/photo.png"),
                now
        );

        BiometricPhotoJpaEntity entity = mapper.toPhotoJpaEntity(domain);
        assertThat(entity.getPhotoUrl()).isEqualTo(userId.value() + "/photo.png");

        BiometricPhoto fromEntity = mapper.toPhotoDomain(entity);
        assertThat(fromEntity.getBlobPath()).isEqualTo(userId.value() + "/photo.png");
        assertThat(fromEntity.getPhotoUrl().value()).contains(userId.value().toString());
    }

    @Test
    @DisplayName("Debe mapear cuota de usuario bidireccionalmente")
    void shouldMapQuotaBidirectionally() {
        UUID quotaId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        Instant now = Instant.now();

        UserQuota domain = new UserQuota(
                quotaId,
                userId,
                10,
                2,
                BillingPeriod.of("2026-11"),
                true,
                now
        );

        UserQuotaJpaEntity entity = mapper.toQuotaJpaEntity(domain);
        assertThat(entity.getBillingPeriod()).isEqualTo("2026-11");
        assertThat(entity.isRadarPlus()).isTrue();

        UserQuota fromEntity = mapper.toQuotaDomain(entity);
        assertThat(fromEntity.getUsedCount()).isEqualTo(2);
        assertThat(fromEntity.getMonthlyLimit()).isEqualTo(10);
    }

    @Test
    @DisplayName("Debe mapear catálogo de prendas bidireccionalmente")
    void shouldMapCatalogGarmentBidirectionally() {
        UUID garmentId = UUID.randomUUID();
        Instant now = Instant.now();

        CatalogGarment domain = new CatalogGarment(
                GarmentId.of(garmentId),
                "Chaqueta Denim",
                GarmentCategory.TOPS,
                "https://cat.local/jacket.png",
                true,
                now
        );

        CatalogGarmentJpaEntity entity = mapper.toCatalogGarmentJpaEntity(domain);
        assertThat(entity.getCategory()).isEqualTo("TOPS");

        CatalogGarment fromEntity = mapper.toCatalogGarmentDomain(entity);
        assertThat(fromEntity.getCategory()).isEqualTo(GarmentCategory.TOPS);
    }

    @Test
    @DisplayName("Debe mapear TryOnJob bidireccionalmente manejando valores nulos y no nulos de resultado, categoría y proveedor")
    void shouldMapTryOnJobBidirectionally() {
        UUID jobId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        Instant now = Instant.now();

        // Con todos los campos poblados
        TryOnJob domainWithResult = new TryOnJob(
                jobId,
                userId,
                garmentId,
                "https://blob/photo.png",
                GarmentCategory.BOTTOMS,
                "https://blob/pants.png",
                JobStatus.COMPLETED,
                SasSignedUrl.of("https://blob/result.png"),
                AiProvider.MOCK_LOCAL,
                1200L,
                null,
                false,
                now,
                now
        );

        TryOnJobJpaEntity entityWithResult = mapper.toJobJpaEntity(domainWithResult);
        assertThat(entityWithResult.getResultImageUrl()).isEqualTo("https://blob/result.png");
        assertThat(entityWithResult.getAiProvider()).isEqualTo("MOCK_LOCAL");

        TryOnJob fromEntity = mapper.toJobDomain(entityWithResult);
        assertThat(fromEntity.getResultImageUrl()).isNotNull();
        assertThat(fromEntity.getResultImageUrl().value()).isEqualTo("https://blob/result.png");
        assertThat(fromEntity.getGarmentCategory()).isEqualTo(GarmentCategory.BOTTOMS);
        assertThat(fromEntity.getAiProvider()).isEqualTo(AiProvider.MOCK_LOCAL);

        // Con campos nulos o vacíos para cubrir ramas de fallback
        TryOnJob domainNullResult = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(userId)
                .withGarmentId(garmentId)
                .withPhotoUrl("https://blob/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withProvider(AiProvider.FASHN_AI)
                .build();

        TryOnJobJpaEntity entityNullResult = mapper.toJobJpaEntity(domainNullResult);
        assertThat(entityNullResult.getResultImageUrl()).isNull();

        TryOnJobJpaEntity fallbackEntity = new TryOnJobJpaEntity(
                jobId,
                userId.value(),
                garmentId.value(),
                "https://blob/photo.png",
                null, // category null -> defaults to TOPS
                null,
                "QUEUED",
                "   ", // blank result url -> null
                null, // aiProvider null -> defaults to FASHN_AI
                null,
                null,
                false,
                now,
                now
        );
        TryOnJob fromFallback = mapper.toJobDomain(fallbackEntity);
        assertThat(fromFallback.getResultImageUrl()).isNull();
        assertThat(fromFallback.getGarmentCategory()).isEqualTo(GarmentCategory.TOPS);
        assertThat(fromFallback.getAiProvider()).isEqualTo(AiProvider.FASHN_AI);
    }

    @Test
    @DisplayName("Debe mapear JobAuditLog bidireccionalmente con estado previo nulo y poblado")
    void shouldMapAuditLogBidirectionally() {
        UUID logId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();

        JobAuditLog domainWithPrev = new JobAuditLog(
                logId,
                jobId,
                JobStatus.QUEUED,
                JobStatus.PROCESSING,
                "Inicio",
                now
        );
        JobAuditLogJpaEntity entity = mapper.toAuditLogJpaEntity(domainWithPrev);
        assertThat(entity.getPreviousStatus()).isEqualTo("QUEUED");

        JobAuditLog fromEntity = mapper.toAuditLogDomain(entity);
        assertThat(fromEntity.getPreviousStatus()).isEqualTo(JobStatus.QUEUED);

        // Sin estado previo
        JobAuditLog domainNoPrev = new JobAuditLog(
                logId,
                jobId,
                null,
                JobStatus.QUEUED,
                "Creacion",
                now
        );
        JobAuditLogJpaEntity entityNoPrev = mapper.toAuditLogJpaEntity(domainNoPrev);
        assertThat(entityNoPrev.getPreviousStatus()).isNull();

        JobAuditLogJpaEntity entityBlankPrev = new JobAuditLogJpaEntity(
                logId, jobId, "  ", "QUEUED", "Creacion", now
        );
        assertThat(mapper.toAuditLogDomain(entityBlankPrev).getPreviousStatus()).isNull();
    }

    @Test
    @DisplayName("normalizePersistedBlobPath: Debe normalizar correctamente distintas variantes de URLs y rutas")
    void shouldNormalizePersistedBlobPathVarieties() {
        assertThat(TryOnPersistenceMapper.normalizePersistedBlobPath(null)).isEmpty();
        assertThat(TryOnPersistenceMapper.normalizePersistedBlobPath("   ")).isEmpty();
        assertThat(TryOnPersistenceMapper.normalizePersistedBlobPath("userId/silhouette.jpg")).isEqualTo("userId/silhouette.jpg");
        assertThat(TryOnPersistenceMapper.normalizePersistedBlobPath("biometric-silhouettes/userId/silhouette.jpg"))
                .isEqualTo("userId/silhouette.jpg");
        assertThat(TryOnPersistenceMapper.normalizePersistedBlobPath("/biometric-silhouettes/userId/silhouette.jpg?se=123&sig=abc"))
                .isEqualTo("userId/silhouette.jpg");
        assertThat(TryOnPersistenceMapper.normalizePersistedBlobPath("https://storage.blob.core.windows.net/biometric-silhouettes/userId/photo.jpg?sv=2024"))
                .isEqualTo("userId/photo.jpg");
        assertThat(TryOnPersistenceMapper.normalizePersistedBlobPath("http://custom.storage/container-x/userId/photo.png"))
                .isEqualTo("userId/photo.png");
    }

    @Test
    @DisplayName("toJobJpaEntity: Debe despojar tokens SAS y query strings de photoUrl y resultImageUrl para que no persistan en BD")
    void shouldStripSasQueryStringsFromJobEntityUrls() {
        UUID jobId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        String photoUrlWithSas = "https://storage.blob.core.windows.net/biometric-silhouettes/user/photo.jpg?se=2026-11-09&sp=r&sig=SecretSig1";
        String resultUrlWithSas = "https://storage.blob.core.windows.net/tryon-results/user/render.png?se=2026-11-09&sp=r&sig=SecretSig2";

        TryOnJob job = new TryOnJob(
                jobId,
                userId,
                GarmentId.of(UUID.randomUUID()),
                photoUrlWithSas,
                GarmentCategory.TOPS,
                "https://cat.local/shirt.png",
                JobStatus.COMPLETED,
                SasSignedUrl.of(resultUrlWithSas),
                AiProvider.FASHN_AI,
                1500L,
                null,
                false,
                Instant.now(),
                Instant.now()
        );

        TryOnJobJpaEntity entity = mapper.toJobJpaEntity(job);

        assertThat(entity.getPhotoUrl())
                .isEqualTo("https://storage.blob.core.windows.net/biometric-silhouettes/user/photo.jpg")
                .doesNotContain("?")
                .doesNotContain("sig=")
                .doesNotContain("SecretSig1");

        assertThat(entity.getResultImageUrl())
                .isEqualTo("https://storage.blob.core.windows.net/tryon-results/user/render.png")
                .doesNotContain("?")
                .doesNotContain("sig=")
                .doesNotContain("SecretSig2");
    }

    @Test
    @DisplayName("toOutfitJpaEntity: Debe despojar tokens SAS de finalRenderUrl para almacenar únicamente la referencia permanente en try_on_outfits")
    void shouldStripSasQueryStringsFromOutfitEntityUrls() {
        UUID outfitId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        String renderUrlWithSas = "https://storage.blob.core.windows.net/tryon-results/user/outfit.png?se=2026-11-09&sp=r&sig=SecretSig3";

        TryOnOutfit outfit = new TryOnOutfit(
                outfitId,
                jobId,
                userId,
                SasSignedUrl.of(renderUrlWithSas),
                Instant.now().plusSeconds(900),
                Instant.now()
        );

        TryOnOutfitJpaEntity entity = mapper.toOutfitJpaEntity(outfit);

        assertThat(entity.getFinalRenderUrl())
                .isEqualTo("https://storage.blob.core.windows.net/tryon-results/user/outfit.png")
                .doesNotContain("?")
                .doesNotContain("sig=")
                .doesNotContain("SecretSig3");

        TryOnOutfit roundtrip = mapper.toOutfitDomain(entity);
        assertThat(roundtrip.getFinalRenderUrl().value())
                .isEqualTo("https://storage.blob.core.windows.net/tryon-results/user/outfit.png");
    }
}
