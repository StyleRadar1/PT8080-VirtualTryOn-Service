package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.port.in.ProcessTryOnJobUseCase;
import edu.dosw.styleradar.virtualtryon.infrastructure.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada AMQP (RabbitMQ) que consume eventos tryon.job.created desde la cola
 * q.tryon.job.process para procesar de forma desacoplada y asíncrona la inferencia de IA (SR-RF-25).
 */
@Component
public class TryOnJobProcessConsumer {

    private static final Logger log = LoggerFactory.getLogger(TryOnJobProcessConsumer.class);

    private final ProcessTryOnJobUseCase processTryOnJobUseCase;

    public TryOnJobProcessConsumer(ProcessTryOnJobUseCase processTryOnJobUseCase) {
        this.processTryOnJobUseCase = processTryOnJobUseCase;
    }

    @RabbitListener(
            queues = "${styleradar.tryon.amqp.process-queue:" + RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE + "}",
            autoStartup = "${styleradar.tryon.amqp.listeners-enabled:true}"
    )
    public void onTryOnJobCreated(TryOnJobDomainEvent event) {
        if (event == null) {
            log.warn("Evento tryon.job.created descartado por ser nulo");
            return;
        }
        log.info("Consumidor AMQP: procesando trabajo {} recibido de la cola {}",
                event.jobId(), RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        try {
            processTryOnJobUseCase.processJob(event.jobId());
            log.info("Consumidor AMQP: trabajo {} procesado exitosamente", event.jobId());
        } catch (Exception ex) {
            log.error("Fallo durante el procesamiento del trabajo {} en consumidor AMQP. Desviando mensaje a DLQ: {}",
                    event.jobId(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(
                    "Error al procesar prueba virtual en consumidor AMQP. Desviado a DLQ", ex
            );
        }
    }
}

