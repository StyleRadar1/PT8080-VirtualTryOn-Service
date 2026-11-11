package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias para el agregado raíz TryOnJob y la entidad OutboxEventJpaEntity (SR-RF-25).
 */
class TryOnJobModelAndOutboxEntityTest {

    @Test
    @DisplayName("HU SR-RF-25: TryOnJob cubre todas las transiciones de estado, purgas granulares y getters")
    void shouldExerciseTryOnJobStateTransitionsAndMethods() {
        UUID id = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        String photoUrl = "https://blob/photo.png";

        TryOnJob job = new TryOnJobBuilder()
                .withId(id)
                .withUserId(userId)
                .withGarmentId(garmentId)
                .withPhotoUrl(photoUrl)
                .withCategory(GarmentCategory.TOPS)
                .withGarmentImageUrl("https://catalog/shirt.png")
                .withProvider(AiProvider.FASHN_AI)
                .build();

        assertThat(job.getId()).isEqualTo(id);
        assertThat(job.getUserId()).isEqualTo(userId);
        assertThat(job.getGarmentId()).isEqualTo(garmentId);
        assertThat(job.getPhotoUrl()).isEqualTo(photoUrl);
        assertThat(job.getGarmentCategory()).isEqualTo(GarmentCategory.TOPS);
        assertThat(job.getGarmentImageUrl()).isEqualTo("https://catalog/shirt.png");
        assertThat(job.getAiProvider()).isEqualTo(AiProvider.FASHN_AI);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.isPhotoPurged()).isFalse();
        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(job.getUpdatedAt()).isNotNull();

