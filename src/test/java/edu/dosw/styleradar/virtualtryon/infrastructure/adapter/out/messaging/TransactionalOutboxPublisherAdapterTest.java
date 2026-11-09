package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnEventPublisherPort;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.OutboxEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para TransactionalOutboxPublisherAdapter (SR-RF-25).
 */
@ExtendWith(MockitoExtension.class)
class TransactionalOutboxPublisherAdapterTest {

    @Mock
    private OutboxEventJpaRepository outboxEventRepository;

    @Mock
    private TryOnEventPublisherPort tryOnEventPublisherPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ObjectMapper objectMapper;
    private TransactionalOutboxPublisherAdapter outboxPublisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });

        outboxPublisher = new TransactionalOutboxPublisherAdapter(
                outboxEventRepository,
                tryOnEventPublisherPort,
                objectMapper,
                transactionTemplate
        );
    }

    @Test
    @DisplayName("HU SR-RF-25: Guarda evento en outbox_events como PENDING")
    void shouldSaveOutboxEvent() {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID garmentId = UUID.randomUUID();
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(), jobId, userId, garmentId, JobStatus.QUEUED, null, null, Instant.now()
        );

        outboxPublisher.saveOutboxEvent(jobId, "tryon.job.created", event);

        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEventJpaEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getEventType()).isEqualTo("tryon.job.created");
        assertThat(saved.getAggregateId()).isEqualTo(jobId.toString());
        assertThat(saved.getPayload()).contains(jobId.toString());
    }

    @Test
    @DisplayName("HU SR-RF-25: Despacha eventos pendientes y los marca como PUBLISHED")
    void shouldPublishPendingEvents() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID garmentId = UUID.randomUUID();
        TryOnJobDomainEvent domainEvent = new TryOnJobDomainEvent(
                UUID.randomUUID(), jobId, userId, garmentId, JobStatus.QUEUED, null, null, Instant.now()
        );
        String jsonPayload = objectMapper.writeValueAsString(domainEvent);

        OutboxEventJpaEntity pendingEntity = OutboxEventJpaEntity.pending(
                UUID.randomUUID(), "TRY_ON_JOB", jobId.toString(), "tryon.job.created", jsonPayload
        );

        when(outboxEventRepository.findPendingEvents()).thenReturn(List.of(pendingEntity));

        outboxPublisher.publishPendingEvents();

        verify(tryOnEventPublisherPort).publishJobEvent(any(TryOnJobDomainEvent.class));
        assertThat(pendingEntity.getStatus()).isEqualTo("PUBLISHED");
        assertThat(pendingEntity.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("HU SR-RF-25: Registra fallo e incrementa retry_count si el broker AMQP falla")
    void shouldRecordFailureWhenBrokerThrowsException() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID garmentId = UUID.randomUUID();
        TryOnJobDomainEvent domainEvent = new TryOnJobDomainEvent(
                UUID.randomUUID(), jobId, userId, garmentId, JobStatus.QUEUED, null, null, Instant.now()
        );
        String jsonPayload = objectMapper.writeValueAsString(domainEvent);

        OutboxEventJpaEntity pendingEntity = OutboxEventJpaEntity.pending(
                UUID.randomUUID(), "TRY_ON_JOB", jobId.toString(), "tryon.job.created", jsonPayload
        );

        when(outboxEventRepository.findPendingEvents()).thenReturn(List.of(pendingEntity));
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(tryOnEventPublisherPort).publishJobEvent(any(TryOnJobDomainEvent.class));

        outboxPublisher.publishPendingEvents();

        assertThat(pendingEntity.getStatus()).isEqualTo("PENDING");
        assertThat(pendingEntity.getRetryCount()).isEqualTo(1);
        assertThat(pendingEntity.getErrorMessage()).contains("RabbitMQ connection refused");
    }

    @Test
    @DisplayName("HU SR-RF-25: No realiza operaciones si no hay eventos pendientes")
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxEventRepository.findPendingEvents()).thenReturn(List.of());
        outboxPublisher.publishPendingEvents();
        verify(tryOnEventPublisherPort, never()).publishJobEvent(any());
    }

    @Test
    @DisplayName("HU SR-RF-25: Despacha eventos completed y failed correctamente")
    void shouldPublishCompletedAndFailedEvents() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID garmentId = UUID.randomUUID();

        TryOnJobDomainEvent completedEvent = new TryOnJobDomainEvent(
                UUID.randomUUID(), jobId, userId, garmentId, JobStatus.COMPLETED, "https://render.png", null, Instant.now()
        );
        TryOnJobDomainEvent failedEvent = new TryOnJobDomainEvent(
                UUID.randomUUID(), jobId, userId, garmentId, JobStatus.FAILED, null, "Error AI", Instant.now()
        );

        OutboxEventJpaEntity entity1 = OutboxEventJpaEntity.pending(
                UUID.randomUUID(), "TRY_ON_JOB", jobId.toString(), "tryon.job.completed", objectMapper.writeValueAsString(completedEvent)
        );
        OutboxEventJpaEntity entity2 = OutboxEventJpaEntity.pending(
                UUID.randomUUID(), "TRY_ON_JOB", jobId.toString(), "tryon.job.failed", objectMapper.writeValueAsString(failedEvent)
        );

        when(outboxEventRepository.findPendingEvents()).thenReturn(List.of(entity1, entity2));

        outboxPublisher.publishPendingEvents();

        verify(tryOnEventPublisherPort, times(2)).publishJobEvent(any());
        assertThat(entity1.getStatus()).isEqualTo("PUBLISHED");
        assertThat(entity2.getStatus()).isEqualTo("PUBLISHED");
    }
}
