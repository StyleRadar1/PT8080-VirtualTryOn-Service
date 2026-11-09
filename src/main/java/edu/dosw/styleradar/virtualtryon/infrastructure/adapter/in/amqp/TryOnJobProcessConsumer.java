package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
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
 * Diseñado de forma idempotente con protección de transiciones de estado en base de datos.
 * Desvía errores permanentes o fatales a la cola de mensajes muertos (DLQ).
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
        if (event == null || event.jobId() == null) {
            log.warn("Evento tryon.job.created descartado por ser nulo o no contener identificador de trabajo");
            return;
        }
        log.info("Consumidor AMQP: procesando trabajo {} recibido de la cola {}",
                event.jobId(), RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        try {
            processTryOnJobUseCase.processJob(event.jobId());
            log.info("Consumidor AMQP: trabajo {} procesado exitosamente o descartado de forma idempotente", event.jobId());
        } catch (JobNotFoundException | ConsentRequiredException | InvalidPhotoException | IllegalArgumentException ex) {
            log.error("Error permanente/no-reintentable para trabajo {} en consumidor AMQP. Desviando mensaje a DLQ: {}",
                    event.jobId(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(
                    "Error definitivo al procesar prueba virtual en consumidor AMQP. Desviado a DLQ", ex
            );
        } catch (RuntimeException ex) {
            log.warn("Fallo transitorio para trabajo {}; AMQP aplicará reintento con backoff: {}",
                    event.jobId(), ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
