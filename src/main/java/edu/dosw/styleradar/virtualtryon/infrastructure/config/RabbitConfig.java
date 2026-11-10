package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp.TryOnJobRetryRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del broker RabbitMQ para el microservicio de Probador Virtual.
 * Declara los TopicExchanges para eventos de catálogo y try-on jobs, colas de sincronización
 * con soporte de Dead Letter Queue (DLQ), sus bindings y el conversor JSON (SR-RF-24 y SR-RF-25).
 */
@Configuration
public class RabbitConfig {

    public static final String DEFAULT_CATALOG_EXCHANGE = "styleradar.exchange";
    public static final String DEFAULT_CATALOG_QUEUE = "styleradar.probador.prenda-publicada.q";
    public static final String DEFAULT_CATALOG_ROUTING_KEY = "styleradar.catalogo.prenda.publicada";

    public static final String DEFAULT_CATALOG_DLX = "styleradar.dlx";
    public static final String DEFAULT_CATALOG_DLQ = "styleradar.probador.dlq";
    public static final String DEFAULT_CATALOG_DLQ_ROUTING_KEY = "styleradar.probador.dlq";

    public static final String DEFAULT_TRYON_EXCHANGE = "styleradar.tryon.events";
    public static final String DEFAULT_TRYON_PROCESS_QUEUE = "q.tryon.job.process";
    public static final String DEFAULT_TRYON_ROUTING_KEY_CREATED = "tryon.job.created";
    public static final String DEFAULT_TRYON_DLX = "styleradar.tryon.dlx";
    public static final String DEFAULT_TRYON_PROCESS_DLQ = "q.tryon.job.process.dlq";
    public static final String DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY = "tryon.job.process.dead";

    public static final String DEFAULT_USER_EVENTS_EXCHANGE = "styleradar.exchange";
    public static final String DEFAULT_USER_ACCOUNT_DELETED_QUEUE = "styleradar.probador.cuenta-eliminada.q";
    public static final String DEFAULT_USER_ACCOUNT_DELETED_ROUTING_KEY = "styleradar.usuarios.cuenta.eliminada";

    @Bean
    public TopicExchange catalogEventsExchange(
            @Value("${styleradar.tryon.amqp.catalog-exchange:" + DEFAULT_CATALOG_EXCHANGE + "}") String exchangeName
    ) {
        return ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
    }

    @Bean
    public DirectExchange catalogDeadLetterExchange(
            @Value("${styleradar.tryon.amqp.catalog-dlx:" + DEFAULT_CATALOG_DLX + "}") String dlxName
    ) {
        return ExchangeBuilder.directExchange(dlxName).durable(true).build();
    }

    @Bean
    public Queue catalogGarmentUpdatedDlq(
            @Value("${styleradar.tryon.amqp.catalog-garment-dlq:" + DEFAULT_CATALOG_DLQ + "}") String dlqName
    ) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding catalogGarmentUpdatedDlqBinding(
            Queue catalogGarmentUpdatedDlq,
            DirectExchange catalogDeadLetterExchange,
            @Value("${styleradar.tryon.amqp.catalog-garment-dlq-routing-key:" + DEFAULT_CATALOG_DLQ_ROUTING_KEY + "}") String dlqRoutingKey
    ) {
        return BindingBuilder.bind(catalogGarmentUpdatedDlq).to(catalogDeadLetterExchange).with(dlqRoutingKey);
    }

