package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.messaging;

import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.infrastructure.config.RabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para TryOnEventPublisherAdapter (SR-RF-25).
 * Valida validación de existencia de RabbitTemplate, publicación con CorrelationData,
 * verificación explícita de ACK en Publisher Confirms y detección de NACK o fallos del broker.
 */
@ExtendWith(MockitoExtension.class)
class TryOnEventPublisherAdapterTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    private TryOnEventPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(rabbitTemplate);
        adapter = new TryOnEventPublisherAdapter(
                rabbitTemplateProvider,
                RabbitConfig.DEFAULT_TRYON_EXCHANGE,
                false
        );
    }

    @Test
    @DisplayName("HU SR-RF-25: publishJobEvent no realiza acciones si el evento es nulo")
    void shouldDoNothingWhenEventIsNull() {
        adapter.publishJobEvent(null);
        adapter.publishPhotoPurgedEvent(null);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(TryOnJobDomainEvent.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("HU SR-RF-27: publishPhotoPurgedEvent publica evento tryon.photo.purged con CorrelationData en el exchange")
    void shouldPublishPhotoPurgedEventSuccessfully() {
        edu.dosw.styleradar.virtualtryon.domain.event.PhotoPurgedDomainEvent event =
                edu.dosw.styleradar.virtualtryon.domain.event.PhotoPurgedDomainEvent.of(UUID.randomUUID(), UUID.randomUUID());

        adapter.publishPhotoPurgedEvent(event);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.DEFAULT_TRYON_EXCHANGE),
                eq("tryon.photo.purged"),
                eq(event),
                any(CorrelationData.class)
        );
    }

    @Test
    @DisplayName("HU SR-RF-25: publishJobEvent lanza IllegalStateException si RabbitTemplate es null (no reporta falso éxito al Outbox)")
    void shouldThrowIllegalStateExceptionWhenRabbitTemplateIsNull() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RabbitTemplate> nullProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(nullProvider.getIfAvailable()).thenReturn(null);

        TryOnEventPublisherAdapter offlineAdapter = new TryOnEventPublisherAdapter(
                nullProvider,
                RabbitConfig.DEFAULT_TRYON_EXCHANGE,
                false
        );

        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                JobStatus.QUEUED, null, null, Instant.now()
        );

        assertThatThrownBy(() -> offlineAdapter.publishJobEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RabbitTemplate no está disponible");
    }

    @Test
    @DisplayName("HU SR-RF-25: publishJobEvent publica con CorrelationData en el exchange configurado")
    void shouldPublishEventWithCorrelationData() {
        UUID eventId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                eventId, jobId, UUID.randomUUID(), UUID.randomUUID(),
                JobStatus.QUEUED, null, null, Instant.now()
        );

        adapter.publishJobEvent(event);

        ArgumentCaptor<CorrelationData> captor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.DEFAULT_TRYON_EXCHANGE),
                eq("tryon.job.created"),
                eq(event),
                captor.capture()
        );
        assertThat(captor.getValue().getId()).isEqualTo(eventId.toString());
    }

    @Test
    @DisplayName("HU SR-RF-25: publishJobEvent con Publisher Confirms activado espera ACK y tiene éxito si el broker confirma")
    void shouldConfirmPublicationWhenPublisherConfirmsEnabledAndBrokerAcks() {
        TryOnEventPublisherAdapter confirmingAdapter = new TryOnEventPublisherAdapter(
                rabbitTemplateProvider,
                RabbitConfig.DEFAULT_TRYON_EXCHANGE,
                true // requirePublisherConfirms = true
        );

        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                JobStatus.COMPLETED, "https://render.png", null, Instant.now()
        );

        // Simular que el broker devuelve ACK completando el future
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(TryOnJobDomainEvent.class), any(CorrelationData.class));

        confirmingAdapter.publishJobEvent(event);

        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.DEFAULT_TRYON_EXCHANGE), eq("tryon.job.completed"), eq(event), any(CorrelationData.class));
    }

    @Test
    @DisplayName("HU SR-RF-25: publishJobEvent con Publisher Confirms lanza IllegalStateException si el broker devuelve NACK")
    void shouldThrowIllegalStateExceptionWhenBrokerReturnsNack() {
        TryOnEventPublisherAdapter confirmingAdapter = new TryOnEventPublisherAdapter(
                rabbitTemplateProvider,
                RabbitConfig.DEFAULT_TRYON_EXCHANGE,
                true // requirePublisherConfirms = true
        );

        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                JobStatus.FAILED, null, "Error AI", Instant.now()
        );

        // Simular que el broker devuelve NACK
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(false, "Exchange queue limit exceeded"));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(TryOnJobDomainEvent.class), any(CorrelationData.class));

        assertThatThrownBy(() -> confirmingAdapter.publishJobEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NACK");
    }

    @Test
    @DisplayName("HU SR-RF-25: publishJobEvent propaga excepción si RabbitTemplate lanza RuntimeException")
    void shouldPropagateExceptionWhenConvertAndSendThrows() {
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                JobStatus.QUEUED, null, null, Instant.now()
        );

        doThrow(new RuntimeException("RabbitMQ connection reset"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(TryOnJobDomainEvent.class), any(CorrelationData.class));

        assertThatThrownBy(() -> adapter.publishJobEvent(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("connection reset");
    }
}
