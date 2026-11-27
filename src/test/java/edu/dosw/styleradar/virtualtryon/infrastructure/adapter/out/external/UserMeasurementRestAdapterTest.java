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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("Pruebas Unitarias - UserMeasurementRestAdapter (ms-usuarios · SR-RF-24)")
class UserMeasurementRestAdapterTest {

    @Test
    @DisplayName("Cuando ms-usuarios responde 200 en ruta interna /api/v1/internal/users/{id}/measurements, retorna UserBodyMeasurements")
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

        server.expect(requestTo("http://localhost:8120/api/v1/internal/users/" + userId.value() + "/measurements"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
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
    @DisplayName("Cuando hay credenciales internas configuradas, prioriza HTTP Basic Auth preservando el secreto completo y el ID numérico de Auth")
    void shouldSendBasicAuthAndNumericIdWhenInternalCredentialsConfigured() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String secretWithSpaces = " " + UUID.randomUUID() + " ";
        UserMeasurementRestAdapter adapter = new UserMeasurementRestAdapter(
                builder.build(),
                "http://localhost:8120",
                UserMeasurementRestAdapter.DEFAULT_MEASUREMENTS_PATH,
                " ms-auth ",
                secretWithSpaces
        );

        UserId userId = UserId.fromFlexible("12");
        assertNotNull(userId);
        assertNotEquals("12", userId.value().toString());

        String expectedBasicHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("ms-auth:" + secretWithSpaces).getBytes(StandardCharsets.UTF_8));

        String jsonResponse = """
                {
                    "heightCm": 170.0,
                    "chestCm": 92.0,
                    "waistCm": 76.0,
                    "hipCm": 94.0
                }
                """;

        server.expect(requestTo("http://localhost:8120/api/v1/internal/users/12/measurements"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedBasicHeader))
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

    @Test
    @DisplayName("Sin credenciales internas, envía ID numérico a /api/v1/internal/users/12/measurements y reenvía Bearer Authorization")
    void shouldForwardBearerAuthorizationWhenInternalCredentialsAreAbsent() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserMeasurementRestAdapter adapter = new UserMeasurementRestAdapter(builder.build(), "http://localhost:8120");

        UserId userId = UserId.fromFlexible("12");
        assertNotNull(userId);

        String jsonResponse = """
                {
                    "heightCm": 170.0,
                    "chestCm": 92.0,
                    "waistCm": 76.0,
                    "hipCm": 94.0
                }
                """;

        server.expect(requestTo("http://localhost:8120/api/v1/internal/users/12/measurements"))
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
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("Con credenciales internas parciales (solo usuario o solo clave), hace fallback a Bearer o sin header si está vacío")
    void shouldFallbackToBearerWhenOnlyPartialInternalCredentialsAreProvided() {
        String randomSecret = UUID.randomUUID().toString();

        RestClient.Builder builderUserOnly = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer serverUserOnly = MockRestServiceServer.bindTo(builderUserOnly).build();
        UserMeasurementRestAdapter adapterUserOnly = new UserMeasurementRestAdapter(
                builderUserOnly.build(),
                "http://localhost:8120",
                null,
                "ms-auth",
                "   "
        );

        serverUserOnly.expect(requestTo("http://localhost:8120/api/v1/internal/users/15/measurements"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer jwt-fallback"))
                .andRespond(withSuccess("{\"chestCm\": 95.0}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest reqWithBearer = new MockHttpServletRequest();
        reqWithBearer.addHeader(HttpHeaders.AUTHORIZATION, "Bearer jwt-fallback");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(reqWithBearer));
        try {
            Optional<UserBodyMeasurements> res1 = adapterUserOnly.findMeasurementsByUserId(UserId.fromFlexible("15"));
            serverUserOnly.verify();
            assertTrue(res1.isPresent());
            assertEquals(95.0, res1.get().chestCm());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        RestClient.Builder builderSecretOnly = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer serverSecretOnly = MockRestServiceServer.bindTo(builderSecretOnly).build();
        UserMeasurementRestAdapter adapterSecretOnly = new UserMeasurementRestAdapter(
                builderSecretOnly.build(),
                "http://localhost:8120",
                "",
                "   ",
                randomSecret
        );

        serverSecretOnly.expect(requestTo("http://localhost:8120/api/v1/internal/users/16/measurements"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"chestCm\": 90.0}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest reqBlankAuth = new MockHttpServletRequest();
        reqBlankAuth.addHeader(HttpHeaders.AUTHORIZATION, "   ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(reqBlankAuth));
        try {
            Optional<UserBodyMeasurements> res2 = adapterSecretOnly.findMeasurementsByUserId(UserId.fromFlexible("16"));
            serverSecretOnly.verify();
            assertTrue(res2.isPresent());
            assertEquals(90.0, res2.get().chestCm());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("Permite personalizar la ruta de medidas y usa la ruta por defecto si viene en blanco")
    void shouldUseCustomPathAndFallbackToDefaultWhenPathIsBlank() {
        RestClient.Builder customBuilder = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer customServer = MockRestServiceServer.bindTo(customBuilder).build();
        UserMeasurementRestAdapter customAdapter = new UserMeasurementRestAdapter(
                customBuilder.build(),
                "http://localhost:8120",
                " /api/v1/usuarios/{userId}/medidas ",
                null,
                null
        );

        customServer.expect(requestTo("http://localhost:8120/api/v1/usuarios/21/medidas"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"heightCm\": 180.0}", MediaType.APPLICATION_JSON));

        Optional<UserBodyMeasurements> customResult = customAdapter.findMeasurementsByUserId(UserId.fromFlexible("21"));
        customServer.verify();
        assertTrue(customResult.isPresent());
        assertEquals(180.0, customResult.get().heightCm());

        RestClient.Builder blankPathBuilder = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer blankPathServer = MockRestServiceServer.bindTo(blankPathBuilder).build();
        UserMeasurementRestAdapter blankPathAdapter = new UserMeasurementRestAdapter(
                blankPathBuilder.build(),
                "http://localhost:8120",
                "   ",
                null,
                null
        );

        blankPathServer.expect(requestTo("http://localhost:8120/api/v1/internal/users/22/measurements"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"heightCm\": 165.0}", MediaType.APPLICATION_JSON));

        Optional<UserBodyMeasurements> blankPathResult = blankPathAdapter.findMeasurementsByUserId(UserId.fromFlexible("22"));
        blankPathServer.verify();
        assertTrue(blankPathResult.isPresent());
        assertEquals(165.0, blankPathResult.get().heightCm());
    }

    @Test
    @DisplayName("Cuando ms-usuarios responde 200 sin cuerpo, retorna Optional.empty()")
    void shouldReturnEmptyWhenUsuariosRespondsWithEmptyBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserMeasurementRestAdapter adapter = new UserMeasurementRestAdapter(builder.build(), "http://localhost:8120");

        UserId userId = UserId.of(UUID.randomUUID());

        server.expect(requestTo("http://localhost:8120/api/v1/internal/users/" + userId.value() + "/measurements"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        Optional<UserBodyMeasurements> result = adapter.findMeasurementsByUserId(userId);

        server.verify();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Cuando ms-usuarios responde 404 Not Found o 403 Forbidden, degrada retornando Optional.empty() según FA-02")
    void shouldReturnEmptyWhenUsuariosFailsOrNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8120");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserMeasurementRestAdapter adapter = new UserMeasurementRestAdapter(builder.build(), "http://localhost:8120");

        UserId userId1 = UserId.of(UUID.randomUUID());
        UserId userId2 = UserId.of(UUID.randomUUID());

        server.expect(requestTo("http://localhost:8120/api/v1/internal/users/" + userId1.value() + "/measurements"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        server.expect(requestTo("http://localhost:8120/api/v1/internal/users/" + userId2.value() + "/measurements"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertTrue(adapter.findMeasurementsByUserId(userId1).isEmpty());
        assertTrue(adapter.findMeasurementsByUserId(userId2).isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("Cuando userId es null o servidor inalcanzable, retorna Optional.empty()")
    void shouldReturnEmptyWhenUserIdIsNull() {
        UserMeasurementRestAdapter adapter = new UserMeasurementRestAdapter("http://localhost:8120");
        assertTrue(adapter.findMeasurementsByUserId(null).isEmpty());
    }

    @Test
    @DisplayName("BUG-031: Propaga X-Correlation-Id y X-Request-Id desde MDC hacia ms-usuarios mediante el interceptor de trazabilidad")
    void shouldPropagateCorrelationIdAndRequestIdHeaders() {
        org.slf4j.MDC.put(edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.filter.CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "corr-tryon-meas-789");
        org.slf4j.MDC.put(edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.filter.CorrelationIdFilter.REQUEST_ID_MDC_KEY, "req-tryon-meas-321");
        try {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl("http://localhost:8120")
                    .requestInterceptor(CatalogGarmentRestAdapter.createTracingInterceptor());
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            UserMeasurementRestAdapter adapter = new UserMeasurementRestAdapter(builder.build(), "http://localhost:8120");

            UserId userId = UserId.of(UUID.randomUUID());
            server.expect(requestTo("http://localhost:8120/api/v1/internal/users/" + userId.value() + "/measurements"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header(edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.filter.CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-tryon-meas-789"))
                    .andExpect(header(edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.filter.CorrelationIdFilter.REQUEST_ID_HEADER, "req-tryon-meas-321"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            adapter.findMeasurementsByUserId(userId);
            server.verify();
        } finally {
            org.slf4j.MDC.clear();
        }
    }
}
