package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del broker RabbitMQ para el microservicio de Probador Virtual.
 * Declara el Exchange de eventos del catálogo, la cola de sincronización de prendas,
 * su binding con routing key y el conversor JSON para mensajes AMQP (SR-RF-24).
 */
@Configuration
public class RabbitConfig {

    public static final String DEFAULT_CATALOG_EXCHANGE = "styleradar.catalog.events";
    public static final String DEFAULT_CATALOG_QUEUE = "q.tryon.catalog.garment.updated";
    public static final String DEFAULT_CATALOG_ROUTING_KEY = "catalog.garment.updated";

    @Bean
    public TopicExchange catalogEventsExchange(
            @Value("${styleradar.tryon.amqp.catalog-exchange:" + DEFAULT_CATALOG_EXCHANGE + "}") String exchangeName
    ) {
        return ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
    }

    @Bean
    public Queue catalogGarmentUpdatedQueue(
            @Value("${styleradar.tryon.amqp.catalog-garment-queue:" + DEFAULT_CATALOG_QUEUE + "}") String queueName
    ) {
        return QueueBuilder.durable(queueName).build();
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
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
