package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.exception.AiInferencePermanentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp.TryOnJobRetryRecoverer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.retry.RetryContext;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pruebas unitarias de configuración para el broker RabbitMQ y DLQ (SR-RF-24).
 */
class RabbitConfigTest {

    @Test
    @DisplayName("HU SR-RF-24: Dado RabbitConfig, Cuando se inicializan beans de AMQP, Entonces declara exchange, cola con DLQ, bindings y conversor JSON correctamente")
    void shouldConfigureRabbitMqBeansCorrectly() {
        RabbitConfig config = new RabbitConfig();
        ObjectMapper objectMapper = new ObjectMapper();

        TopicExchange exchange = config.catalogEventsExchange("test.catalog.events");
        org.springframework.amqp.core.DirectExchange dlx = config.catalogDeadLetterExchange("test.catalog.dlx");
        Queue dlq = config.catalogGarmentUpdatedDlq("test.catalog.garment.dlq");
        Binding dlqBinding = config.catalogGarmentUpdatedDlqBinding(dlq, dlx, "test.catalog.dlq.routing.key");
        Queue queue = config.catalogGarmentUpdatedQueue("test.catalog.garment.queue", "test.catalog.dlx", "test.catalog.dlq.routing.key");
        Binding binding = config.catalogGarmentUpdatedBinding(queue, exchange, "test.catalog.routing.key");
        MessageConverter converter = config.jackson2JsonMessageConverter(objectMapper);

        assertThat(exchange).isNotNull();
        assertThat(exchange.getName()).isEqualTo("test.catalog.events");
        assertThat(exchange.isDurable()).isTrue();

        assertThat(dlx).isNotNull();
        assertThat(dlx.getName()).isEqualTo("test.catalog.dlx");
        assertThat(dlx.isDurable()).isTrue();

        assertThat(dlq).isNotNull();
        assertThat(dlq.getName()).isEqualTo("test.catalog.garment.dlq");
        assertThat(dlq.isDurable()).isTrue();

        assertThat(dlqBinding).isNotNull();
        assertThat(dlqBinding.getDestination()).isEqualTo("test.catalog.garment.dlq");
        assertThat(dlqBinding.getExchange()).isEqualTo("test.catalog.dlx");
        assertThat(dlqBinding.getRoutingKey()).isEqualTo("test.catalog.dlq.routing.key");

        assertThat(queue).isNotNull();
        assertThat(queue.getName()).isEqualTo("test.catalog.garment.queue");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "test.catalog.dlx");
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-routing-key", "test.catalog.dlq.routing.key");

        assertThat(binding).isNotNull();
        assertThat(binding.getDestination()).isEqualTo("test.catalog.garment.queue");
        assertThat(binding.getExchange()).isEqualTo("test.catalog.events");
        assertThat(binding.getRoutingKey()).isEqualTo("test.catalog.routing.key");

        TopicExchange tryOnExchange = config.tryOnEventsExchange("test.tryon.events");
        DirectExchange tryOnDlx = config.tryOnDeadLetterExchange("test.tryon.dlx");
        Queue tryOnDlq = config.tryOnJobProcessDlq("test.tryon.process.dlq");
        Binding tryOnDlqBinding = config.tryOnJobProcessDlqBinding(tryOnDlq, tryOnDlx, "test.tryon.process.dead");
        Queue tryOnQueue = config.tryOnJobProcessQueue("test.tryon.process.queue", "test.tryon.dlx", "test.tryon.process.dead");
        Binding tryOnBinding = config.tryOnJobProcessBinding(tryOnQueue, tryOnExchange, "styleradar.tryon.job.created");

        assertThat(RabbitConfig.DEFAULT_TRYON_DLX).isEqualTo("styleradar.dlx");
        assertThat(tryOnExchange.getName()).isEqualTo("test.tryon.events");
        assertThat(tryOnDlx.getName()).isEqualTo("test.tryon.dlx");
        assertThat(tryOnDlx.isDurable()).isTrue();
        assertThat(tryOnDlx.getType()).isEqualTo("direct");
        assertThat(tryOnDlq.getName()).isEqualTo("test.tryon.process.dlq");
        assertThat(tryOnDlqBinding.getDestination()).isEqualTo("test.tryon.process.dlq");
        assertThat(tryOnQueue.getName()).isEqualTo("test.tryon.process.queue");
        assertThat(tryOnQueue.getArguments()).containsEntry("x-dead-letter-exchange", "test.tryon.dlx");
        assertThat(tryOnBinding.getDestination()).isEqualTo("test.tryon.process.queue");
        assertThat(tryOnBinding.getRoutingKey()).isEqualTo("styleradar.tryon.job.created");

