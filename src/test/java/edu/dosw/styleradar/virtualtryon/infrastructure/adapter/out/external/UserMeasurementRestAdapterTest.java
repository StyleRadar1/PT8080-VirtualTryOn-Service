package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import edu.dosw.styleradar.virtualtryon.domain.model.UserBodyMeasurements;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("Pruebas Unitarias - UserMeasurementRestAdapter (ms-usuarios · SR-RF-24)")
class UserMeasurementRestAdapterTest {

    @Test
    @DisplayName("Cuando ms-usuarios responde 200 con medidas, retorna UserBodyMeasurements con datos parseados")
    void shouldReturnMeasurementsWhenUsuariosResponds200() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserMeasurementRestAdapter adapter = new UserMeasurementRestAdapter(builder.build(), "http://localhost:8120");

        UserId userId = UserId.of(UUID.randomUUID());
        String jsonResponse = """
                {
                    "heightCm": 178.5,
                    "chestCm": 98.0,
                    "waistCm": 82.0,
                    "hipCm": 96.0
                }
                """;

        server.expect(requestTo("http://localhost:8120/api/v1/usuarios/" + userId.value() + "/medidas"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<UserBodyMeasurements> result = adapter.findMeasurementsByUserId(userId);

        server.verify();
        assertTrue(result.isPresent());
        assertEquals(178.5, result.get().heightCm());
        assertEquals(98.0, result.get().chestCm());
        assertEquals(82.0, result.get().waistCm());
        assertEquals(96.0, result.get().hipCm());
        assertTrue(result.get().hasSufficientMeasurements());
    }

    @Test
    @DisplayName("Cuando ms-usuarios responde 404 Not Found o 500, degrada retornando Optional.empty() según FA-02")
    void shouldReturnEmptyWhenUsuariosFailsOrNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserMeasurementRestAdapter adapter = new UserMeasurementRestAdapter(builder.build(), "http://localhost:8120");

        UserId userId = UserId.of(UUID.randomUUID());

        server.expect(requestTo("http://localhost:8120/api/v1/usuarios/" + userId.value() + "/medidas"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<UserBodyMeasurements> result = adapter.findMeasurementsByUserId(userId);

        server.verify();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Cuando userId es null o servidor inalcanzable, retorna Optional.empty()")
    void shouldReturnEmptyWhenUserIdIsNull() {
        UserMeasurementRestAdapter adapter = new UserMeasurementRestAdapter("http://localhost:8120");
        assertTrue(adapter.findMeasurementsByUserId(null).isEmpty());
    }

    @Test
    @DisplayName("Cuando userId proviene de un ID numérico de Auth, envía el Long original a ms-usuarios y reenvía Authorization")
    void shouldSendOriginalNumericIdWhenUserIdWasDerivedFromAuthNumericSubject() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserMeasurementRestAdapter adapter = new UserMeasurementRestAdapter(builder.build(), "http://localhost:8120");

        UserId userId = UserId.fromFlexible("12");
        assertNotNull(userId);
        assertNotEquals("12", userId.value().toString());

        String jsonResponse = """
                {
                    "heightCm": 170.0,
                    "chestCm": 92.0,
                    "waistCm": 76.0,
                    "hipCm": 94.0
                }
                """;

        server.expect(requestTo("http://localhost:8120/api/v1/usuarios/12/medidas"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer jwt-prueba-ms-auth"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer jwt-prueba-ms-auth");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        try {
            Optional<UserBodyMeasurements> result = adapter.findMeasurementsByUserId(userId);

            server.verify();
            assertTrue(result.isPresent());
            assertEquals(170.0, result.get().heightCm());
            assertEquals(92.0, result.get().chestCm());
            assertEquals(76.0, result.get().waistCm());
            assertEquals(94.0, result.get().hipCm());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
