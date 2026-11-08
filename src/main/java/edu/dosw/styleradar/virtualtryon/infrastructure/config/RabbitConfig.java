package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de mensajería asíncrona RabbitMQ (TopicExchange styleradar.tryon.events
 * y serializador JSON Jackson) para el microservicio de Probador Virtual (SR-RF-25).
 */
@Configuration
public class RabbitConfig {

    public static final String DEFAULT_TRYON_EXCHANGE = "styleradar.tryon.events";

    @Bean
    public TopicExchange tryOnEventsExchange(
            @Value("${styleradar.tryon.amqp.exchange:" + DEFAULT_TRYON_EXCHANGE + "}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

