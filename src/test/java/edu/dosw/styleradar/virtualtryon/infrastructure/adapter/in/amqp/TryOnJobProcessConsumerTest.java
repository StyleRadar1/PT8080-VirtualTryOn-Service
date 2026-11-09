package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.port.in.ProcessTryOnJobUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pruebas unitarias para el consumidor AMQP de procesamiento de trabajos de prueba virtual (SR-RF-25).
 */
@ExtendWith(MockitoExtension.class)
class TryOnJobProcessConsumerTest {

    @Mock
    private ProcessTryOnJobUseCase processTryOnJobUseCase;

    @InjectMocks
    private TryOnJobProcessConsumer consumer;

    @Test
    @DisplayName("HU SR-RF-25: Cuando llega evento tryon.job.created válido, delega en ProcessTryOnJobUseCase")
    void shouldCallProcessJobWhenValidEventReceived() {
        UUID jobId = UUID.randomUUID();
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(),
                jobId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JobStatus.QUEUED,
                null,
                null,
                Instant.now()
        );

        consumer.onTryOnJobCreated(event);

        verify(processTryOnJobUseCase).processJob(jobId);
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando el evento o jobId es nulo, descarta el mensaje sin invocar el caso de uso")
    void shouldIgnoreNullEventOrJobId() {
        consumer.onTryOnJobCreated(null);
        verify(processTryOnJobUseCase, never()).processJob(any());

        TryOnJobDomainEvent eventWithNullJob = org.mockito.Mockito.mock(TryOnJobDomainEvent.class);
        org.mockito.Mockito.when(eventWithNullJob.jobId()).thenReturn(null);
        consumer.onTryOnJobCreated(eventWithNullJob);
        verify(processTryOnJobUseCase, never()).processJob(any());
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando falla con error transitorio, propaga RuntimeException para que Spring Retry aplique backoff")
    void shouldPropagateTransientExceptionForRetry() {
        UUID jobId = UUID.randomUUID();
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(),
                jobId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JobStatus.QUEUED,
                null,
                null,
                Instant.now()
        );

        doThrow(new RuntimeException("Error transitorio de red o timeout")).when(processTryOnJobUseCase).processJob(jobId);

        assertThatThrownBy(() -> consumer.onTryOnJobCreated(event))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    @DisplayName("HU SR-RF-25: Consumidor que recibe el mismo mensaje dos veces delega en caso de uso idempotente sin lanzar excepción")
    void shouldHandleDuplicateMessageIdempotently() {
        UUID jobId = UUID.randomUUID();
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(),
                jobId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JobStatus.QUEUED,
                null,
                null,
                Instant.now()
        );

        // Primera entrega
        consumer.onTryOnJobCreated(event);
        // Segunda entrega (mensaje duplicado por redelivery tras fallo de ack de red)
        consumer.onTryOnJobCreated(event);

        verify(processTryOnJobUseCase, org.mockito.Mockito.times(2)).processJob(jobId);
    }

    @Test
    @DisplayName("HU SR-RF-25: Error permanente de trabajo inexistente (JobNotFoundException) desvía inmediatamente a DLQ sin reintentar")
    void shouldRejectPermanentlyToDlqOnJobNotFoundException() {
        UUID jobId = UUID.randomUUID();
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(),
                jobId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JobStatus.QUEUED,
                null,
                null,
                Instant.now()
        );

        doThrow(new edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException(jobId))
                .when(processTryOnJobUseCase).processJob(jobId);

        assertThatThrownBy(() -> consumer.onTryOnJobCreated(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Desviado a DLQ");
    }

    @Test
    @DisplayName("HU SR-RF-25: Error permanente de consentimiento revocado desvía inmediatamente a DLQ sin reintentar")
    void shouldRejectPermanentlyToDlqOnConsentRequiredException() {
        UUID jobId = UUID.randomUUID();
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(), jobId, UUID.randomUUID(), UUID.randomUUID(),
                JobStatus.QUEUED, null, null, Instant.now()
        );

        doThrow(new edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException("Consentimiento revocado"))
                .when(processTryOnJobUseCase).processJob(jobId);

        assertThatThrownBy(() -> consumer.onTryOnJobCreated(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Desviado a DLQ");
    }

    @Test
    @DisplayName("HU SR-RF-25: Error permanente de foto inválida desvía inmediatamente a DLQ sin reintentar")
    void shouldRejectPermanentlyToDlqOnInvalidPhotoException() {
        UUID jobId = UUID.randomUUID();
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(), jobId, UUID.randomUUID(), UUID.randomUUID(),
                JobStatus.QUEUED, null, null, Instant.now()
        );

        doThrow(new edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException("Foto de otro titular"))
                .when(processTryOnJobUseCase).processJob(jobId);

        assertThatThrownBy(() -> consumer.onTryOnJobCreated(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Desviado a DLQ");
    }
}