        // startAiProcessing
        job.startAiProcessing();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);

        // complete con tiempo positivo y negativo
        SasSignedUrl renderUrl = SasSignedUrl.of("https://blob/render.png");
        job.complete(renderUrl, 2500L);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getExecutionTimeMs()).isEqualTo(2500L);
        assertThat(job.getResultImageUrl()).isEqualTo(renderUrl);

        job.complete(renderUrl, -10L);
        assertThat(job.getExecutionTimeMs()).isZero();

        job.complete(renderUrl, null);
        assertThat(job.getExecutionTimeMs()).isZero();

        // markFailed con motivo nulo, blanco y con texto
        job.markFailed(null);
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("Error en el procesamiento de inferencia IA");

        job.markFailed("   ");
        assertThat(job.getErrorMessage()).isEqualTo("Error en el procesamiento de inferencia IA");

        job.markFailed(" Error de timeout en modelo ");
        assertThat(job.getErrorMessage()).isEqualTo("Error de timeout en modelo");

        // Purga granular de silueta
        job.purgeRawSilhouettePhoto();
        assertThat(job.isPhotoPurged()).isTrue();
        assertThat(job.getPhotoUrl()).isEqualTo("PURGED_BY_USER_REQUEST");

        // Purga granular de render
        job.purgeResultRenderImage();
        assertThat(job.getResultImageUrl()).isNull();

        // Purga integral
        job.purgeBiometricPhoto();
        assertThat(job.isPhotoPurged()).isTrue();
        assertThat(job.getPhotoUrl()).isEqualTo("PURGED_BY_USER_REQUEST");
        assertThat(job.getResultImageUrl()).isNull();
    }

    @Test
    @DisplayName("HU SR-RF-25: TryOnJob constructor valida photoUrl obligatoria si photoPurged es false")
    void shouldValidatePhotoUrlWhenNotPurged() {
        UUID id = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        assertThatThrownBy(() -> new TryOnJob(
                id, userId, garmentId, null, GarmentCategory.TOPS,
                JobStatus.QUEUED, null, AiProvider.FASHN_AI, null, null, false, Instant.now(), Instant.now()
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new TryOnJob(
                id, userId, garmentId, "   ", GarmentCategory.TOPS,
                JobStatus.QUEUED, null, AiProvider.FASHN_AI, null, null, false, Instant.now(), Instant.now()
        )).isInstanceOf(IllegalArgumentException.class);

        // Si photoPurged es true, permite photoUrl nula o PURGED
        TryOnJob purgedJob = new TryOnJob(
                id, userId, garmentId, null, GarmentCategory.TOPS,
                JobStatus.COMPLETED, null, AiProvider.FASHN_AI, 100L, null, true, Instant.now(), Instant.now()
        );
        assertThat(purgedJob.isPhotoPurged()).isTrue();
    }

    @Test
    @DisplayName("HU SR-RF-25: OutboxEventJpaEntity cubre métodos de reclamo, falla, publicación y getters/setters")
    void shouldExerciseOutboxEventJpaEntityMethods() {
        UUID id = UUID.randomUUID();
        OutboxEventJpaEntity entityWithId = OutboxEventJpaEntity.pending(
                id, "TRY_ON_JOB", "agg-123", "tryon.job.created", "{}"
        );
        assertThat(entityWithId.getId()).isEqualTo(id);
        assertThat(entityWithId.getStatus()).isEqualTo("PENDING");
        assertThat(entityWithId.getRetryCount()).isZero();

        OutboxEventJpaEntity entityRandomId = OutboxEventJpaEntity.pending(
                null, "TRY_ON_JOB", "agg-456", "tryon.job.created", "{}"
        );
        assertThat(entityRandomId.getId()).isNotNull();

        // claim con y sin parámetros explícitos
        Instant fixedNow = Instant.now();
        entityWithId.claim(fixedNow, Duration.ofMinutes(1));
        assertThat(entityWithId.getStatus()).isEqualTo("PROCESSING");
        assertThat(entityWithId.getClaimedAt()).isEqualTo(fixedNow);
        assertThat(entityWithId.getClaimExpiresAt()).isEqualTo(fixedNow.plus(Duration.ofMinutes(1)));

        entityRandomId.claim(null, null);
        assertThat(entityRandomId.getStatus()).isEqualTo("PROCESSING");
        assertThat(entityRandomId.getClaimExpiresAt()).isNotNull();

        // markPublished
        entityWithId.markPublished();
        assertThat(entityWithId.getStatus()).isEqualTo("PUBLISHED");
        assertThat(entityWithId.getPublishedAt()).isNotNull();
        assertThat(entityWithId.getClaimExpiresAt()).isNull();
        assertThat(entityWithId.getErrorMessage()).isNull();

        // recordFailure con error corto y error > 1000 caracteres
        entityWithId.recordFailure("Broker caido");
        assertThat(entityWithId.getStatus()).isEqualTo("PENDING");
        assertThat(entityWithId.getRetryCount()).isEqualTo(1);
        assertThat(entityWithId.getErrorMessage()).isEqualTo("Broker caido");

        String hugeError = "E".repeat(1500);
        entityWithId.recordFailure(hugeError);
        assertThat(entityWithId.getRetryCount()).isEqualTo(2);
        assertThat(entityWithId.getErrorMessage()).hasSize(1000);

        entityWithId.recordFailure(null);
        assertThat(entityWithId.getRetryCount()).isEqualTo(3);
        assertThat(entityWithId.getErrorMessage()).isNull();

        // No-arg constructor y setters
        OutboxEventJpaEntity bean = new OutboxEventJpaEntity();
        bean.setId(id);
        bean.setAggregateType("AG");
        bean.setAggregateId("123");
        bean.setEventType("ET");
        bean.setPayload("P");
        bean.setStatus("ST");
        bean.setRetryCount(5);
        bean.setCreatedAt(fixedNow);
        bean.setPublishedAt(fixedNow);
        bean.setClaimedAt(fixedNow);
        bean.setClaimExpiresAt(fixedNow);
        bean.setErrorMessage("ERR");

        assertThat(bean.getId()).isEqualTo(id);
        assertThat(bean.getAggregateType()).isEqualTo("AG");
        assertThat(bean.getAggregateId()).isEqualTo("123");
        assertThat(bean.getEventType()).isEqualTo("ET");
        assertThat(bean.getPayload()).isEqualTo("P");
        assertThat(bean.getStatus()).isEqualTo("ST");
        assertThat(bean.getRetryCount()).isEqualTo(5);
        assertThat(bean.getCreatedAt()).isEqualTo(fixedNow);
        assertThat(bean.getPublishedAt()).isEqualTo(fixedNow);
        assertThat(bean.getClaimedAt()).isEqualTo(fixedNow);
        assertThat(bean.getClaimExpiresAt()).isEqualTo(fixedNow);
        assertThat(bean.getErrorMessage()).isEqualTo("ERR");
    }
}

