package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthRestControllerTest {

    @Test
    @DisplayName("Smoke Test: /api/v1/health debe responder 200 OK con status UP")
    void shouldReturnHealthStatusUp() {
        HealthRestController controller = new HealthRestController();
        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("service")).isEqualTo("PT8080-VirtualTryOn-Service");
        assertThat(response.getBody()).containsKey("timestamp");
    }
}
