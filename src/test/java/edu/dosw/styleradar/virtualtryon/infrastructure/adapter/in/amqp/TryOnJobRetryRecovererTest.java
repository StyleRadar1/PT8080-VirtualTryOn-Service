package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TryOnJobRetryRecovererTest {
    @Test
    void marksJobFailedOnlyWhenRetriesAreExhaustedThenRejectsToDlq() {
        ProcessTryOnJobService service = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(service);
        UUID jobId = UUID.randomUUID();
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(UUID.randomUUID(), jobId,
                UUID.randomUUID(), UUID.randomUUID(), JobStatus.PROCESSING, null, null, Instant.now());

        assertThatThrownBy(() -> recoverer.recover(new Object[]{event}, new IllegalStateException("network")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(service).markFailedAfterRetries(jobId, "IllegalStateException");
    }

    @Test
    void shouldThrowAmqpRejectWhenArgsIsNull() {
        ProcessTryOnJobService service = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(service);
        assertThatThrownBy(() -> recoverer.recover(null, new RuntimeException("err")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void shouldIgnoreNonDomainEventOrNullJobIdInArgs() {
        ProcessTryOnJobService service = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(service);
        assertThatThrownBy(() -> recoverer.recover(new Object[]{"not-an-event", null}, new RuntimeException("err")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
