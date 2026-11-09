package edu.dosw.styleradar.virtualtryon.domain.port.out;

import java.util.UUID;

/**
 * Puerto de salida para persistir y despachar eventos transaccionales mediante el patrón Transactional Outbox (SR-RF-25).
 */
public interface OutboxPublisherPort {

    void saveOutboxEvent(UUID aggregateId, String eventType, Object payload);

    void publishPendingEvents();
}