    @Bean
    public Queue catalogGarmentUpdatedQueue(
            @Value("${styleradar.tryon.amqp.catalog-garment-queue:" + DEFAULT_CATALOG_QUEUE + "}") String queueName,
            @Value("${styleradar.tryon.amqp.catalog-dlx:" + DEFAULT_CATALOG_DLX + "}") String dlxName,
            @Value("${styleradar.tryon.amqp.catalog-garment-dlq-routing-key:" + DEFAULT_CATALOG_DLQ_ROUTING_KEY + "}") String dlqRoutingKey
    ) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(dlxName)
                .deadLetterRoutingKey(dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding catalogGarmentUpdatedBinding(
            Queue catalogGarmentUpdatedQueue,
            TopicExchange catalogEventsExchange,
            @Value("${styleradar.tryon.amqp.catalog-garment-routing-key:" + DEFAULT_CATALOG_ROUTING_KEY + "}") String routingKey
    ) {
        return BindingBuilder.bind(catalogGarmentUpdatedQueue).to(catalogEventsExchange).with(routingKey);
    }

    @Bean
    public TopicExchange tryOnEventsExchange(
            @Value("${styleradar.tryon.amqp.exchange:" + DEFAULT_TRYON_EXCHANGE + "}") String exchangeName
    ) {
        return ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
    }

    @Bean
    public TopicExchange tryOnDeadLetterExchange(
            @Value("${styleradar.tryon.amqp.dlx:" + DEFAULT_TRYON_DLX + "}") String dlxName
    ) {
        return ExchangeBuilder.topicExchange(dlxName).durable(true).build();
    }

    @Bean
    public Queue tryOnJobProcessDlq(
            @Value("${styleradar.tryon.amqp.process-dlq:" + DEFAULT_TRYON_PROCESS_DLQ + "}") String dlqName
    ) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding tryOnJobProcessDlqBinding(
            Queue tryOnJobProcessDlq,
            TopicExchange tryOnDeadLetterExchange,
            @Value("${styleradar.tryon.amqp.process-dlq-routing-key:" + DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY + "}") String dlqRoutingKey
    ) {
        return BindingBuilder.bind(tryOnJobProcessDlq).to(tryOnDeadLetterExchange).with(dlqRoutingKey);
    }

    @Bean
    public Queue tryOnJobProcessQueue(
            @Value("${styleradar.tryon.amqp.process-queue:" + DEFAULT_TRYON_PROCESS_QUEUE + "}") String queueName,
            @Value("${styleradar.tryon.amqp.dlx:" + DEFAULT_TRYON_DLX + "}") String dlxName,
            @Value("${styleradar.tryon.amqp.process-dlq-routing-key:" + DEFAULT_TRYON_PROCESS_DLQ_ROUTING_KEY + "}") String dlqRoutingKey
    ) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(dlxName)
                .deadLetterRoutingKey(dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding tryOnJobProcessBinding(
            Queue tryOnJobProcessQueue,
            TopicExchange tryOnEventsExchange,
            @Value("${styleradar.tryon.amqp.created-routing-key:" + DEFAULT_TRYON_ROUTING_KEY_CREATED + "}") String routingKey
    ) {
        return BindingBuilder.bind(tryOnJobProcessQueue).to(tryOnEventsExchange).with(routingKey);
    }

    @Bean
    public TopicExchange userEventsExchange(
            @Value("${styleradar.tryon.amqp.user-exchange:" + DEFAULT_USER_EVENTS_EXCHANGE + "}") String exchangeName
    ) {
        return ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
    }

    @Bean
    public Queue userAccountDeletedQueue(
            @Value("${styleradar.tryon.amqp.user-account-deleted-queue:" + DEFAULT_USER_ACCOUNT_DELETED_QUEUE + "}") String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding userAccountDeletedBinding(
            Queue userAccountDeletedQueue,
            TopicExchange userEventsExchange,
            @Value("${styleradar.tryon.amqp.user-account-deleted-routing-key:" + DEFAULT_USER_ACCOUNT_DELETED_ROUTING_KEY + "}") String routingKey
    ) {
        return BindingBuilder.bind(userAccountDeletedQueue).to(userEventsExchange).with(routingKey);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RetryOperationsInterceptor tryOnRetryInterceptor(
            TryOnJobRetryRecoverer recoverer,
            @Value("${styleradar.tryon.amqp.retry.max-attempts:4}") int maxAttempts,
            @Value("${styleradar.tryon.amqp.retry.initial-interval-ms:1000}") long initialInterval,
            @Value("${styleradar.tryon.amqp.retry.multiplier:2.0}") double multiplier,
            @Value("${styleradar.tryon.amqp.retry.max-interval-ms:10000}") long maxInterval
    ) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(initialInterval, multiplier, maxInterval)
                .recoverer(recoverer)
                .build();
    }

    @Bean(name = "rabbitListenerContainerFactory")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(ConnectionFactory.class)
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            RetryOperationsInterceptor tryOnRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(tryOnRetryInterceptor);
        return factory;
    }
}