        TopicExchange userExchange = config.userEventsExchange("test.user.events");
        Queue userDeletedDlq = config.userAccountDeletedDlq("test.user.deleted.dlq");
        Binding userDeletedDlqBinding = config.userAccountDeletedDlqBinding(userDeletedDlq, dlx, "test.user.deleted.dead");
        Queue userDeletedQueue = config.userAccountDeletedQueue("test.user.deleted.queue", "test.catalog.dlx", "test.user.deleted.dead");
        Binding userDeletedBinding = config.userAccountDeletedBinding(userDeletedQueue, userExchange, "styleradar.usuarios.cuenta.eliminada");

        assertThat(userExchange.getName()).isEqualTo("test.user.events");
        assertThat(userDeletedDlq.getName()).isEqualTo("test.user.deleted.dlq");
        assertThat(userDeletedDlqBinding.getRoutingKey()).isEqualTo("test.user.deleted.dead");
        assertThat(userDeletedQueue.getName()).isEqualTo("test.user.deleted.queue");
        assertThat(userDeletedQueue.getArguments()).containsEntry("x-dead-letter-exchange", "test.catalog.dlx");
        assertThat(userDeletedQueue.getArguments()).containsEntry("x-dead-letter-routing-key", "test.user.deleted.dead");
        assertThat(userDeletedBinding.getDestination()).isEqualTo("test.user.deleted.queue");
        assertThat(userDeletedBinding.getExchange()).isEqualTo("test.user.events");
        assertThat(userDeletedBinding.getRoutingKey()).isEqualTo("styleradar.usuarios.cuenta.eliminada");

        Queue withdrawnDlq = config.catalogGarmentWithdrawnDlq("test.withdrawn.dlq");
        Binding withdrawnDlqBinding = config.catalogGarmentWithdrawnDlqBinding(withdrawnDlq, dlx, "test.withdrawn.dead");
        Queue withdrawnQueue = config.catalogGarmentWithdrawnQueue("test.withdrawn.q", "test.catalog.dlx", "test.withdrawn.dead");
        Binding withdrawnBinding = config.catalogGarmentWithdrawnBinding(withdrawnQueue, exchange, "styleradar.catalogo.prenda.retirada");
        assertThat(withdrawnDlq.getName()).isEqualTo("test.withdrawn.dlq");
        assertThat(withdrawnDlqBinding.getRoutingKey()).isEqualTo("test.withdrawn.dead");
        assertThat(withdrawnBinding.getRoutingKey()).isEqualTo("styleradar.catalogo.prenda.retirada");

        Queue moderationDlq = config.catalogGarmentModerationDlq("test.moderation.dlq");
        Binding moderationDlqBinding = config.catalogGarmentModerationDlqBinding(moderationDlq, dlx, "test.moderation.dead");
        Queue moderationQueue = config.catalogGarmentModerationQueue("test.moderation.q", "test.catalog.dlx", "test.moderation.dead");
        Binding moderationBinding = config.catalogGarmentModerationBinding(moderationQueue, exchange, "styleradar.catalogo.moderacion.cambiada");
        assertThat(moderationDlq.getName()).isEqualTo("test.moderation.dlq");
        assertThat(moderationDlqBinding.getRoutingKey()).isEqualTo("test.moderation.dead");
        assertThat(moderationBinding.getRoutingKey()).isEqualTo("styleradar.catalogo.moderacion.cambiada");

        TopicExchange paymentExchange = config.paymentEventsExchange("test.payment.events");
        Queue paymentConfirmedDlq = config.paymentConfirmedDlq("test.payment.confirmed.dlq");
        Binding paymentConfirmedDlqBinding = config.paymentConfirmedDlqBinding(paymentConfirmedDlq, dlx, "test.payment.confirmed.dead");
        Queue paymentConfirmedQueue = config.paymentConfirmedQueue("test.payment.confirmed.q", "test.catalog.dlx", "test.payment.confirmed.dead");
        Binding paymentConfirmedBinding = config.paymentConfirmedBinding(paymentConfirmedQueue, paymentExchange, "styleradar.pagos.pago.confirmado");

        assertThat(paymentExchange.getName()).isEqualTo("test.payment.events");
        assertThat(paymentConfirmedDlq.getName()).isEqualTo("test.payment.confirmed.dlq");
        assertThat(paymentConfirmedDlqBinding.getRoutingKey()).isEqualTo("test.payment.confirmed.dead");
        assertThat(paymentConfirmedQueue.getName()).isEqualTo("test.payment.confirmed.q");
        assertThat(paymentConfirmedQueue.getArguments()).containsEntry("x-dead-letter-exchange", "test.catalog.dlx");
        assertThat(paymentConfirmedQueue.getArguments()).containsEntry("x-dead-letter-routing-key", "test.payment.confirmed.dead");
        assertThat(paymentConfirmedBinding.getDestination()).isEqualTo("test.payment.confirmed.q");
        assertThat(paymentConfirmedBinding.getRoutingKey()).isEqualTo("styleradar.pagos.pago.confirmado");

