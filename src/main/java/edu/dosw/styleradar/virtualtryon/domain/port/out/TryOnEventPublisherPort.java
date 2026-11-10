package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.event.PhotoPurgedDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;

/**
 * Puerto de salida (Driven Port) para la publicación asíncrona de eventos de dominio
 * hacia el broker RabbitMQ (SR-RF-25 y SR-RF-27).
 */
public interface TryOnEventPublisherPort {

    void publishJobEvent(TryOnJobDomainEvent event);

    default void publishPhotoPurgedEvent(PhotoPurgedDomainEvent event) {}
}

