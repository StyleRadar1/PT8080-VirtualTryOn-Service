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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de infraestructura que implementa el patrón Transactional Outbox (SR-RF-25).
 * Persiste eventos en outbox_events dentro de la transacción JDBC de PostgreSQL y despacha
 * asíncronamente a RabbitMQ garantizando entrega At-Least-Once y consistencia Dual-Write.
 */
@Component
public class TransactionalOutboxPublisherAdapter implements OutboxPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(TransactionalOutboxPublisherAdapter.class);

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
        List<OutboxEventJpaEntity> pendingEvents = outboxEventRepository.findPendingEvents();
        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEventJpaEntity event : pendingEvents) {
            try {
                if ("tryon.job.created".equals(event.getEventType())
                        || "tryon.job.completed".equals(event.getEventType())
                        || "tryon.job.failed".equals(event.getEventType())) {
                    TryOnJobDomainEvent domainEvent = objectMapper.readValue(event.getPayload(), TryOnJobDomainEvent.class);
                    tryOnEventPublisherPort.publishJobEvent(domainEvent);
                }
                transactionTemplate.execute(status -> {
                    event.markPublished();
                    outboxEventRepository.save(event);
                    return null;
                });
                log.info("Evento outbox despachado y confirmado en broker: {} ({})", event.getId(), event.getEventType());
            } catch (Exception ex) {
                log.warn("Fallo temporal al despachar evento outbox {}: {}", event.getId(), ex.getMessage());
                transactionTemplate.execute(status -> {
                    event.recordFailure(ex.getMessage());
                    outboxEventRepository.save(event);
                    return null;
                });
            }
        }
    }
}

