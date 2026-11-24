package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.port.in.ProcessTryOnJobUseCase;
import edu.dosw.styleradar.virtualtryon.infrastructure.config.RabbitConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.RabbitMQContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Prueba de integración real sobre RabbitMQ 3.13 (Management API + AMQP) que simula una actualización
 * desde una cola {@code q.tryon.job.process} preexistente con {@code x-dead-letter-exchange=styleradar.tryon.dlx}:
 * 1. Verifica PRECONDITION_FAILED al intentar redeclarar con styleradar.dlx y demuestra que ignoreDeclarationExceptions
 *    conserva la cola anterior sin perder mensajes.
 * 2. Ejecuta el procedimiento de migración sin pérdida de jobs pendientes hacia una cola temporal y recrea
 *    {@code q.tryon.job.process} con {@code x-dead-letter-exchange=styleradar.dlx}.
 * 3. Verifica vía Management API y {@code rabbitmqctl} que los nuevos argumentos quedaron activos, que todos los
 *    jobs previos se procesan en orden y que un job rechazado sin reencolar llega a {@code q.tryon.job.process.dlq}
 *    con routing key {@code tryon.job.process.dead}.
 */
class TryOnJobRabbitUpgradeMigrationIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TryOnJobRabbitUpgradeMigrationIntegrationTest.class);
    private static final String LEGACY_TRYON_DLX = "styleradar.tryon.dlx";
    private static final String TEMP_MIGRATION_QUEUE = "q.tryon.job.process.migration.tmp";

    private static RabbitMQContainer rabbitContainer;
    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbitTemplate;
    private static Jackson2JsonMessageConverter messageConverter;
    private static ObjectMapper objectMapper;
    private static HttpClient httpClient;

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
        rabbitContainer = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
        rabbitContainer.start();

        connectionFactory = new CachingConnectionFactory(
                rabbitContainer.getHost(),
                rabbitContainer.getAmqpPort()
        );
        connectionFactory.setUsername(rabbitContainer.getAdminUsername());
        connectionFactory.setPassword(rabbitContainer.getAdminPassword());

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        messageConverter = new Jackson2JsonMessageConverter(objectMapper);

        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);

        httpClient = HttpClient.newHttpClient();
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

    @Test
    @DisplayName("HU SR-RF-25 (Migración desde topología anterior): Migra q.tryon.job.process sin perder jobs y enruta rechazos a styleradar.dlx / tryon.job.process.dead")
    void shouldMigrateLegacyQueueWithoutLosingPendingJobsAndRouteRejectedJobToSharedDlx() throws Exception {
        Assumptions.assumeTrue(isDockerAvailable(), "Docker no disponible; se omite prueba de migración RabbitMQ");

        RabbitConfig rabbitConfig = new RabbitConfig();

        // -----------------------------------------------------------------
        // FASE 1: Arrancar el broker con la configuración ANTERIOR (styleradar.tryon.dlx)
        // -----------------------------------------------------------------
        RabbitAdmin legacyAdmin = new RabbitAdmin(connectionFactory);
        TopicExchange mainExchange = ExchangeBuilder.topicExchange(RabbitConfig.DEFAULT_TRYON_EXCHANGE)
                .durable(true).build();
        legacyAdmin.declareExchange(mainExchange);

        TopicExchange legacyDlx = ExchangeBuilder.topicExchange(LEGACY_TRYON_DLX).durable(true).build();
        legacyAdmin.declareExchange(legacyDlx);

        Queue legacyDlq = QueueBuilder.durable(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ).build();
        legacyAdmin.declareQueue(legacyDlq);
        legacyAdmin.declareBinding(
                BindingBuilder.bind(legacyDlq).to(legacyDlx).with(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY)
        );

        Queue legacyQueue = QueueBuilder.durable(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE)
                .deadLetterExchange(LEGACY_TRYON_DLX)
                .deadLetterRoutingKey(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY)
                .build();
        legacyAdmin.declareQueue(legacyQueue);
        legacyAdmin.declareBinding(
                BindingBuilder.bind(legacyQueue).to(mainExchange).with(RabbitConfig.DEFAULT_TRYON_ROUTING_KEY_CREATED)
        );

        JsonNode legacyQueueInfo = fetchQueueInfoFromManagementApi(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        assertThat(legacyQueueInfo.path("arguments").path("x-dead-letter-exchange").asText())
                .isEqualTo(LEGACY_TRYON_DLX);

        UUID pendingJob1 = UUID.randomUUID();
        UUID pendingJob2 = UUID.randomUUID();
        publishCreatedJob(pendingJob1);
        publishCreatedJob(pendingJob2);

        int queuedBeforeMigration = getMessageCount(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        assertThat(queuedBeforeMigration).isEqualTo(2);

        // -----------------------------------------------------------------
        // FASE 2: Reproducir PRECONDITION_FAILED al redeclarar con styleradar.dlx
        // -----------------------------------------------------------------
        Queue targetQueueBean = rabbitConfig.tryOnJobProcessQueue(
                RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE,
                RabbitConfig.DEFAULT_TRYON_DLX,
                RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY
        );

        RabbitAdmin strictAdmin = new RabbitAdmin(connectionFactory);
        assertThatThrownBy(() -> strictAdmin.declareQueue(targetQueueBean))
                .isInstanceOf(AmqpException.class)
                .satisfies(ex -> assertThat(collectCauseMessages(ex)).contains("PRECONDITION_FAILED"));

        RabbitAdmin resilientAdmin = rabbitConfig.amqpAdmin(connectionFactory);
        resilientAdmin.declareQueue(targetQueueBean);

        JsonNode afterResilientDeclare = fetchQueueInfoFromManagementApi(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        assertThat(afterResilientDeclare.path("arguments").path("x-dead-letter-exchange").asText())
                .isEqualTo(LEGACY_TRYON_DLX);
        assertThat(getMessageCount(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE)).isEqualTo(2);

        // -----------------------------------------------------------------
        // FASE 3: Procedimiento de migración preservando los 2 jobs pendientes
        // -----------------------------------------------------------------
        Queue tempQueue = QueueBuilder.durable(TEMP_MIGRATION_QUEUE).build();
        legacyAdmin.declareQueue(tempQueue);
        legacyAdmin.declareBinding(
                BindingBuilder.bind(tempQueue).to(mainExchange).with(RabbitConfig.DEFAULT_TRYON_ROUTING_KEY_CREATED)
        );

        int drainedToTemp = rabbitTemplate.execute(channel -> {
            channel.queueUnbind(
                    RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE,
                    RabbitConfig.DEFAULT_TRYON_EXCHANGE,
                    RabbitConfig.DEFAULT_TRYON_ROUTING_KEY_CREATED
            );
            return moveAllMessagesWithConfirms(
                    channel,
                    RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE,
                    TEMP_MIGRATION_QUEUE
            );
        });
        assertThat(drainedToTemp).isEqualTo(2);
        assertThat(getMessageCount(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE)).isZero();

        rabbitTemplate.execute(channel -> {
            channel.queueDelete(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE, false, true);
            return null;
        });

        DirectExchange sharedDlxBean = rabbitConfig.tryOnDeadLetterExchange(RabbitConfig.DEFAULT_TRYON_DLX);
        Queue dlqBean = rabbitConfig.tryOnJobProcessDlq(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ);
        Binding dlqBinding = rabbitConfig.tryOnJobProcessDlqBinding(
                dlqBean, sharedDlxBean, RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY
        );
        TopicExchange tryOnExchangeBean = rabbitConfig.tryOnEventsExchange(RabbitConfig.DEFAULT_TRYON_EXCHANGE);
        Binding queueBinding = rabbitConfig.tryOnJobProcessBinding(
                targetQueueBean, tryOnExchangeBean, RabbitConfig.DEFAULT_TRYON_ROUTING_KEY_CREATED
        );

        RabbitAdmin postMigrationStrictAdmin = new RabbitAdmin(connectionFactory);
        postMigrationStrictAdmin.declareExchange(tryOnExchangeBean);
        postMigrationStrictAdmin.declareExchange(sharedDlxBean);
        postMigrationStrictAdmin.declareQueue(dlqBean);
        postMigrationStrictAdmin.declareBinding(dlqBinding);
        postMigrationStrictAdmin.declareQueue(targetQueueBean);
        postMigrationStrictAdmin.declareBinding(queueBinding);

        int restoredToTarget = rabbitTemplate.execute(channel -> {
            channel.queueUnbind(
                    TEMP_MIGRATION_QUEUE,
                    RabbitConfig.DEFAULT_TRYON_EXCHANGE,
                    RabbitConfig.DEFAULT_TRYON_ROUTING_KEY_CREATED
            );
            int moved = moveAllMessagesWithConfirms(
                    channel,
                    TEMP_MIGRATION_QUEUE,
                    RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE
            );
            channel.queueDelete(TEMP_MIGRATION_QUEUE, false, true);
            return moved;
        });
        assertThat(restoredToTarget).isEqualTo(2);

        // -----------------------------------------------------------------
        // FASE 4: Evidencia real post-migración y enrutamiento de rechazo a DLQ
        // -----------------------------------------------------------------
        JsonNode migratedQueueInfo = fetchQueueInfoFromManagementApi(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        Container.ExecResult rabbitCtlResult = rabbitContainer.execInContainer(
                "rabbitmqctl", "list_queues", "-p", "/", "name", "messages", "arguments"
        );
        assertThat(rabbitCtlResult.getExitCode()).isZero();

        log.info("[EVIDENCIA MIGRACION AMQP] Management API {} => arguments={}, messages={}",
                RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE,
                migratedQueueInfo.path("arguments"),
                migratedQueueInfo.path("messages").asInt());
        log.info("[EVIDENCIA MIGRACION AMQP] rabbitmqctl list_queues:\n{}", rabbitCtlResult.getStdout());

        assertThat(migratedQueueInfo.path("arguments").path("x-dead-letter-exchange").asText())
                .isEqualTo(RabbitConfig.DEFAULT_TRYON_DLX);
        assertThat(migratedQueueInfo.path("arguments").path("x-dead-letter-routing-key").asText())
                .isEqualTo(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY);
        assertThat(getMessageCount(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE)).isEqualTo(2);
        assertThat(rabbitCtlResult.getStdout())
                .contains(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE)
                .contains(RabbitConfig.DEFAULT_TRYON_DLX);

        ProcessTryOnJobUseCase mockUseCase = mock(ProcessTryOnJobUseCase.class);
        ProcessTryOnJobService mockService = mock(ProcessTryOnJobService.class);
        TryOnJobRetryRecoverer recoverer = new TryOnJobRetryRecoverer(mockService);

        List<UUID> processedOrder = new CopyOnWriteArrayList<>();
        CountDownLatch pendingProcessedLatch = new CountDownLatch(2);
        UUID rejectedJobId = UUID.randomUUID();

        doAnswer(invocation -> {
            UUID jobId = invocation.getArgument(0);
            processedOrder.add(jobId);
            pendingProcessedLatch.countDown();
            return null;
        }).when(mockUseCase).processJob(any());

        doThrow(new JobNotFoundException(rejectedJobId))
                .when(mockUseCase).processJob(rejectedJobId);

        TryOnJobProcessConsumer consumer = new TryOnJobProcessConsumer(mockUseCase);
        RetryOperationsInterceptor retryInterceptor = rabbitConfig.tryOnRetryInterceptor(
                recoverer, 4, 100L, 2.0, 1000L
        );
        SimpleRabbitListenerContainerFactory factory = rabbitConfig.rabbitListenerContainerFactory(
                connectionFactory,
                messageConverter,
                retryInterceptor
        );
        SimpleMessageListenerContainer container = factory.createListenerContainer();
        assertThat(ReflectionTestUtils.getField(container, "missingQueuesFatal")).isEqualTo(Boolean.FALSE);
        assertThat(ReflectionTestUtils.getField(container, "defaultRequeueRejected")).isEqualTo(Boolean.FALSE);

        container.setQueueNames(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
        MessageListenerAdapter adapter = new MessageListenerAdapter(consumer, messageConverter);
        adapter.setDefaultListenerMethod("onTryOnJobCreated");
        container.setMessageListener(adapter);
        container.start();

        try {
            assertThat(pendingProcessedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(processedOrder).containsExactly(pendingJob1, pendingJob2);

            publishCreatedJob(rejectedJobId);

            Message deadLetterMsg = rabbitTemplate.receive(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ, 5000);
            assertThat(deadLetterMsg).isNotNull();

            TryOnJobDomainEvent deadEvent = (TryOnJobDomainEvent) messageConverter.fromMessage(deadLetterMsg);
            String receivedExchange = deadLetterMsg.getMessageProperties().getReceivedExchange();
            String receivedRoutingKey = deadLetterMsg.getMessageProperties().getReceivedRoutingKey();
            List<Map<String, ?>> xDeathHeader = deadLetterMsg.getMessageProperties().getXDeathHeader();

            log.info("[EVIDENCIA MIGRACION AMQP] DLQ {} => jobId={}, receivedExchange={}, receivedRoutingKey={}, xDeath={}",
                    RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ,
                    deadEvent.jobId(),
                    receivedExchange,
                    receivedRoutingKey,
                    xDeathHeader);

            assertThat(deadEvent.jobId()).isEqualTo(rejectedJobId);
            assertThat(receivedExchange).isEqualTo(RabbitConfig.DEFAULT_TRYON_DLX);
            assertThat(receivedRoutingKey).isEqualTo(RabbitConfig.DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY);
            assertThat(xDeathHeader).isNotNull().isNotEmpty();
            Map<String, ?> deathEntry = xDeathHeader.get(0);
            assertThat(String.valueOf(deathEntry.get("queue"))).isEqualTo(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE);
            assertThat(String.valueOf(deathEntry.get("reason"))).isEqualTo("rejected");
            assertThat(String.valueOf(deathEntry.get("exchange"))).isEqualTo(RabbitConfig.DEFAULT_TRYON_EXCHANGE);
            assertThat(getMessageCount(RabbitConfig.DEFAULT_TRYON_PROCESS_QUEUE)).isZero();
        } finally {
            container.stop();
        }
    }

    private void publishCreatedJob(UUID jobId) {
        TryOnJobDomainEvent event = new TryOnJobDomainEvent(
                UUID.randomUUID(),
                jobId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JobStatus.QUEUED,
                null,
                null,
                Instant.now()
        );
        rabbitTemplate.convertAndSend(
                RabbitConfig.DEFAULT_TRYON_EXCHANGE,
                RabbitConfig.DEFAULT_TRYON_ROUTING_KEY_CREATED,
                event
        );
    }

    private int moveAllMessagesWithConfirms(
            com.rabbitmq.client.Channel channel,
            String sourceQueue,
            String destinationQueue
    ) throws Exception {
        channel.confirmSelect();
        int moved = 0;
        while (true) {
            GetResponse response = channel.basicGet(sourceQueue, false);
            if (response == null) {
                break;
            }
            AMQP.BasicProperties persistentProps = response.getProps().builder()
                    .deliveryMode(2)
                    .build();
            channel.basicPublish("", destinationQueue, persistentProps, response.getBody());
            channel.waitForConfirmsOrDie(5_000L);
            channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
            moved++;
        }
        return moved;
    }

    private int getMessageCount(String queueName) {
        Integer count = rabbitTemplate.execute(channel -> channel.queueDeclarePassive(queueName).getMessageCount());
        return count != null ? count : 0;
    }

    private JsonNode fetchQueueInfoFromManagementApi(String queueName) throws Exception {
        String auth = Base64.getEncoder().encodeToString(
                (rabbitContainer.getAdminUsername() + ":" + rabbitContainer.getAdminPassword())
                        .getBytes(StandardCharsets.UTF_8)
        );
        String url = rabbitContainer.getHttpUrl() + "/api/queues/%2F/" + queueName;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + auth)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private String collectCauseMessages(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(" | ");
            }
            current = current.getCause();
        }
        return sb.toString();
    }
}
