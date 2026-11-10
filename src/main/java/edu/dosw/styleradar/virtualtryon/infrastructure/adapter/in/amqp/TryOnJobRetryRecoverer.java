package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.retry.interceptor.MethodInvocationRecoverer;
import org.springframework.stereotype.Component;

/** Finaliza el job sólo después de agotar la política de reintentos AMQP. */
@Component
public class TryOnJobRetryRecoverer implements MethodInvocationRecoverer<Object> {
    private final ProcessTryOnJobService processService;

    public TryOnJobRetryRecoverer(ProcessTryOnJobService processService) {
        this.processService = processService;
    }

    @Override
    public Object recover(Object[] args, Throwable cause) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof TryOnJobDomainEvent event && event.jobId() != null) {
                    processService.markFailedAfterRetries(event.jobId(), cause.getClass().getSimpleName());
                    break;
                }
            }
        }
        throw new AmqpRejectAndDontRequeueException("Reintentos AMQP agotados; mensaje enviado a DLQ", cause);
    }
}
