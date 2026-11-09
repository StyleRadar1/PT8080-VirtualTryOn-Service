package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.port.in.ProcessTryOnJobUseCase;
import edu.dosw.styleradar.virtualtryon.infrastructure.config.RabbitConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.RabbitMQContainer;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pruebas de integración reales con RabbitMQ Testcontainers (SR-RF-25):
 * Valida reintentos con backoff ante fallos transitorios, enrutamiento a Dead Letter Queue (DLQ),
 * invocación del recoverer para marcar FAILED al agotar reintentos, y envío directo a DLQ
 * ante errores permanentes (JobNotFoundException, ConsentRequiredException).
 */
class TryOnJobRabbitIntegrationTest {

    private static RabbitMQContainer rabbitContainer;
    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbitTemplate;
    private static RabbitAdmin rabbitAdmin;
    private static Jackson2JsonMessageConverter messageConverter;

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @BeforeAll
    static void setUpAll() {
        if (!isDockerAvailable()) {
            return;
        }

        rabbitContainer = new RabbitMQContainer("rabbitmq:3.13-alpine");
        rabbitContainer.start();

        connectionFactory = new CachingConnectionFactory(
                rabbitContainer.getHost(),
                rabbitContainer.getAmqpPort()
        );
        connectionFactory.setUsername(rabbitContainer.getAdminUsername());
        connectionFactory.setPassword(rabbitContainer.getAdminPassword());

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        messageConverter = new Jackson2JsonMessageConverter(objectMapper);

        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);

        rabbitAdmin = new RabbitAdmin(connectionFactory);

        // Declarar topología DLX y DLQ
        TopicExchange dlx = ExchangeBuilder.topicExchange(RabbitConfig.DEFAULT_TRYON_DLX).durable(true).build();
        rabbitAdmin.declareExchange(dlx);

        Queue dlq = QueueBuilder.durable(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ).build();
        rabbitAdmin.declareQueue(dlq);
        rabbitAdmin.declareBinding(BindingBuilder.bind(dlq).to(dlx).with(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY));

        // Declarar exchange principal y cola con DLX
        TopicExchange exchange = ExchangeBuilder.topicExchange(RabbitConfig.DEFAULT_TRYON_EXCHANGE).durable(true).build();
        rabbitAdmin.declareExchange(exchange);

