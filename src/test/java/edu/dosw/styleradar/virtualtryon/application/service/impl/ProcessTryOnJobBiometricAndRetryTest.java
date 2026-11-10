package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.OutboxPublisherPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.strategy.AiInferenceStrategy;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias complementarias para ProcessTryOnJobServiceImpl
 * enfocadas en revalidación biométrica, fallos de inferencia definitivos vs transitorios,
 * idempotencia en estado PROCESSING durante reintentos AMQP, terminales en markFailedAfterRetries,
 * y recuperación en sweepStaleProcessingJobs.
 */
@ExtendWith(MockitoExtension.class)
class ProcessTryOnJobBiometricAndRetryTest {

    @Mock
    private TryOnJobRepositoryPort jobRepositoryPort;

    @Mock
    private OutboxPublisherPort outboxPublisherPort;

    @Mock
    private AiInferenceStrategy fashnStrategy;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private BiometricDataRepositoryPort biometricDataRepositoryPort;

    @Mock
    private BlobStoragePort blobStoragePort;

    private ProcessTryOnJobServiceImpl processTryOnJobService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });

        processTryOnJobService = new ProcessTryOnJobServiceImpl(
                jobRepositoryPort,
                outboxPublisherPort,
                List.of(fashnStrategy),
                transactionTemplate,
                biometricDataRepositoryPort,
                blobStoragePort
        );
    }

    private TryOnJob createSampleJob(JobStatus status) {
        UUID jobId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        TryOnJobBuilder builder = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(userId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("photos/user1/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withGarmentImageUrl("https://catalog/shirt.png")
                .withProvider(AiProvider.FASHN_AI);

        TryOnJob job = builder.build();
        if (status == JobStatus.PROCESSING) {
            job.startAiProcessing();
        } else if (status == JobStatus.COMPLETED) {
            job.startAiProcessing();
            job.complete(SasSignedUrl.of("https://storage.blob.core.windows.net/renders/job.png"), 1000L);
        } else if (status == JobStatus.FAILED) {
            job.startAiProcessing();
            job.markFailed("Error previo");
        } else if (status == JobStatus.CANCELLED) {
            job.cancel("Cancelado por usuario");
        }
        return job;
    }

    @Test
    @DisplayName("Bloqueo 3: Si el consentimiento biométrico no está presente o fue revocado, lanza ConsentRequiredException y marca FAILED")
    void shouldFailWhenConsentMissingOrRevoked() {
        TryOnJob job = createSampleJob(JobStatus.QUEUED);
        when(jobRepositoryPort.findJobByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(biometricDataRepositoryPort.findConsentByUserId(job.getUserId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processTryOnJobService.processJob(job.getId()))
                .isInstanceOf(ConsentRequiredException.class)
                .hasMessageContaining("El consentimiento biométrico ya no está vigente");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        verify(outboxPublisherPort).saveOutboxEvent(eq(job.getId()), eq("tryon.job.failed"), any(TryOnJobDomainEvent.class));
    }

    @Test
    @DisplayName("Bloqueo 3: Si la foto biométrica ya no existe, lanza InvalidPhotoException y marca FAILED")
    void shouldFailWhenPhotoMissing() {
        TryOnJob job = createSampleJob(JobStatus.QUEUED);
        BiometricConsent activeConsent = new BiometricConsent(
                UUID.randomUUID(), job.getUserId(), "v1.0", true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"), Instant.now(), null, "127.0.0.1"
        );

        when(jobRepositoryPort.findJobByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(biometricDataRepositoryPort.findConsentByUserId(job.getUserId())).thenReturn(Optional.of(activeConsent));
        when(biometricDataRepositoryPort.findPhotoByUserId(job.getUserId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processTryOnJobService.processJob(job.getId()))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("La fotografía biométrica ya no está disponible");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    @DisplayName("Bloqueo 3: Si la foto pertenece a otro usuario, lanza InvalidPhotoException")
    void shouldFailWhenPhotoBelongsToDifferentUser() {
        TryOnJob job = createSampleJob(JobStatus.QUEUED);
        UserId differentUser = UserId.of(UUID.randomUUID());
        BiometricConsent activeConsent = new BiometricConsent(
                UUID.randomUUID(), job.getUserId(), "v1.0", true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"), Instant.now(), null, "127.0.0.1"
        );
        BiometricPhoto photoOfOther = new BiometricPhoto(
                UUID.randomUUID(), differentUser, "image/png", 1024, 1024, 2048L,
                "photos/other/photo.png", SasSignedUrl.of("https://storage/other.png"), Instant.now()
        );

        when(jobRepositoryPort.findJobByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(biometricDataRepositoryPort.findConsentByUserId(job.getUserId())).thenReturn(Optional.of(activeConsent));
        when(biometricDataRepositoryPort.findPhotoByUserId(job.getUserId())).thenReturn(Optional.of(photoOfOther));

        assertThatThrownBy(() -> processTryOnJobService.processJob(job.getId()))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("no pertenece al titular");
    }

    @Test
    @DisplayName("Bloqueo 3: Si la ruta de la foto no coincide con el job, lanza InvalidPhotoException")
    void shouldFailWhenPhotoPathDoesNotMatch() {
        TryOnJob job = createSampleJob(JobStatus.QUEUED);
        BiometricConsent activeConsent = new BiometricConsent(
                UUID.randomUUID(), job.getUserId(), "v1.0", true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"), Instant.now(), null, "127.0.0.1"
        );
        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(), job.getUserId(), "image/png", 1024, 1024, 2048L,
                "photos/user1/other_photo.png", SasSignedUrl.of("https://storage/user1/other_photo.png"), Instant.now()
        );

        when(jobRepositoryPort.findJobByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(biometricDataRepositoryPort.findConsentByUserId(job.getUserId())).thenReturn(Optional.of(activeConsent));
        when(biometricDataRepositoryPort.findPhotoByUserId(job.getUserId())).thenReturn(Optional.of(photo));
        when(blobStoragePort.normalizeBlobReference(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> processTryOnJobService.processJob(job.getId()))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("no pertenece al titular");
    }

    @Test
    @DisplayName("Bloqueo 3: Si la ruta del job termina con '/' + currentPath, la validación biométrica es exitosa")
    void shouldPassWhenJobPathEndsWithSlashCurrentPath() {
        TryOnJob job = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://account.blob.core.windows.net/biometrics/photos/user1/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withGarmentImageUrl("https://catalog/shirt.png")
                .withProvider(AiProvider.FASHN_AI)
                .build();

        BiometricConsent activeConsent = new BiometricConsent(
                UUID.randomUUID(), job.getUserId(), "v1.0", true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"), Instant.now(), null, "127.0.0.1"
        );
        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(), job.getUserId(), "image/png", 1024, 1024, 2048L,
                "photos/user1/photo.png", SasSignedUrl.of("https://storage/user1/photo.png"), Instant.now()
        );

        when(jobRepositoryPort.findJobByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(biometricDataRepositoryPort.findConsentByUserId(job.getUserId())).thenReturn(Optional.of(activeConsent));
        when(biometricDataRepositoryPort.findPhotoByUserId(job.getUserId())).thenReturn(Optional.of(photo));
        when(blobStoragePort.normalizeBlobReference(any())).thenAnswer(inv -> {
            String arg = inv.getArgument(0);
            if (arg != null && arg.equals(job.getPhotoUrl())) {
                return "biometrics/photos/user1/photo.png";
            }
            return arg;
        });

        when(fashnStrategy.supportsProvider(AiProvider.FASHN_AI)).thenReturn(true);
        when(fashnStrategy.submitTryOn(job)).thenReturn(SasSignedUrl.of("https://storage.blob.core.windows.net/renders/output.png"));

        processTryOnJobService.processJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    @DisplayName("Idempotencia estricta: Si llega un evento duplicado cuando el job ya está en PROCESSING, se descarta y no ejecuta la IA")
    void shouldDiscardDuplicateEventWhenJobAlreadyInProcessing() {
        TryOnJob job = createSampleJob(JobStatus.PROCESSING);
        when(jobRepositoryPort.findJobByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        processTryOnJobService.processJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
        verify(fashnStrategy, never()).submitTryOn(any());
    }

    @Test
    @DisplayName("Reintento AMQP seguro: Si ocurre un fallo transitorio, reencola el job a QUEUED para el backoff")
    void shouldRequeueJobToQueuedOnTransientFailure() {
        TryOnJob job = createSampleJob(JobStatus.QUEUED);
        BiometricConsent activeConsent = new BiometricConsent(
                UUID.randomUUID(), job.getUserId(), "v1.0", true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"), Instant.now(), null, "127.0.0.1"
        );
        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(), job.getUserId(), "image/png", 1024, 1024, 2048L,
                "photos/user1/photo.png", SasSignedUrl.of("https://storage/user1/photo.png"), Instant.now()
        );

        when(jobRepositoryPort.findJobByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(biometricDataRepositoryPort.findConsentByUserId(job.getUserId())).thenReturn(Optional.of(activeConsent));
        when(biometricDataRepositoryPort.findPhotoByUserId(job.getUserId())).thenReturn(Optional.of(photo));
        when(blobStoragePort.normalizeBlobReference(any())).thenAnswer(inv -> inv.getArgument(0));

        when(fashnStrategy.supportsProvider(AiProvider.FASHN_AI)).thenReturn(true);
        when(fashnStrategy.submitTryOn(job)).thenThrow(new RuntimeException("Fallo transitorio de conexion"));

        assertThatThrownBy(() -> processTryOnJobService.processJob(job.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Fallo transitorio de conexion");

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    @DisplayName("Bloqueo 2: Si outboxPublisherPort.publishPendingEvents() lanza excepción, no interrumpe el éxito del trabajo")
    void shouldTolerateOutboxPublisherException() {
        TryOnJob job = createSampleJob(JobStatus.QUEUED);
        BiometricConsent activeConsent = new BiometricConsent(
                UUID.randomUUID(), job.getUserId(), "v1.0", true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"), Instant.now(), null, "127.0.0.1"
        );
        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(), job.getUserId(), "image/png", 1024, 1024, 2048L,
                "photos/user1/photo.png", SasSignedUrl.of("https://storage/user1/photo.png"), Instant.now()
        );

        when(jobRepositoryPort.findJobByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(biometricDataRepositoryPort.findConsentByUserId(job.getUserId())).thenReturn(Optional.of(activeConsent));
        when(biometricDataRepositoryPort.findPhotoByUserId(job.getUserId())).thenReturn(Optional.of(photo));
        when(blobStoragePort.normalizeBlobReference(any())).thenAnswer(inv -> inv.getArgument(0));

        when(fashnStrategy.supportsProvider(AiProvider.FASHN_AI)).thenReturn(true);
        when(fashnStrategy.submitTryOn(job)).thenReturn(SasSignedUrl.of("https://storage.blob.core.windows.net/renders/output.png"));
        doThrow(new RuntimeException("Error temporal de conexión Outbox")).when(outboxPublisherPort).publishPendingEvents();

        processTryOnJobService.processJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    @DisplayName("Bloqueo 2: markFailedAfterRetries no modifica trabajos en estados terminales (COMPLETED, FAILED, CANCELLED)")
    void shouldNotModifyTerminalJobsInMarkFailedAfterRetries() {
        TryOnJob completedJob = createSampleJob(JobStatus.COMPLETED);
        TryOnJob failedJob = createSampleJob(JobStatus.FAILED);
        TryOnJob cancelledJob = createSampleJob(JobStatus.CANCELLED);

        when(jobRepositoryPort.findJobByIdForUpdate(completedJob.getId())).thenReturn(Optional.of(completedJob));
        when(jobRepositoryPort.findJobByIdForUpdate(failedJob.getId())).thenReturn(Optional.of(failedJob));
        when(jobRepositoryPort.findJobByIdForUpdate(cancelledJob.getId())).thenReturn(Optional.of(cancelledJob));

        processTryOnJobService.markFailedAfterRetries(completedJob.getId(), "error");
        processTryOnJobService.markFailedAfterRetries(failedJob.getId(), "error");
        processTryOnJobService.markFailedAfterRetries(cancelledJob.getId(), "error");

        assertThat(completedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(failedJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(cancelledJob.getStatus()).isEqualTo(JobStatus.CANCELLED);
        verify(jobRepositoryPort, never()).saveJob(completedJob);
    }

    @Test
    @DisplayName("sweepStaleProcessingJobs cubre casos de eliminación concurrente, estado no PROCESSING y actualización reciente")
    void shouldCoverSweepStaleProcessingJobsEdgeCases() {
        UUID idNotFound = UUID.randomUUID();
        UUID idNotProcessing = UUID.randomUUID();
        UUID idRecentlyUpdated = UUID.randomUUID();
        UUID idRecoverable = UUID.randomUUID();

        TryOnJob candidateNotFound = new TryOnJobBuilder().withId(idNotFound).withUserId(UserId.of(UUID.randomUUID())).withGarmentId(GarmentId.of(UUID.randomUUID())).withPhotoUrl("p").withCategory(GarmentCategory.TOPS).withGarmentImageUrl("g").withProvider(AiProvider.FASHN_AI).build();
        TryOnJob candidateNotProcessing = new TryOnJobBuilder().withId(idNotProcessing).withUserId(UserId.of(UUID.randomUUID())).withGarmentId(GarmentId.of(UUID.randomUUID())).withPhotoUrl("p").withCategory(GarmentCategory.TOPS).withGarmentImageUrl("g").withProvider(AiProvider.FASHN_AI).build();
        candidateNotProcessing.startAiProcessing();
        candidateNotProcessing.complete(SasSignedUrl.of("https://storage.blob.core.windows.net/res"), 100L);

        TryOnJob candidateRecentlyUpdated = new TryOnJobBuilder().withId(idRecentlyUpdated).withUserId(UserId.of(UUID.randomUUID())).withGarmentId(GarmentId.of(UUID.randomUUID())).withPhotoUrl("p").withCategory(GarmentCategory.TOPS).withGarmentImageUrl("g").withProvider(AiProvider.FASHN_AI).build();
        candidateRecentlyUpdated.startAiProcessing();

        TryOnJob candidateRecoverable = new TryOnJobBuilder().withId(idRecoverable).withUserId(UserId.of(UUID.randomUUID())).withGarmentId(GarmentId.of(UUID.randomUUID())).withPhotoUrl("p").withCategory(GarmentCategory.TOPS).withGarmentImageUrl("g").withProvider(AiProvider.FASHN_AI).build();
        candidateRecoverable.startAiProcessing();
        // Simular que fue actualizado hace 1 hora (anterior al threshold)
        java.lang.reflect.Field updatedAtField;
        try {
            updatedAtField = TryOnJob.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(candidateRecoverable, Instant.now().minus(Duration.ofHours(1)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(jobRepositoryPort.findStaleProcessingJobs(any())).thenReturn(List.of(
                candidateNotFound, candidateNotProcessing, candidateRecentlyUpdated, candidateRecoverable
        ));
        when(jobRepositoryPort.findJobByIdForUpdate(idNotFound)).thenReturn(Optional.empty());
        when(jobRepositoryPort.findJobByIdForUpdate(idNotProcessing)).thenReturn(Optional.of(candidateNotProcessing));
        when(jobRepositoryPort.findJobByIdForUpdate(idRecentlyUpdated)).thenReturn(Optional.of(candidateRecentlyUpdated));
        when(jobRepositoryPort.findJobByIdForUpdate(idRecoverable)).thenReturn(Optional.of(candidateRecoverable));

        int recovered = processTryOnJobService.sweepStaleProcessingJobs(Duration.ofMinutes(10));

        assertThat(recovered).isEqualTo(1);
        assertThat(candidateRecoverable.getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    @DisplayName("Condición de carrera (RF-27): Si el trabajo fue purgado mientras la IA ejecutaba, descarta el render generado en Azure y no completa el trabajo")
    void shouldDiscardGeneratedRenderWhenJobPurgedDuringAiInference() {
        TryOnJob initialJob = createSampleJob(JobStatus.QUEUED);
        UUID jobId = initialJob.getId();
        UserId userId = initialJob.getUserId();

        BiometricConsent activeConsent = new BiometricConsent(
                UUID.randomUUID(), userId, "v1.0", true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"), Instant.now(), null, "127.0.0.1"
        );
        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(), userId, "image/png", 1024, 1024, 2048L,
                "photos/user1/photo.png", SasSignedUrl.of("https://storage/user1/photo.png"), Instant.now()
        );

        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(activeConsent));
        when(biometricDataRepositoryPort.findPhotoByUserId(userId)).thenReturn(Optional.of(photo));
        when(blobStoragePort.normalizeBlobReference(any())).thenAnswer(inv -> inv.getArgument(0));

        TryOnJob purgedJob = createSampleJob(JobStatus.PROCESSING);
        purgedJob.purgeBiometricPhoto(); // photoPurged = true, photoUrl = PURGED_BY_USER_REQUEST

        // En la primera llamada (bloqueo 1), está QUEUED; en la segunda llamada (bloqueo 3 tras inferencia), ya fue purgado por el usuario
        when(jobRepositoryPort.findJobByIdForUpdate(jobId))
                .thenReturn(Optional.of(initialJob))
                .thenReturn(Optional.of(purgedJob));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));

        when(fashnStrategy.supportsProvider(AiProvider.FASHN_AI)).thenReturn(true);
        SasSignedUrl renderUrl = SasSignedUrl.of("https://storage.blob.core.windows.net/renders/generated-output.png?sp=r");
        when(fashnStrategy.submitTryOn(any(TryOnJob.class))).thenReturn(renderUrl);

        processTryOnJobService.processJob(jobId);

        // Verifica que se descartó físicamente la imagen generada llamando a deleteBlob
        verify(blobStoragePort).deleteBlob(renderUrl.value());
        // Verifica que el trabajo purgado NO se transicionó a COMPLETED ni se guardó evento de completed
        assertThat(purgedJob.getStatus()).isNotEqualTo(JobStatus.COMPLETED);
        verify(outboxPublisherPort, never()).saveOutboxEvent(eq(jobId), eq("tryon.job.completed"), any(TryOnJobDomainEvent.class));
    }

    @Test
    @DisplayName("Condición de carrera (RF-27): Si la foto biométrica fue eliminada mientras la IA ejecutaba, descarta render y purga trabajo")
    void shouldDiscardGeneratedRenderAndPurgeJobWhenPhotoDeletedDuringAiInference() {
        TryOnJob initialJob = createSampleJob(JobStatus.QUEUED);
        UUID jobId = initialJob.getId();
        UserId userId = initialJob.getUserId();

        BiometricConsent activeConsent = new BiometricConsent(
                UUID.randomUUID(), userId, "v1.0", true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"), Instant.now(), null, "127.0.0.1"
        );
        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(), userId, "image/png", 1024, 1024, 2048L,
                "photos/user1/photo.png", SasSignedUrl.of("https://storage/user1/photo.png"), Instant.now()
        );

        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(activeConsent));
        // Primera consulta (revalidate) tiene foto; segunda consulta (step 3) ya no tiene foto (usuario borró sus datos biométricos)
        when(biometricDataRepositoryPort.findPhotoByUserId(userId))
                .thenReturn(Optional.of(photo))
                .thenReturn(Optional.empty());
        when(blobStoragePort.normalizeBlobReference(any())).thenAnswer(inv -> inv.getArgument(0));

        when(jobRepositoryPort.findJobByIdForUpdate(jobId)).thenReturn(Optional.of(initialJob));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));

        when(fashnStrategy.supportsProvider(AiProvider.FASHN_AI)).thenReturn(true);
        SasSignedUrl renderUrl = SasSignedUrl.of("https://storage.blob.core.windows.net/renders/generated-output.png?sp=r");
        when(fashnStrategy.submitTryOn(any(TryOnJob.class))).thenReturn(renderUrl);

        processTryOnJobService.processJob(jobId);

        // Verifica que se descartó físicamente el render en Azure
        verify(blobStoragePort).deleteBlob(renderUrl.value());
        // Verifica que el trabajo fue purgado y no quedó en COMPLETED
        assertThat(initialJob.isPhotoPurged()).isTrue();
        assertThat(initialJob.getStatus()).isNotEqualTo(JobStatus.COMPLETED);
        verify(outboxPublisherPort, never()).saveOutboxEvent(eq(jobId), eq("tryon.job.completed"), any(TryOnJobDomainEvent.class));
    }

    @Test
    @DisplayName("Condición de carrera (RF-27): Cuando falla el descarte del render tras reintentos, se registra como pendiente de limpieza en Outbox y auditoría")
    void shouldRegisterPendingCleanupWhenDiscardGeneratedRenderFailsAfterRetries() {
        TryOnJob initialJob = createSampleJob(JobStatus.QUEUED);
        UUID jobId = initialJob.getId();
        UserId userId = initialJob.getUserId();

        BiometricConsent activeConsent = new BiometricConsent(
                UUID.randomUUID(), userId, "v1.0", true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"), Instant.now(), null, "127.0.0.1"
        );
        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(), userId, "image/png", 1024, 1024, 2048L,
                "photos/user1/photo.png", SasSignedUrl.of("https://storage/user1/photo.png"), Instant.now()
        );

        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(activeConsent));
        when(biometricDataRepositoryPort.findPhotoByUserId(userId)).thenReturn(Optional.of(photo));
        when(blobStoragePort.normalizeBlobReference(any())).thenAnswer(inv -> inv.getArgument(0));

        TryOnJob purgedJob = createSampleJob(JobStatus.PROCESSING);
        purgedJob.purgeBiometricPhoto();

        when(jobRepositoryPort.findJobByIdForUpdate(jobId))
                .thenReturn(Optional.of(initialJob))
                .thenReturn(Optional.of(purgedJob));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));

        when(fashnStrategy.supportsProvider(AiProvider.FASHN_AI)).thenReturn(true);
        SasSignedUrl renderUrl = SasSignedUrl.of("https://storage.blob.core.windows.net/renders/failed-render.png");
        when(fashnStrategy.submitTryOn(any(TryOnJob.class))).thenReturn(renderUrl);

        // Simular que la eliminación de Azure Blob Storage falla consistentemente (3 reintentos)
        doThrow(new IllegalStateException("Azure Blob Storage 503 Service Unavailable"))
                .when(blobStoragePort).deleteBlob(renderUrl.value());

        processTryOnJobService.processJob(jobId);

        // Verifica que se realizaron los 3 intentos de eliminación (reintentos)
        verify(blobStoragePort, times(3)).deleteBlob(renderUrl.value());

        // Verifica que se registró el borrado fallido como pendiente de limpieza en Transactional Outbox
        verify(outboxPublisherPort).saveOutboxEvent(eq(jobId), eq("tryon.blob.cleanup"), eq(renderUrl.value()));

        // Verifica que se registró en la auditoría como PENDING_BLOB_CLEANUP
        ArgumentCaptor<edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog> auditCaptor =
                ArgumentCaptor.forClass(edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog.class);
        verify(jobRepositoryPort, org.mockito.Mockito.atLeastOnce()).saveAuditLog(auditCaptor.capture());
        boolean hasPendingCleanupLog = auditCaptor.getAllValues().stream()
                .anyMatch(log -> log.getTransitionReason() != null && log.getTransitionReason().contains("PENDING_BLOB_CLEANUP"));
        assertThat(hasPendingCleanupLog).isTrue();
    }
}

