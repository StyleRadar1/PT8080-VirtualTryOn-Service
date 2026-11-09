package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.port.out.OutboxPublisherPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnEventPublisherPort;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de infraestructura que implementa el patrón Transactional Outbox (SR-RF-25).
 * Persiste eventos en outbox_events dentro de la transacción JDBC de PostgreSQL y despacha
 * asíncronamente a RabbitMQ garantizando entrega At-Least-Once y consistencia Dual-Write.
 * Utiliza reclamación exclusiva de eventos pendientes mediante SELECT ... FOR UPDATE SKIP LOCKED,
 * estado PROCESSING con lease temporal y recuperación de eventos abandonados.
 */
@Component
public class TransactionalOutboxPublisherAdapter implements OutboxPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(TransactionalOutboxPublisherAdapter.class);
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(30);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventJpaRepository outboxEventRepository;
    private final TryOnEventPublisherPort tryOnEventPublisherPort;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public TransactionalOutboxPublisherAdapter(
            OutboxEventJpaRepository outboxEventRepository,
            TryOnEventPublisherPort tryOnEventPublisherPort,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository, "outboxEventRepository no puede ser nulo");
        this.tryOnEventPublisherPort = Objects.requireNonNull(tryOnEventPublisherPort, "tryOnEventPublisherPort no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate no puede ser nulo");
    }

    @Override
    public void saveOutboxEvent(UUID aggregateId, String eventType, Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            OutboxEventJpaEntity entity = OutboxEventJpaEntity.pending(
                    UUID.randomUUID(),
                    "TRY_ON_JOB",
                    aggregateId.toString(),
                    eventType,
                    jsonPayload
            );
            outboxEventRepository.save(entity);
            log.debug("Evento outbox persistido atómicamente: {} ({})", entity.getId(), eventType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Error al serializar payload JSON para outbox: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${styleradar.tryon.outbox.poll-interval-ms:5000}")
    public void publishPendingEvents() {
        // 1. Reclamación exclusiva concurrente con SELECT ... FOR UPDATE SKIP LOCKED
        List<OutboxEventJpaEntity> claimedEvents = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<OutboxEventJpaEntity> claimable = outboxEventRepository.findClaimableEventsForUpdate(
                    now,
                    PageRequest.of(0, BATCH_SIZE)
            );
            if (claimable.isEmpty()) {
                return List.of();
            }
            for (OutboxEventJpaEntity event : claimable) {
                event.claim(now, DEFAULT_LEASE_DURATION);
            }
            return outboxEventRepository.saveAll(claimable);
        });

        if (claimedEvents == null || claimedEvents.isEmpty()) {
            return;
        }

        // 2. Despacho individual y confirmación con broker RabbitMQ
        for (OutboxEventJpaEntity event : claimedEvents) {
            try {
                if ("tryon.job.created".equals(event.getEventType())
                        || "tryon.job.completed".equals(event.getEventType())
                        || "tryon.job.failed".equals(event.getEventType())) {
                    TryOnJobDomainEvent domainEvent = objectMapper.readValue(event.getPayload(), TryOnJobDomainEvent.class);
                    tryOnEventPublisherPort.publishJobEvent(domainEvent);
                } else if ("tryon.photo.purged".equals(event.getEventType())) {
                    edu.dosw.styleradar.virtualtryon.domain.event.PhotoPurgedDomainEvent domainEvent =
                            objectMapper.readValue(event.getPayload(), edu.dosw.styleradar.virtualtryon.domain.event.PhotoPurgedDomainEvent.class);
                    tryOnEventPublisherPort.publishPhotoPurgedEvent(domainEvent);
                }

                // Confirmación exitosa en broker: marcar como PUBLISHED en base de datos
                transactionTemplate.execute(status -> {
                    outboxEventRepository.findById(event.getId()).ifPresent(fresh -> {
                        fresh.markPublished();
                        outboxEventRepository.save(fresh);
                    });
                    return null;
                });
                log.info("Evento outbox despachado y confirmado en broker: {} ({})", event.getId(), event.getEventType());
            } catch (Exception ex) {
                log.warn("Fallo temporal al despachar evento outbox {}: {}. Retornando a PENDING para reintento",
                        event.getId(), ex.getMessage());
                // Fallo en broker o de red: retornar a PENDING, registrar fallo e incrementar reintentos
                transactionTemplate.execute(status -> {
                    outboxEventRepository.findById(event.getId()).ifPresent(fresh -> {
                        fresh.recordFailure(ex.getMessage());
                        outboxEventRepository.save(fresh);
                    });
                    return null;
                });
            }
        }
    }
}
