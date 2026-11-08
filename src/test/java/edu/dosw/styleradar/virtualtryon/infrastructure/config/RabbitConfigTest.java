package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

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
        TopicExchange dlx = config.catalogDeadLetterExchange("test.catalog.dlx");
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

        assertThat(converter).isNotNull();
    }
}
