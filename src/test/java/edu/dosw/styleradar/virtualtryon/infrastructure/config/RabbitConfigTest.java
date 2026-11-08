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
 * Pruebas unitarias de configuración para el broker RabbitMQ (SR-RF-24).
 */
class RabbitConfigTest {

    @Test
    @DisplayName("HU SR-RF-24: Dado RabbitConfig, Cuando se inicializan beans de AMQP, Entonces declara exchange, cola, binding y conversor JSON correctamente")
    void shouldConfigureRabbitMqBeansCorrectly() {
        RabbitConfig config = new RabbitConfig();
        ObjectMapper objectMapper = new ObjectMapper();

        TopicExchange exchange = config.catalogEventsExchange("test.catalog.events");
        Queue queue = config.catalogGarmentUpdatedQueue("test.catalog.garment.queue");
        Binding binding = config.catalogGarmentUpdatedBinding(queue, exchange, "test.catalog.routing.key");
        MessageConverter converter = config.jackson2JsonMessageConverter(objectMapper);

        assertThat(exchange).isNotNull();
        assertThat(exchange.getName()).isEqualTo("test.catalog.events");
        assertThat(exchange.isDurable()).isTrue();

        assertThat(queue).isNotNull();
        assertThat(queue.getName()).isEqualTo("test.catalog.garment.queue");
        assertThat(queue.isDurable()).isTrue();

        assertThat(binding).isNotNull();
        assertThat(binding.getDestination()).isEqualTo("test.catalog.garment.queue");
        assertThat(binding.getExchange()).isEqualTo("test.catalog.events");
        assertThat(binding.getRoutingKey()).isEqualTo("test.catalog.routing.key");

        assertThat(converter).isNotNull();
    }
}
