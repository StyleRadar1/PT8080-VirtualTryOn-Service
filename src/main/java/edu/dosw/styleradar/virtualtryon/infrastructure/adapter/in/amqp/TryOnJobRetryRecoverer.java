package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.event.UserAccountDeletedEvent;
import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.retry.interceptor.MethodInvocationRecoverer;
import org.springframework.stereotype.Component;

/** Finaliza el job sólo después de agotar la política de reintentos AMQP y audita eventos críticos enviados a DLQ. */
@Component
public class TryOnJobRetryRecoverer implements MethodInvocationRecoverer<Object> {

    private static final Logger log = LoggerFactory.getLogger(TryOnJobRetryRecoverer.class);
    private final ProcessTryOnJobService processService;

    public TryOnJobRetryRecoverer(ProcessTryOnJobService processService) {
        this.processService = processService;
    }

    @Override
    public Object recover(Object[] args, Throwable cause) {
        String causeName = cause != null ? cause.getClass().getSimpleName() : "UnknownException";
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof TryOnJobDomainEvent event && event.jobId() != null) {
                    processService.markFailedAfterRetries(event.jobId(), causeName);
                    break;
                }
                if (arg instanceof UserAccountDeletedEvent deletedEvent) {
                    log.error("ALERTA LEY 1581: Reintentos AMQP agotados al purgar datos del usuario {}. Redirigiendo a DLQ por {}",
                            deletedEvent.userId(), causeName, cause);
                    break;
                }
                if (arg instanceof CatalogGarmentUpdatedEvent catalogEvent) {
                    log.error("Reintentos AMQP agotados al sincronizar prenda {}. Redirigiendo a DLQ por {}",
                            catalogEvent.garmentId(), causeName, cause);
                    break;
                }
            }
        }
        throw new AmqpRejectAndDontRequeueException("Reintentos AMQP agotados; mensaje enviado a DLQ", cause);
    }
}
