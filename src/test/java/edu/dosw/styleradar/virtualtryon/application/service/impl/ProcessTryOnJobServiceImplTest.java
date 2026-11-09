package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.AiProviderTimeoutException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para ProcessTryOnJobServiceImpl (SR-RF-25).
 * Verifica transiciones atómicas con bloqueo pesimista en base de datos, idempotencia
 * ante mensajes duplicados o reentregas, y persistencia de eventos en Transactional Outbox.
 */
@ExtendWith(MockitoExtension.class)
class ProcessTryOnJobServiceImplTest {

    @Mock
    private TryOnJobRepositoryPort jobRepositoryPort;

    @Mock
    private OutboxPublisherPort outboxPublisherPort;

    @Mock
    private AiInferenceStrategy fashnStrategy;

    @Mock
    private TransactionTemplate transactionTemplate;

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
                transactionTemplate
        );
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando se procesa un job QUEUED exitosamente, pasa a PROCESSING, luego a COMPLETED y guarda tryon.job.completed en Outbox")
    void shouldProcessJobSuccessfullyAndMarkCompleted() {
        UUID jobId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        TryOnJob job = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(userId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withGarmentImageUrl("https://catalog/shirt.png")
                .withProvider(AiProvider.FASHN_AI)
                .build();

        when(jobRepositoryPort.findJobByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(fashnStrategy.supportsProvider(AiProvider.FASHN_AI)).thenReturn(true);
        when(fashnStrategy.submitTryOn(job)).thenReturn(
                SasSignedUrl.of("https://storage/render.png?sp=r")
        );
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));

        processTryOnJobService.processJob(jobId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getResultImageUrl()).isNotNull();
        assertThat(job.getResultImageUrl().value()).contains("sp=r");

        ArgumentCaptor<TryOnJobDomainEvent> eventCaptor = ArgumentCaptor.forClass(TryOnJobDomainEvent.class);
        verify(outboxPublisherPort).saveOutboxEvent(eq(jobId), eq("tryon.job.completed"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(eventCaptor.getValue().routingKey()).isEqualTo("tryon.job.completed");
        verify(outboxPublisherPort).publishPendingEvents();
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando la estrategia de IA falla, marca el job como FAILED, registra auditoría y guarda tryon.job.failed en Outbox")
    void shouldMarkJobFailedWhenAiStrategyThrowsException() {
        UUID jobId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        TryOnJob job = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(userId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withGarmentImageUrl("https://catalog/shirt.png")
                .withProvider(AiProvider.FASHN_AI)
                .build();

        when(jobRepositoryPort.findJobByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(fashnStrategy.supportsProvider(AiProvider.FASHN_AI)).thenReturn(true);
        when(fashnStrategy.submitTryOn(job)).thenThrow(
                new AiProviderTimeoutException("FASHN AI excedió el tiempo límite de generación")
        );
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> processTryOnJobService.processJob(jobId))
                .isInstanceOf(AiProviderTimeoutException.class);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("FASHN AI excedió el tiempo límite");

        ArgumentCaptor<TryOnJobDomainEvent> eventCaptor = ArgumentCaptor.forClass(TryOnJobDomainEvent.class);
        verify(outboxPublisherPort).saveOutboxEvent(eq(jobId), eq("tryon.job.failed"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo(JobStatus.FAILED);
        assertThat(eventCaptor.getValue().routingKey()).isEqualTo("tryon.job.failed");
    }

    @Test
    @DisplayName("HU SR-RF-25: Si el trabajo ya está en estado terminal (COMPLETED, FAILED, PROCESSING), omite la inferencia por idempotencia")
    void shouldSkipExecutionWhenJobAlreadyInTerminalStatus() {
        UUID jobId = UUID.randomUUID();
        TryOnJob completedJob = new TryOnJobBuilder()
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withProvider(AiProvider.FASHN_AI)
                .build();
        completedJob.complete(SasSignedUrl.of("https://storage/done.png"), 1500L);

        when(jobRepositoryPort.findJobByIdForUpdate(jobId)).thenReturn(Optional.of(completedJob));
        processTryOnJobService.processJob(jobId);

        completedJob.markFailed("Fallo previo");
        processTryOnJobService.processJob(jobId);

        completedJob.startAiProcessing();
        processTryOnJobService.processJob(jobId);

        verify(fashnStrategy, never()).submitTryOn(any());
        verify(outboxPublisherPort, never()).saveOutboxEvent(any(), any(), any());
    }

    @Test
    @DisplayName("HU SR-RF-25: Ejecución a través de TransactionTemplate y manejo de proveedor no soportado")
    void shouldExecuteWithTransactionTemplateAndHandleUnsupportedProvider() {
        UUID jobId = UUID.randomUUID();
        TryOnJob job = new TryOnJobBuilder()
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withProvider(AiProvider.MOCK_LOCAL)
                .build();

        when(jobRepositoryPort.findJobByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fashnStrategy.supportsProvider(AiProvider.MOCK_LOCAL)).thenReturn(false);

        assertThatThrownBy(() -> processTryOnJobService.processJob(jobId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando el trabajo no existe, lanza JobNotFoundException")
    void shouldThrowJobNotFoundExceptionWhenJobDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();
        when(jobRepositoryPort.findJobByIdForUpdate(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processTryOnJobService.processJob(nonExistentId))
                .isInstanceOf(JobNotFoundException.class);
    }

    @Test
    @DisplayName("HU SR-RF-25: Recuperación de trabajos bloqueados en PROCESSING con bloqueo pesimista y re-verificación de estado")
    void shouldSweepStaleProcessingJobs() {
        TryOnJob staleJob = new TryOnJob(
                UUID.randomUUID(),
                UserId.of(UUID.randomUUID()),
                GarmentId.of(UUID.randomUUID()),
                "https://storage/photo.png",
                GarmentCategory.TOPS,
                "https://catalog/shirt.png",
                JobStatus.PROCESSING,
                null,
                AiProvider.FASHN_AI,
                null,
                null,
                false,
                Instant.now().minus(Duration.ofMinutes(15)),
                Instant.now().minus(Duration.ofMinutes(15)) // Más antiguo que threshold (10 min)
        );

        when(jobRepositoryPort.findStaleProcessingJobs(any())).thenReturn(List.of(staleJob));
        when(jobRepositoryPort.findJobByIdForUpdate(staleJob.getId())).thenReturn(Optional.of(staleJob));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));

        int recovered = processTryOnJobService.sweepStaleProcessingJobs(Duration.ofMinutes(10));

        assertThat(recovered).isEqualTo(1);
        assertThat(staleJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(staleJob.getErrorMessage()).contains("Timeout de procesamiento");
        verify(outboxPublisherPort).saveOutboxEvent(eq(staleJob.getId()), eq("tryon.job.failed"), any());
    }

    @Test
    @DisplayName("HU SR-RF-25: sweepStaleProcessingJobs omite trabajos que cambiaron a COMPLETED o fueron actualizados recientemente (evita carrera con worker)")
    void shouldSkipRecoveryWhenJobAlreadyCompletedOrRecentlyUpdated() {
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();

        // Trabajo 1: pasó a COMPLETED mientras el recuperador ejecutaba
        TryOnJob completedJob = new TryOnJob(
                jobId1,
                UserId.of(UUID.randomUUID()),
                GarmentId.of(UUID.randomUUID()),
                "https://storage/photo.png",
                GarmentCategory.TOPS,
                "https://catalog/shirt.png",
                JobStatus.COMPLETED,
                SasSignedUrl.of("https://storage/render.png"),
                AiProvider.FASHN_AI,
                2000L,
                null,
                false,
                Instant.now().minus(Duration.ofMinutes(15)),
                Instant.now()
        );

        // Trabajo 2: sigue en PROCESSING pero fue actualizado hace 1 minuto (worker activo)
        TryOnJob activeWorkerJob = new TryOnJob(
                jobId2,
                UserId.of(UUID.randomUUID()),
                GarmentId.of(UUID.randomUUID()),
                "https://storage/photo.png",
                GarmentCategory.TOPS,
                "https://catalog/shirt.png",
                JobStatus.PROCESSING,
                null,
                AiProvider.FASHN_AI,
                null,
                null,
                false,
                Instant.now().minus(Duration.ofMinutes(15)),
                Instant.now().minus(Duration.ofMinutes(1))
        );

        when(jobRepositoryPort.findStaleProcessingJobs(any())).thenReturn(List.of(completedJob, activeWorkerJob));
        when(jobRepositoryPort.findJobByIdForUpdate(jobId1)).thenReturn(Optional.of(completedJob));
        when(jobRepositoryPort.findJobByIdForUpdate(jobId2)).thenReturn(Optional.of(activeWorkerJob));

        int recovered = processTryOnJobService.sweepStaleProcessingJobs(Duration.ofMinutes(10));

        // Ninguno debió ser marcado como fallido
        assertThat(recovered).isZero();
        assertThat(completedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(activeWorkerJob.getStatus()).isEqualTo(JobStatus.PROCESSING);
        verify(jobRepositoryPort, never()).saveJob(completedJob);
        verify(jobRepositoryPort, never()).saveJob(activeWorkerJob);
    }
}
