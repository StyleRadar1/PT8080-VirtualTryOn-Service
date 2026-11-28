package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import edu.dosw.styleradar.virtualtryon.domain.exception.CatalogServiceUnavailableException;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Pruebas Unitarias - CatalogGarmentRestAdapter (Fallback REST ms-catalogo · SR-RF-24)")
class CatalogGarmentRestAdapterTest {

    @Test
    @DisplayName("Cuando ms-catalogo responde 200 con contrato real de /api/v1/garments/{garmentId}, mapea CatalogGarment elegible")
    void shouldMapRealCatalogoResponseToCatalogGarment() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentRestAdapter adapter = new CatalogGarmentRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        String jsonResponse = """
                {
                    "id": "%s",
                    "nombre": "Vestido largo negro",
                    "tipo": "Vestido",
                    "fotografias": [
                        "https://cdn.styleradar.co/vestido-largo-negro.jpg"
                    ],
                    "estado": "APROBADA",
                    "disponible": true,
                    "fechaPublicacion": "2026-11-22T19:26:23.09893"
                }
                """.formatted(garmentId.value());

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + garmentId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<CatalogGarment> result = adapter.findGarmentById(garmentId);

        server.verify();
        assertTrue(result.isPresent());
        CatalogGarment garment = result.get();
        assertEquals(garmentId, garment.getGarmentId());
        assertEquals("Vestido largo negro", garment.getName());
        assertEquals(GarmentCategory.ONE_PIECE, garment.getCategory());
        assertEquals("https://cdn.styleradar.co/vestido-largo-negro.jpg", garment.getImageUrl());
        assertTrue(garment.isVisible());
        assertTrue(garment.isEligibleForTryOn());
    }

    @Test
    @DisplayName("Cuando ms-catalogo indica estado RECHAZADA e imageUrl escalar, marca visible=false")
    void shouldMarkGarmentNotVisibleWhenModerationRejected() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentRestAdapter adapter = new CatalogGarmentRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        String jsonResponse = """
                {
                    "id": "%s",
                    "nombre": "Camiseta blanca",
                    "tipo": "Camiseta",
                    "imageUrl": "https://cdn.styleradar.co/camiseta.jpg",
                    "estado": "RECHAZADA",
                    "disponible": true,
                    "fechaPublicacion": "2026-11-22T19:26:23Z"
                }
                """.formatted(garmentId.value());

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + garmentId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<CatalogGarment> result = adapter.findGarmentById(garmentId);

        server.verify();
        assertTrue(result.isPresent());
        assertFalse(result.get().isVisible());
        assertEquals(GarmentCategory.TOP, result.get().getCategory());
        assertEquals("https://cdn.styleradar.co/camiseta.jpg", result.get().getImageUrl());
    }

    @Test
    @DisplayName("Cuando ms-catalogo responde 404 o datos inválidos o garmentId nulo, retorna Optional.empty()")
    void shouldDegradeSafelyWhenNotFoundInvalidOrNull() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentRestAdapter adapter = new CatalogGarmentRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId missingId = GarmentId.of(UUID.randomUUID());
        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + missingId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        GarmentId badCategoryId = GarmentId.of(UUID.randomUUID());
        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + badCategoryId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"nombre\":\"Item\",\"tipo\":\"DESCONOCIDA\"}", MediaType.APPLICATION_JSON));

        assertTrue(adapter.findGarmentById(null).isEmpty());
        assertTrue(adapter.findGarmentById(missingId).isEmpty());
        assertTrue(adapter.findGarmentById(badCategoryId).isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("Distingue 404 (Optional.empty) de fallos temporales de Catálogo: 503, 429 y timeout lanzan CatalogServiceUnavailableException")
    void shouldDistinguishNotFound404FromTemporaryFailures503RateLimitAndTimeout() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentRestAdapter adapter = new CatalogGarmentRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId notFoundId = GarmentId.of(UUID.randomUUID());
        GarmentId unavailableId = GarmentId.of(UUID.randomUUID());
        GarmentId rateLimitedId = GarmentId.of(UUID.randomUUID());
        GarmentId timeoutId = GarmentId.of(UUID.randomUUID());

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + notFoundId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + unavailableId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + rateLimitedId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + timeoutId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out", new SocketTimeoutException("connect timed out"));
                });

        assertTrue(adapter.findGarmentById(notFoundId).isEmpty(), "Un 404 debe devolver Optional.empty()");
        assertThrows(CatalogServiceUnavailableException.class, () -> adapter.findGarmentById(unavailableId));
        assertThrows(CatalogServiceUnavailableException.class, () -> adapter.findGarmentById(rateLimitedId));
        assertThrows(CatalogServiceUnavailableException.class, () -> adapter.findGarmentById(timeoutId));
        server.verify();
    }

    @Test
    @DisplayName("BUG-031: Propaga X-Correlation-Id y X-Request-Id desde MDC hacia ms-catalogo en llamadas REST salientes")
    void shouldPropagateCorrelationIdAndRequestIdHeaders() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8220")
                .requestInterceptor(CatalogGarmentRestAdapter.createTracingInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentRestAdapter adapter = new CatalogGarmentRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        String jsonResponse = """
                {
                    "id": "%s",
                    "nombre": "Vestido con Trazabilidad",
                    "tipo": "Vestido",
                    "fotografias": ["https://cdn.styleradar.co/vestido.jpg"],
                    "estado": "APROBADA",
                    "disponible": true
                }
                """.formatted(garmentId.value());

        org.slf4j.MDC.put(edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.filter.CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "corr-test-123");
        org.slf4j.MDC.put(edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.filter.CorrelationIdFilter.REQUEST_ID_MDC_KEY, "req-test-456");

        try {
            server.expect(requestTo("http://localhost:8220/api/v1/garments/" + garmentId.value()))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("X-Correlation-Id", "corr-test-123"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("X-Request-Id", "req-test-456"))
                    .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

            Optional<CatalogGarment> result = adapter.findGarmentById(garmentId);

            server.verify();
            assertTrue(result.isPresent());
        } finally {
            org.slf4j.MDC.clear();
        }
    }

    @Test
    @DisplayName("Regresión falso 404: tipos de catálogo Conjunto, Chaqueta, Abrigo, Cinturón y Bufanda se mapean sin retornar Optional.empty()")
    void shouldNotReturnFalse404ForValidCatalogTypesWithNewAliases() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentRestAdapter adapter = new CatalogGarmentRestAdapter(builder.build(), "http://localhost:8220");

        record Case(GarmentId id, String tipo, GarmentCategory expectedCategory, boolean expectedEligible) {}

        var cases = java.util.List.of(
                new Case(GarmentId.of(UUID.randomUUID()), "Conjunto", GarmentCategory.ONE_PIECE, true),
                new Case(GarmentId.of(UUID.randomUUID()), "Chaqueta", GarmentCategory.OUTERWEAR, true),
                new Case(GarmentId.of(UUID.randomUUID()), "Abrigo", GarmentCategory.OUTERWEAR, true),
                new Case(GarmentId.of(UUID.randomUUID()), "Cinturón", GarmentCategory.ACCESSORIES, false),
                new Case(GarmentId.of(UUID.randomUUID()), "Bufanda", GarmentCategory.ACCESSORIES, false)
        );

        for (Case c : cases) {
            String jsonResponse = """
                    {
                        "id": "%s",
                        "nombre": "Prenda %s",
                        "tipo": "%s",
                        "fotografias": ["https://cdn.styleradar.co/%s.jpg"],
                        "estado": "APROBADA",
                        "disponible": true
                    }
                    """.formatted(c.id().value(), c.tipo(), c.tipo(), c.tipo().toLowerCase());

            server.expect(requestTo("http://localhost:8220/api/v1/garments/" + c.id().value()))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));
        }

        for (Case c : cases) {
            Optional<CatalogGarment> result = adapter.findGarmentById(c.id());
            assertTrue(result.isPresent(), "Tipo '" + c.tipo() + "' no debe degradarse a Optional.empty() (falso 404)");
            assertEquals(c.expectedCategory(), result.get().getCategory());
            assertTrue(result.get().isVisible());
            assertEquals(c.expectedEligible(), result.get().isEligibleForTryOn());
        }

        server.verify();
    }
}