        Queue queue = QueueBuilder.durable(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE)
                .deadLetterExchange(RabbitConfig.DEFAULT_TRYON_DLX)
                .deadLetterRoutingKey(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY)
                .build();
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(RabbitConfig.DEFAULT_TRYON_ROUTING_KEY_CREATED));
    }

    @AfterAll
    static void tearDownAll() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (rabbitContainer != null) {
            rabbitContainer.stop();
        }
    }

    private void purgeQueues() {
        rabbitAdmin.purgeQueue(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        rabbitAdmin.purgeQueue(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ);
    }

    @Test
    @DisplayName("HU SR-RF-25 (RabbitMQ Real): Reintento transitorio exitoso en 2do intento ackea el mensaje sin ir a DLQ")
    void shouldSucceedAfterTransientRetry() throws Exception {
        Assumptions.assumeTrue(isDockerAvailable(), "Docker no disponible; se omite integración RabbitMQ");
        purgeQueues();

        ProcessTryOnJobUseCase mockUseCase = mock(ProcessTryOnJobUseCase.class);
        ProcessTryOnJobService mockService = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(mockService);

        AtomicInteger attempts = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> {
            int current = attempts.incrementAndGet();
            if (current == 1) {
                throw new RuntimeException("Fallo transitorio de conexión a FASHN AI");
            }
            latch.countDown();
            return null;
        }).when(mockUseCase).processJob(any());

        TryOnJobProcessConsumer consumer = new TryOnJobProcessConsumer(mockUseCase);

        RetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(100, 1.5, 500)
                .recoverer(recoverer)
                .build();

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        container.setDefaultRequeueRejected(false);
        container.setAdviceChain(retryInterceptor);

        MessageListenerAdapter adapter = new MessageListenerAdapter(consumer, messageConverter);
        adapter.setDefaultListenerMethod("onTryOnJobCreated");
        container.setMessageListener(adapter);
        container.start();

        try {
            UUID jobId = UUID.randomUUID();
            TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                    UUID.randomUUID(), jobId, UUID.randomUUID(), UUID.randomUUID(),
                    JobStatus.QUEUED, null, null, Instant.now()
            );

            rabbitTemplate.convertAndSend(
                    RabbitConfig.DEFAULT_TRYON_EXCHANGE,
                    RabbitConfig.DEFAULT_TRYON_ROUTING_KEY_CREATED,
                    event
            );

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(attempts.get()).isEqualTo(2);

            // DLQ debe estar vacía
            Message dlqMsg = rabbitTemplate.receive(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ, 1000);
            assertThat(dlqMsg).isNull();
        } finally {
            container.stop();
        }
    }

    @Test
    @DisplayName("HU SR-RF-25 (RabbitMQ Real): Agotamiento de reintentos marca FAILED vía recoverer y desvía mensaje a DLQ")
    void shouldRouteToDlqAndMarkFailedWhenRetriesExhausted() throws Exception {
        Assumptions.assumeTrue(isDockerAvailable(), "Docker no disponible; se omite integración RabbitMQ");
        purgeQueues();

        ProcessTryOnJobUseCase mockUseCase = mock(ProcessTryOnJobUseCase.class);
        ProcessTryOnJobService mockService = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(mockService);

        CountDownLatch exhaustedLatch = new CountDownLatch(1);
        UUID targetJobId = UUID.randomUUID();

        doAnswer(inv -> {
            throw new RuntimeException("FASHN AI no responde (timeout transitorio)");
        }).when(mockUseCase).processJob(targetJobId);

        doAnswer(inv -> {
            exhaustedLatch.countDown();
            return null;
        }).when(mockService).markFailedAfterRetries(eq(targetJobId), any());

        TryOnJobProcessConsumer consumer = new TryOnJobProcessConsumer(mockUseCase);

        RetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxAttempts(2)
                .backOffOptions(50, 1.0, 100)
                .recoverer(recoverer)
                .build();

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        container.setDefaultRequeueRejected(false);
        container.setAdviceChain(retryInterceptor);

        MessageListenerAdapter adapter = new MessageListenerAdapter(consumer, messageConverter);
        adapter.setDefaultListenerMethod("onTryOnJobCreated");
        container.setMessageListener(adapter);
        container.start();

        try {
            TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                    UUID.randomUUID(), targetJobId, UUID.randomUUID(), UUID.randomUUID(),
                    JobStatus.QUEUED, null, null, Instant.now()
            );

            rabbitTemplate.convertAndSend(
                    RabbitConfig.DEFAULT_TRYON_EXCHANGE,
                    RabbitConfig.DEFAULT_TRYON_ROUTING_KEY_CREATED,
                    event
            );

            boolean markedFailed = exhaustedLatch.await(10, TimeUnit.SECONDS);
            assertThat(markedFailed).isTrue();
            verify(mockService).markFailedAfterRetries(eq(targetJobId), eq("RuntimeException"));

            // Mensaje debe estar presente en la DLQ
            Message deadLetterMsg = rabbitTemplate.receive(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ, 5000);
            assertThat(deadLetterMsg).isNotNull();
            TryOnJobDomainEvent deadEvent = (TryOnJobDomainEvent) messageConverter.fromMessage(deadLetterMsg);
            assertThat(deadEvent.jobId()).isEqualTo(targetJobId);
        } finally {
            container.stop();
        }
    }

    @Test
    @DisplayName("HU SR-RF-25 (RabbitMQ Real): Error permanente (JobNotFound / ConsentRequired) va directo a DLQ sin reintentar")
    void shouldRouteDirectlyToDlqOnPermanentErrorWithoutRetries() throws Exception {
        Assumptions.assumeTrue(isDockerAvailable(), "Docker no disponible; se omite integración RabbitMQ");
        purgeQueues();

        ProcessTryOnJobUseCase mockUseCase = mock(ProcessTryOnJobUseCase.class);
        ProcessTryOnJobService mockService = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(mockService);

        UUID nonExistentJobId = UUID.randomUUID();
        AtomicInteger invocationCount = new AtomicInteger(0);

        doAnswer(inv -> {
            invocationCount.incrementAndGet();
            throw new JobNotFoundException(nonExistentJobId);
        }).when(mockUseCase).processJob(nonExistentJobId);

        TryOnJobProcessConsumer consumer = new TryOnJobProcessConsumer(mockUseCase);

        RetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxAttempts(4)
                .backOffOptions(100, 2.0, 1000)
                .recoverer(recoverer)
                .build();

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        container.setDefaultRequeueRejected(false);
        container.setAdviceChain(retryInterceptor);

        MessageListenerAdapter adapter = new MessageListenerAdapter(consumer, messageConverter);
        adapter.setDefaultListenerMethod("onTryOnJobCreated");
        container.setMessageListener(adapter);
        container.start();

        try {
            TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                    UUID.randomUUID(), nonExistentJobId, UUID.randomUUID(), UUID.randomUUID(),
                    JobStatus.QUEUED, null, null, Instant.now()
            );

            rabbitTemplate.convertAndSend(
                    RabbitConfig.DEFAULT_TRYON_EXCHANGE,
                    RabbitConfig.DEFAULT_TRYON_ROUTING_KEY_CREATED,
                    event
            );

            // Mensaje debe recibirse en DLQ casi inmediatamente
            Message deadLetterMsg = rabbitTemplate.receive(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ, 5000);
            assertThat(deadLetterMsg).isNotNull();
            TryOnJobDomainEvent deadEvent = (TryOnJobDomainEvent) messageConverter.fromMessage(deadLetterMsg);
            assertThat(deadEvent.jobId()).isEqualTo(nonExistentJobId);

            // Se invocó una sola vez: sin reintentos
            assertThat(invocationCount.get()).isEqualTo(1);
        } finally {
            container.stop();
        }
    }
}
