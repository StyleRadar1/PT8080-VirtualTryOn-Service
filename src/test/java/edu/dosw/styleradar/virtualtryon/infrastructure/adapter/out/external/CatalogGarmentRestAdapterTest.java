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
    @DisplayName("Cuando ms-catalogo responde 200 con contrato real de /api/v1/prendas/{id}, mapea CatalogGarment elegible")
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

        server.expect(requestTo("http://localhost:8220/api/v1/prendas/" + garmentId.value()))
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

        server.expect(requestTo("http://localhost:8220/api/v1/prendas/" + garmentId.value()))
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
        server.expect(requestTo("http://localhost:8220/api/v1/prendas/" + missingId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        GarmentId badCategoryId = GarmentId.of(UUID.randomUUID());
        server.expect(requestTo("http://localhost:8220/api/v1/prendas/" + badCategoryId.value()))
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

        server.expect(requestTo("http://localhost:8220/api/v1/prendas/" + notFoundId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        server.expect(requestTo("http://localhost:8220/api/v1/prendas/" + unavailableId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        server.expect(requestTo("http://localhost:8220/api/v1/prendas/" + rateLimitedId.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        server.expect(requestTo("http://localhost:8220/api/v1/prendas/" + timeoutId.value()))
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
}
