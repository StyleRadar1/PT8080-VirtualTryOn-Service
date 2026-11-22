package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.event.UserAccountDeletedEvent;
import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Test
    void shouldRejectUserAccountDeletedAndCatalogEventsToDlqSafelyIncludingNullCause() {
        ProcessTryOnJobService service = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(service);

        UserAccountDeletedEvent deletedEvent = new UserAccountDeletedEvent(UUID.randomUUID(), Instant.now());
        assertThatThrownBy(() -> recoverer.recover(new Object[]{deletedEvent}, new IllegalStateException("db down")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        CatalogGarmentUpdatedEvent catalogEvent = new CatalogGarmentUpdatedEvent(
                UUID.randomUUID(), "Jacket", "TOP", "https://img", true, Instant.now()
        );
        assertThatThrownBy(() -> recoverer.recover(new Object[]{catalogEvent}, null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(service);
    }

    @Test
    void recoversAmqpMessageWithJobIdAndUnwrapsListenerExecutionFailedException() {
        ProcessTryOnJobService service = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(service, new ObjectMapper());
        UUID jobId = UUID.randomUUID();
        Message amqpMessage = new Message(
                ("{\"jobId\":\"" + jobId + "\",\"status\":\"QUEUED\"}").getBytes(StandardCharsets.UTF_8),
                new MessageProperties()
        );
        ListenerExecutionFailedException wrapped = new ListenerExecutionFailedException(
                "Listener failed",
                new RuntimeException("FASHN AI timeout"),
                amqpMessage
        );

        assertThatThrownBy(() -> recoverer.recover(new Object[]{"channel", amqpMessage}, wrapped))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(service).markFailedAfterRetries(jobId, "RuntimeException");
    }

    @Test
    void rethrowsPermanentRejectionWithoutMarkingJobFailed() {
        ProcessTryOnJobService service = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(service);
        UUID jobId = UUID.randomUUID();
        Message amqpMessage = new Message(
                ("{\"jobId\":\"" + jobId + "\"}").getBytes(StandardCharsets.UTF_8),
                new MessageProperties()
        );
        ListenerExecutionFailedException wrapped = new ListenerExecutionFailedException(
                "Permanent error",
                new AmqpRejectAndDontRequeueException("Desviado a DLQ", new JobNotFoundException(jobId)),
                amqpMessage
        );

        assertThatThrownBy(() -> recoverer.recover(new Object[]{amqpMessage}, wrapped))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Desviado a DLQ");

        verifyNoInteractions(service);
    }

    @Test
    void handlesNonJobJsonEmptyArrayAndMalformedAmqpMessagesSafely() {
        ProcessTryOnJobService service = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(service);
        UUID garmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Message garmentMsg = new Message(("{\"garmentId\":\"" + garmentId + "\"}").getBytes(StandardCharsets.UTF_8), new MessageProperties());
        Message userMsg = new Message(("{\"userId\":\"" + userId + "\"}").getBytes(StandardCharsets.UTF_8), new MessageProperties());
        Message usuarioMsg = new Message("{\"usuarioId\":42}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        Message noIdsMsg = new Message("{\"other\":\"value\"}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        Message emptyMsg = new Message(new byte[0], new MessageProperties());
        Message arrayMsg = new Message("[1,2,3]".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        Message malformedMsg = new Message("{bad-json".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        for (Message msg : new Message[]{garmentMsg, userMsg, usuarioMsg, noIdsMsg, emptyMsg, arrayMsg, malformedMsg}) {
            assertThatThrownBy(() -> recoverer.recover(new Object[]{msg}, new RuntimeException("transient")))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }

        verifyNoInteractions(service);
    }
}