        Queue suspendedDlq = config.subscriptionSuspendedDlq("test.sub.suspended.dlq");
        Binding suspendedDlqBinding = config.subscriptionSuspendedDlqBinding(suspendedDlq, dlx, "test.sub.suspended.dead");
        Queue suspendedQueue = config.subscriptionSuspendedQueue("test.sub.suspended.q", "test.catalog.dlx", "test.sub.suspended.dead");
        Binding suspendedBinding = config.subscriptionSuspendedBinding(suspendedQueue, paymentExchange, "styleradar.pagos.suscripcion.suspendida");

        assertThat(suspendedDlq.getName()).isEqualTo("test.sub.suspended.dlq");
        assertThat(suspendedDlqBinding.getRoutingKey()).isEqualTo("test.sub.suspended.dead");
        assertThat(suspendedQueue.getName()).isEqualTo("test.sub.suspended.q");
        assertThat(suspendedQueue.getArguments()).containsEntry("x-dead-letter-exchange", "test.catalog.dlx");
        assertThat(suspendedQueue.getArguments()).containsEntry("x-dead-letter-routing-key", "test.sub.suspended.dead");
        assertThat(suspendedBinding.getDestination()).isEqualTo("test.sub.suspended.q");
        assertThat(suspendedBinding.getRoutingKey()).isEqualTo("styleradar.pagos.suscripcion.suspendida");

        assertThat(converter).isNotNull();

        SimpleRetryPolicy retryPolicy = RabbitConfig.buildTryOnRetryPolicy(4);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(mock(ProcessTryOnJobService.class));
        RetryOperationsInterceptor interceptor = config.tryOnRetryInterceptor(recoverer, 4, 100L, 2.0, 1000L);
        assertThat(interceptor).isNotNull();

        for (Throwable permanent : new Throwable[]{
                new AmqpRejectAndDontRequeueException("dlq"),
                new ListenerExecutionFailedException("wrapped", new AmqpRejectAndDontRequeueException("dlq", new JobNotFoundException(UUID.randomUUID()))),
                new JobNotFoundException(UUID.randomUUID()),
                new ConsentRequiredException("no consent"),
                new InvalidPhotoException("bad photo"),
                new AiInferencePermanentException("bad input"),
                new IllegalArgumentException("invalid arg")
        }) {
            RetryContext permanentCtx = retryPolicy.open(null);
            retryPolicy.registerThrowable(permanentCtx, permanent);
            assertThat(retryPolicy.canRetry(permanentCtx))
                    .as("Error permanente %s no debe reintentarse", permanent.getClass().getSimpleName())
                    .isFalse();
        }

        RetryContext transientCtx = retryPolicy.open(null);
        for (int attempt = 1; attempt <= 3; attempt++) {
            retryPolicy.registerThrowable(transientCtx, new ListenerExecutionFailedException("wrapped", new RuntimeException("timeout")));
            assertThat(retryPolicy.canRetry(transientCtx)).isTrue();
        }
        retryPolicy.registerThrowable(transientCtx, new ListenerExecutionFailedException("wrapped", new RuntimeException("timeout")));
        assertThat(retryPolicy.canRetry(transientCtx)).isFalse();
    }

    @Test
    @DisplayName("HU SR-RF-25: Dado RabbitConfig resiliente, Cuando se crean RabbitAdmin y ContainerFactory, Entonces ignoran errores de declaración y colas faltantes no son fatales")
    void shouldConfigureResilientAmqpAdminAndListenerContainerFactory() {
        RabbitConfig config = new RabbitConfig();
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        MessageConverter converter = config.jackson2JsonMessageConverter(new ObjectMapper());
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(mock(ProcessTryOnJobService.class));
        RetryOperationsInterceptor interceptor = config.tryOnRetryInterceptor(recoverer, 4, 100L, 2.0, 1000L);

        RabbitAdmin amqpAdmin = config.amqpAdmin(connectionFactory);
        assertThat(ReflectionTestUtils.getField(amqpAdmin, "ignoreDeclarationExceptions")).isEqualTo(Boolean.TRUE);

        SimpleRabbitListenerContainerFactory factory = config.rabbitListenerContainerFactory(
                connectionFactory,
                converter,
                interceptor
        );
        assertThat(ReflectionTestUtils.getField(factory, "missingQueuesFatal")).isEqualTo(Boolean.FALSE);
        assertThat(ReflectionTestUtils.getField(factory, "defaultRequeueRejected")).isEqualTo(Boolean.FALSE);
        assertThat(factory.createListenerContainer()).isNotNull();
    }
}
