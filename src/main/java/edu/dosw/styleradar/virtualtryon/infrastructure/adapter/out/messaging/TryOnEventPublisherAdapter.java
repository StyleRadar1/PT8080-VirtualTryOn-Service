package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.messaging;

import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnEventPublisherPort;
import edu.dosw.styleradar.virtualtryon.infrastructure.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Adaptador de salida de mensajería AMQP que publica los eventos de dominio
 * (tryon.job.created, tryon.job.completed, tryon.job.failed) en RabbitMQ (SR-RF-25).
 * Valida la existencia de RabbitTemplate y confirma de manera concluyente la entrega
 * mediante confirmaciones del broker (Publisher Confirms / ACK).
 */
@Component
public class TryOnEventPublisherAdapter implements TryOnEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(TryOnEventPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final boolean requirePublisherConfirms;

    @Autowired
    public TryOnEventPublisherAdapter(
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            @Value("${styleradar.tryon.amqp.exchange:" + RabbitConfig.DEFAULT_TRYON_EXCHANGE + "}") String exchangeName,
            @Value("${styleradar.tryon.amqp.publisher-confirms:false}") boolean requirePublisherConfirms
    ) {
        this.rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        this.exchangeName = exchangeName;
        this.requirePublisherConfirms = requirePublisherConfirms;
    }

    public TryOnEventPublisherAdapter(
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            String exchangeName
    ) {
        this(rabbitTemplateProvider, exchangeName, false);
    }

    @Override
    public void publishJobEvent(TryOnJobDomainEvent event) {
        if (event == null) {
            return;
        }
        String routingKey = event.routingKey();
        if (rabbitTemplate == null) {
            throw new IllegalStateException(
                    "RabbitTemplate no está disponible en este entorno. No es posible publicar ni confirmar el evento " + routingKey
            );
        }

        try {
            CorrelationData correlationData = new CorrelationData(
                    event.eventId() != null ? event.eventId().toString() : UUID.randomUUID().toString()
            );
            rabbitTemplate.convertAndSend(exchangeName, routingKey, event, correlationData);

            // Verificación concluyente de confirmación del broker (Publisher Confirms)
            CompletableFuture<CorrelationData.Confirm> future = correlationData.getFuture();
            if (requirePublisherConfirms && future != null) {
                try {
                    CorrelationData.Confirm confirm = future.get(5, TimeUnit.SECONDS);
                    if (confirm == null || !confirm.isAck()) {
                        String reason = confirm != null ? confirm.getReason() : "timeout";
                        throw new IllegalStateException("RabbitMQ rechazó con NACK la entrega del evento " + routingKey + ": " + reason);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupción esperando confirmación de RabbitMQ para evento " + routingKey, e);
                } catch (ExecutionException | TimeoutException e) {
                    throw new IllegalStateException("Timeout o error esperando confirmación (ACK) de RabbitMQ para evento " + routingKey, e);
                }
            } else if (future != null && future.isDone()) {
                CorrelationData.Confirm confirm = future.getNow(null);
                if (confirm != null && !confirm.isAck()) {
                    throw new IllegalStateException("RabbitMQ devolvió NACK para el evento " + routingKey + ": " + confirm.getReason());
                }
            }

            log.info("Evento AMQP {} publicado y confirmado en exchange {} para el trabajo {}",
                    routingKey, exchangeName, event.jobId());
        } catch (RuntimeException ex) {
            log.error("Fallo al publicar o confirmar evento {} en RabbitMQ para el trabajo {}: {}",
                    routingKey, event.jobId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
