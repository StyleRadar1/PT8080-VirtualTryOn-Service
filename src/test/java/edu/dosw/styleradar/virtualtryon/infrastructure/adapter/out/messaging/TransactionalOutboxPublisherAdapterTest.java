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
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * Valida persistencia atómica, reclamación concurrente con bloqueo pesimista y SKIP LOCKED,
 * estado PROCESSING con lease temporal, recuperación de eventos abandonados y tolerancia a fallos del broker.
 */
@ExtendWith(MockitoExtension.class)
class TransactionalOutboxPublisherAdapterTest {

    @Mock
    private OutboxEventJpaRepository outboxEventRepository;

    @Mock
    private TryOnEventPublisherPort tryOnEventPublisherPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort blobStoragePort;

    private ObjectMapper objectMapper;
    private TransactionalOutboxPublisherAdapter outboxPublisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        lenient().when(outboxEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxPublisher = new TransactionalOutboxPublisherAdapter(
                outboxEventRepository,
                tryOnEventPublisherPort,
                objectMapper,
                transactionTemplate,
                blobStoragePort
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
    @DisplayName("HU SR-RF-25: Reclamación concurrente con SKIP LOCKED cambia estado a PROCESSING con lease y luego PUBLISHED tras confirmar broker")
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

        when(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(pendingEntity));
        when(outboxEventRepository.findById(pendingEntity.getId()))
                .thenReturn(Optional.of(pendingEntity));

        outboxPublisher.publishPendingEvents();

        verify(tryOnEventPublisherPort).publishJobEvent(any(TryOnJobDomainEvent.class));
        assertThat(pendingEntity.getStatus()).isEqualTo("PUBLISHED");
        assertThat(pendingEntity.getPublishedAt()).isNotNull();
        assertThat(pendingEntity.getClaimExpiresAt()).isNull();
    }

    @Test
    @DisplayName("HU SR-RF-27: Despacha evento tryon.photo.purged mediante publishPhotoPurgedEvent y confirma como PUBLISHED")
    void shouldPublishPhotoPurgedEventFromOutbox() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        edu.dosw.styleradar.virtualtryon.domain.event.PhotoPurgedDomainEvent purgeEvent =
                edu.dosw.styleradar.virtualtryon.domain.event.PhotoPurgedDomainEvent.of(jobId, userId);
        String jsonPayload = objectMapper.writeValueAsString(purgeEvent);

        OutboxEventJpaEntity pendingEntity = OutboxEventJpaEntity.pending(
                UUID.randomUUID(), "TRY_ON_JOB", jobId.toString(), "tryon.photo.purged", jsonPayload
        );

        when(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(pendingEntity));
        when(outboxEventRepository.findById(pendingEntity.getId()))
                .thenReturn(Optional.of(pendingEntity));

        outboxPublisher.publishPendingEvents();

        verify(tryOnEventPublisherPort).publishPhotoPurgedEvent(any(edu.dosw.styleradar.virtualtryon.domain.event.PhotoPurgedDomainEvent.class));
        assertThat(pendingEntity.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("HU SR-RF-25: Evento en PROCESSING con lease expirado tras caída de worker es recuperado y publicado")
    void shouldRecoverAbandonedProcessingEventAfterLeaseExpiration() throws Exception {
        UUID jobId = UUID.randomUUID();
        TryOnJobDomainEvent domainEvent = new TryOnJobDomainEvent(
                UUID.randomUUID(), jobId, UUID.randomUUID(), UUID.randomUUID(), JobStatus.COMPLETED, "https://render.png", null, Instant.now()
        );
        String jsonPayload = objectMapper.writeValueAsString(domainEvent);

        OutboxEventJpaEntity abandonedEntity = new OutboxEventJpaEntity(
                UUID.randomUUID(),
                "TRY_ON_JOB",
                jobId.toString(),
                "tryon.job.completed",
                jsonPayload,
                "PROCESSING",
                0,
                Instant.now().minus(Duration.ofMinutes(5)),
                null,
                Instant.now().minus(Duration.ofMinutes(5)),
                Instant.now().minus(Duration.ofMinutes(4)), // Expirado hace 4 minutos
                null
        );

        when(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(abandonedEntity));
        when(outboxEventRepository.findById(abandonedEntity.getId()))
                .thenReturn(Optional.of(abandonedEntity));

        outboxPublisher.publishPendingEvents();

        verify(tryOnEventPublisherPort).publishJobEvent(any(TryOnJobDomainEvent.class));
        assertThat(abandonedEntity.getStatus()).isEqualTo("PUBLISHED");
        assertThat(abandonedEntity.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("HU SR-RF-25: Registra fallo, retorna a PENDING e incrementa retry_count si el broker AMQP falla")
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

        when(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(pendingEntity));
        when(outboxEventRepository.findById(pendingEntity.getId()))
                .thenReturn(Optional.of(pendingEntity));
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(tryOnEventPublisherPort).publishJobEvent(any(TryOnJobDomainEvent.class));

        outboxPublisher.publishPendingEvents();

        assertThat(pendingEntity.getStatus()).isEqualTo("PENDING");
        assertThat(pendingEntity.getRetryCount()).isEqualTo(1);
        assertThat(pendingEntity.getErrorMessage()).contains("RabbitMQ connection refused");
        assertThat(pendingEntity.getClaimExpiresAt()).isNull();
    }

    @Test
    @DisplayName("HU SR-RF-25: No realiza operaciones si no hay eventos pendientes o reclamables")
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());
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

        when(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(entity1, entity2));
        when(outboxEventRepository.findById(entity1.getId())).thenReturn(Optional.of(entity1));
        when(outboxEventRepository.findById(entity2.getId())).thenReturn(Optional.of(entity2));

        outboxPublisher.publishPendingEvents();

        verify(tryOnEventPublisherPort, times(2)).publishJobEvent(any());
        assertThat(entity1.getStatus()).isEqualTo("PUBLISHED");
        assertThat(entity2.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("HU SR-RF-27: Despacha evento tryon.blob.cleanup ejecutando deleteBlob y marcando PUBLISHED")
    void shouldExecuteBlobCleanupWhenOutboxEventIsTryonBlobCleanup() throws Exception {
        String blobUrl = "https://storage.blob.core.windows.net/renders/pending-render.png";
        OutboxEventJpaEntity entity = OutboxEventJpaEntity.pending(
                UUID.randomUUID(), "TRY_ON_JOB", UUID.randomUUID().toString(), "tryon.blob.cleanup", objectMapper.writeValueAsString(blobUrl)
        );

        when(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(entity));
        when(outboxEventRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        outboxPublisher.publishPendingEvents();

        verify(blobStoragePort).deleteBlob(blobUrl);
        assertThat(entity.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("HU SR-RF-27: Falla y mantiene evento en PENDING con reintento si blobStoragePort es null al procesar tryon.blob.cleanup")
    void shouldFailAndKeepPendingWhenBlobStoragePortIsNullForBlobCleanup() throws Exception {
        TransactionalOutboxPublisherAdapter adapterWithoutStorage = new TransactionalOutboxPublisherAdapter(
                outboxEventRepository,
                tryOnEventPublisherPort,
                objectMapper,
                transactionTemplate,
                null
        );

        String blobUrl = "https://storage.blob.core.windows.net/renders/pending-render.png";
        OutboxEventJpaEntity entity = OutboxEventJpaEntity.pending(
                UUID.randomUUID(), "TRY_ON_JOB", UUID.randomUUID().toString(), "tryon.blob.cleanup", objectMapper.writeValueAsString(blobUrl)
        );

        when(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(entity));
        when(outboxEventRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        adapterWithoutStorage.publishPendingEvents();

        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getRetryCount()).isEqualTo(1);
        assertThat(entity.getErrorMessage()).contains("BlobStoragePort no está disponible");
    }
}
