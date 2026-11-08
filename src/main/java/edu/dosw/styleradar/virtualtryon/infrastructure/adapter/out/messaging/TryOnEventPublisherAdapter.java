package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.messaging;

import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnEventPublisherPort;
import edu.dosw.styleradar.virtualtryon.infrastructure.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida de mensajería AMQP que publica los eventos de dominio
 * (tryon.job.created, tryon.job.completed, tryon.job.failed) en RabbitMQ (SR-RF-25).
 */
@Component
public class TryOnEventPublisherAdapter implements TryOnEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(TryOnEventPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public TryOnEventPublisherAdapter(
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            @Value("${styleradar.tryon.amqp.exchange:" + RabbitConfig.DEFAULT_TRYON_EXCHANGE + "}") String exchangeName
    ) {
        this.rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        this.exchangeName = exchangeName;
    }

    @Override
    public void publishJobEvent(TryOnJobDomainEvent event) {
        if (event == null) {
            return;
        }
        String routingKey = event.routingKey();
        if (rabbitTemplate == null) {
            log.info("Broker RabbitMQ no activo en este perfil; evento {} registrado localmente para job {}",
                    routingKey, event.jobId());
            return;
        }
        try {
            rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
            log.info("Evento AMQP {} publicado en exchange {} para el trabajo {}",
                    routingKey, exchangeName, event.jobId());
        } catch (RuntimeException ex) {
            log.warn("No fue posible publicar el evento {} en RabbitMQ para el trabajo {}: {}",
                    routingKey, event.jobId(), ex.getMessage());
        }
    }
}

