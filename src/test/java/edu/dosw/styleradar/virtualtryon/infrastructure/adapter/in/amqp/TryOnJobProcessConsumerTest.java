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
        verify(processTryOnJobUseCase, never()).processJob(null);
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando falla el procesamiento, lanza AmqpRejectAndDontRequeueException para desviar a DLQ")
    void shouldThrowAmqpRejectWhenProcessThrows() {
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

        doThrow(new RuntimeException("Error en worker")).when(processTryOnJobUseCase).processJob(jobId);

        assertThatThrownBy(() -> consumer.onTryOnJobCreated(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}

