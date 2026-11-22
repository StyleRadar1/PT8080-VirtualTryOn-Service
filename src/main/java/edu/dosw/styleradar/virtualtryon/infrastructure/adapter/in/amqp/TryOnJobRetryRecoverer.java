package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.event.UserAccountDeletedEvent;
import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.interceptor.MethodInvocationRecoverer;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Finaliza el job sólo después de agotar la política de reintentos AMQP y audita eventos críticos enviados a DLQ. */
@Component
public class TryOnJobRetryRecoverer implements MethodInvocationRecoverer<Object> {

    private static final Logger log = LoggerFactory.getLogger(TryOnJobRetryRecoverer.class);
    private final ProcessTryOnJobService processService;
    private final ObjectMapper objectMapper;

    @Autowired
    public TryOnJobRetryRecoverer(
            ProcessTryOnJobService processService,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.processService = processService;
        this.objectMapper = objectMapper != null
                ? objectMapper
                : new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public TryOnJobRetryRecoverer(ProcessTryOnJobService processService) {
        this(processService, null);
    }

    @Override
    public Object recover(Object[] args, Throwable cause) {
        Throwable effectiveCause = unwrapListenerException(cause);
        if (effectiveCause instanceof AmqpRejectAndDontRequeueException permanentRejection) {
            throw permanentRejection;
        }
        String causeName = effectiveCause != null ? effectiveCause.getClass().getSimpleName() : "UnknownException";
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof TryOnJobDomainEvent event && event.jobId() != null) {
                    processService.markFailedAfterRetries(event.jobId(), causeName);
                    break;
                }
                if (arg instanceof UserAccountDeletedEvent deletedEvent) {
                    log.error("ALERTA LEY 1581: Reintentos AMQP agotados al purgar datos del usuario {}. Redirigiendo a DLQ por {}",
                            deletedEvent.userId(), causeName, effectiveCause);
                    break;
                }
                if (arg instanceof CatalogGarmentUpdatedEvent catalogEvent) {
                    log.error("Reintentos AMQP agotados al sincronizar prenda {}. Redirigiendo a DLQ por {}",
                            catalogEvent.garmentId(), causeName, effectiveCause);
                    break;
                }
                if (arg instanceof Message amqpMessage) {
                    handleAmqpMessageRecovery(amqpMessage, causeName, effectiveCause);
                    break;
                }
            }
        }
        throw new AmqpRejectAndDontRequeueException("Reintentos AMQP agotados; mensaje enviado a DLQ", effectiveCause);
    }

    private static Throwable unwrapListenerException(Throwable cause) {
        Throwable current = cause;
        while (current instanceof ListenerExecutionFailedException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void handleAmqpMessageRecovery(Message amqpMessage, String causeName, Throwable cause) {
        byte[] body = amqpMessage.getBody();
        if (body == null || body.length == 0) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isObject()) {
                return;
            }
            if (root.hasNonNull("jobId")) {
                UUID jobId = UUID.fromString(root.get("jobId").asText());
                processService.markFailedAfterRetries(jobId, causeName);
                return;
            }
            if (root.hasNonNull("garmentId")) {
                log.error("Reintentos AMQP agotados al sincronizar prenda {}. Redirigiendo a DLQ por {}",
                        root.get("garmentId").asText(), causeName, cause);
                return;
            }
            String userRef = root.hasNonNull("userId")
                    ? root.get("userId").asText()
                    : (root.hasNonNull("usuarioId") ? root.get("usuarioId").asText() : null);
            if (userRef != null) {
                log.error("ALERTA LEY 1581: Reintentos AMQP agotados al purgar datos del usuario {}. Redirigiendo a DLQ por {}",
                        userRef, causeName, cause);
            }
        } catch (Exception ex) {
            log.warn("No fue posible inspeccionar el payload JSON del mensaje AMQP fallido en recoverer ({}): {}",
                    causeName, ex.getMessage());
        }
    }
}
