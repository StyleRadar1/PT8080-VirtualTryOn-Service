package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.AiProviderTimeoutException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnEventPublisherPort;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para el procesador asíncrono de inferencia de IA (SR-RF-25).
 */
@ExtendWith(MockitoExtension.class)
class ProcessTryOnJobServiceImplTest {

    @Mock
    private TryOnJobRepositoryPort jobRepositoryPort;

    @Mock
    private TryOnEventPublisherPort eventPublisherPort;

    @Mock
    private AiInferenceStrategy fashnStrategy;

    private ProcessTryOnJobServiceImpl processTryOnJobService;

    @BeforeEach
    void setUp() {
        processTryOnJobService = new ProcessTryOnJobServiceImpl(
                jobRepositoryPort,
                eventPublisherPort,
                List.of(fashnStrategy)
        );
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando se procesa un job QUEUED exitosamente, pasa a PROCESSING, luego a COMPLETED y publica tryon.job.completed")
    void shouldProcessJobSuccessfullyAndMarkCompleted() {
        UUID jobId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        TryOnJob job = new TryOnJobBuilder()
                .withUserId(userId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withGarmentImageUrl("https://catalog/shirt.png")
                .withProvider(AiProvider.FASHN_AI)
                .build();

        when(jobRepositoryPort.findJobById(jobId)).thenReturn(Optional.of(job));
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
        verify(eventPublisherPort).publishJobEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(eventCaptor.getValue().routingKey()).isEqualTo("tryon.job.completed");
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando la estrategia de IA falla, marca el job como FAILED, registra auditoría y publica tryon.job.failed")
    void shouldMarkJobFailedWhenAiStrategyThrowsException() {
        UUID jobId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        TryOnJob job = new TryOnJobBuilder()
                .withUserId(userId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withGarmentImageUrl("https://catalog/shirt.png")
                .withProvider(AiProvider.FASHN_AI)
                .build();

        when(jobRepositoryPort.findJobById(jobId)).thenReturn(Optional.of(job));
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
        verify(eventPublisherPort).publishJobEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo(JobStatus.FAILED);
        assertThat(eventCaptor.getValue().routingKey()).isEqualTo("tryon.job.failed");
    }

    @Test
    @DisplayName("HU SR-RF-25: Si el trabajo ya está en estado terminal (COMPLETED, FAILED, CANCELLED), omite la inferencia por idempotencia")
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

        when(jobRepositoryPort.findJobById(jobId)).thenReturn(Optional.of(completedJob));
        processTryOnJobService.processJob(jobId);

        completedJob.markFailed("Fallo previo");
        processTryOnJobService.processJob(jobId);

        completedJob.startAiProcessing();
        processTryOnJobService.processJob(jobId);

        verify(fashnStrategy, never()).submitTryOn(any());
        verify(eventPublisherPort, never()).publishJobEvent(any());
    }

    @Test
    @DisplayName("HU SR-RF-25: Ejecución a través de TransactionTemplate y manejo de proveedor no soportado")
    void shouldExecuteWithTransactionTemplateAndHandleUnsupportedProvider() {
        org.springframework.transaction.support.TransactionTemplate mockTemplate = org.mockito.Mockito.mock(
                org.springframework.transaction.support.TransactionTemplate.class
        );
        when(mockTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });

        ProcessTryOnJobServiceImpl serviceWithTx = new ProcessTryOnJobServiceImpl(
                jobRepositoryPort,
                eventPublisherPort,
                List.of(fashnStrategy),
                mockTemplate
        );

        UUID jobId = UUID.randomUUID();
        TryOnJob job = new TryOnJobBuilder()
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .withProvider(AiProvider.MOCK_LOCAL)
                .build();

        when(jobRepositoryPort.findJobById(jobId)).thenReturn(Optional.of(job));
        when(jobRepositoryPort.saveJob(any(TryOnJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fashnStrategy.supportsProvider(AiProvider.MOCK_LOCAL)).thenReturn(false);

        assertThatThrownBy(() -> serviceWithTx.processJob(jobId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando el trabajo no existe, lanza JobNotFoundException")
    void shouldThrowJobNotFoundExceptionWhenJobDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();
        when(jobRepositoryPort.findJobById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processTryOnJobService.processJob(nonExistentId))
                .isInstanceOf(JobNotFoundException.class);
    }
}

