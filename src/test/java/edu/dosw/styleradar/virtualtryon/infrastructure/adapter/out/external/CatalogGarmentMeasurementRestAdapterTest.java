package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import edu.dosw.styleradar.virtualtryon.domain.model.GarmentSizeChart;
import edu.dosw.styleradar.virtualtryon.domain.model.SizeMeasurementRange;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("Pruebas Unitarias - CatalogGarmentMeasurementRestAdapter (ms-catalogo · SR-RF-24)")
class CatalogGarmentMeasurementRestAdapterTest {

    @Test
    @DisplayName("Cuando ms-catalogo responde 200 con tabla de medidas, retorna GarmentSizeChart parseado")
    void shouldReturnSizeChartWhenCatalogoResponds200() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentMeasurementRestAdapter adapter =
                new CatalogGarmentMeasurementRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        String jsonResponse = """
                {
                    "availableSizes": ["S", "M", "L"],
                    "sizes": [
                        {
                            "size": "S",
                            "minChestCm": 86.0,
                            "maxChestCm": 94.0
                        },
                        {
                            "size": "M",
                            "minChestCm": 92.0,
                            "maxChestCm": 102.0
                        }
                    ]
                }
                """;

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + garmentId.value() + "/size-guide"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<GarmentSizeChart> result = adapter.findSizeChartByGarmentId(garmentId);

        server.verify();
        assertTrue(result.isPresent());
        GarmentSizeChart chart = result.get();
        assertEquals(garmentId, chart.garmentId());
        assertTrue(chart.offersSize("S"));
        assertTrue(chart.offersSize("M"));
        assertTrue(chart.offersSize("L"));
        assertFalse(chart.offersSize("XL"));

        SizeMeasurementRange rangeM = chart.getMeasurementRange("M");
        assertNotNull(rangeM);
        assertEquals(92.0, rangeM.minChestCm());
        assertEquals(102.0, rangeM.maxChestCm());
        assertTrue(rangeM.chestFits(98.0));
        assertFalse(rangeM.chestFits(110.0));
    }

    @Test
    @DisplayName("Cuando ms-catalogo responde 404 o falla la conexión, degrada retornando Optional.empty() según FA-02")
    void shouldReturnEmptyWhenCatalogoFailsOrNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentMeasurementRestAdapter adapter =
                new CatalogGarmentMeasurementRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + garmentId.value() + "/size-guide"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<GarmentSizeChart> result = adapter.findSizeChartByGarmentId(garmentId);

        server.verify();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Cuando garmentId es null, retorna Optional.empty()")
    void shouldReturnEmptyWhenGarmentIdIsNull() {
        CatalogGarmentMeasurementRestAdapter adapter =
                new CatalogGarmentMeasurementRestAdapter("http://localhost:8220");
        assertTrue(adapter.findSizeChartByGarmentId(null).isEmpty());
    }

    @Test
    @DisplayName("Cuando Catálogo ofrece S y M pero conserva medidas históricas para XL, GarmentSizeChart solo ofrece S y M")
    void shouldSeparateOfferedSizesFromHistoricalMeasurementSizes() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentMeasurementRestAdapter adapter =
                new CatalogGarmentMeasurementRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        String jsonResponse = """
                {
                    "availableSizes": ["S", "M"],
                    "sizes": [
                        {
                            "size": "S",
                            "minChestCm": 86.0,
                            "maxChestCm": 94.0
                        },
                        {
                            "size": "M",
                            "minChestCm": 92.0,
                            "maxChestCm": 102.0
                        },
                        {
                            "size": "XL",
                            "minChestCm": 108.0,
                            "maxChestCm": 116.0
                        }
                    ]
                }
                """;

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + garmentId.value() + "/size-guide"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<GarmentSizeChart> result = adapter.findSizeChartByGarmentId(garmentId);

        server.verify();
        assertTrue(result.isPresent());
        GarmentSizeChart chart = result.get();

        // Tallas ofertadas son exclusivamente S y M
        assertTrue(chart.offersSize("S"));
        assertTrue(chart.offersSize("M"));
        assertFalse(chart.offersSize("XL"), "XL no debe figurar en tallas disponibles porque no está en availableSizes");

        // Sin embargo, las medidas de XL se conservan en la tabla si existen
        assertNotNull(chart.getMeasurementRange("XL"));
        assertEquals(108.0, chart.getMeasurementRange("XL").minChestCm());
    }

    @Test
    @DisplayName("Cuando Catálogo ofrece M pero sizes está vacío (falta tabla de medidas), GarmentSizeChart ofrece M y getMeasurementRange es null")
    void shouldHandleOfferedSizeWithMissingMeasurementTable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentMeasurementRestAdapter adapter =
                new CatalogGarmentMeasurementRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        String jsonResponse = """
                {
                    "availableSizes": ["M"],
                    "sizes": []
                }
                """;

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + garmentId.value() + "/size-guide"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<GarmentSizeChart> result = adapter.findSizeChartByGarmentId(garmentId);

        server.verify();
        assertTrue(result.isPresent());
        GarmentSizeChart chart = result.get();

        assertTrue(chart.offersSize("M"));
        assertNull(chart.getMeasurementRange("M"), "Al no tener tabla de medidas en sizes, el rango debe ser null");
    }

    @Test
    @DisplayName("Cuando Catálogo responde availableSizes: [], GarmentSizeChart tiene hasExplicitAvailableSizes=true y lista vacía")
    void shouldHandleExplicitEmptyAvailableSizesWhenCatalogReturnsEmptyList() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentMeasurementRestAdapter adapter =
                new CatalogGarmentMeasurementRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId garmentIdEmpty = GarmentId.of(UUID.randomUUID());
        String jsonEmptySizes = """
                {
                    "availableSizes": [],
                    "sizes": []
                }
                """;

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + garmentIdEmpty.value() + "/size-guide"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonEmptySizes, MediaType.APPLICATION_JSON));

        Optional<GarmentSizeChart> resultEmpty = adapter.findSizeChartByGarmentId(garmentIdEmpty);
        server.verify();
        assertTrue(resultEmpty.isPresent());
        GarmentSizeChart chartEmpty = resultEmpty.get();
        assertTrue(chartEmpty.hasExplicitAvailableSizes());
        assertNotNull(chartEmpty.availableSizes());
        assertTrue(chartEmpty.availableSizes().isEmpty());
        assertFalse(chartEmpty.offersSize("M"));
    }

    @Test
    @DisplayName("Cuando Catálogo omite el campo availableSizes (null), GarmentSizeChart tiene hasExplicitAvailableSizes=false y availableSizes es null")
    void shouldHandleAbsentAvailableSizesWhenCatalogOmitsField() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8220");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogGarmentMeasurementRestAdapter adapter =
                new CatalogGarmentMeasurementRestAdapter(builder.build(), "http://localhost:8220");

        GarmentId garmentIdAbsent = GarmentId.of(UUID.randomUUID());
        String jsonAbsentSizes = """
                {
                    "sizes": []
                }
                """;

        server.expect(requestTo("http://localhost:8220/api/v1/garments/" + garmentIdAbsent.value() + "/size-guide"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonAbsentSizes, MediaType.APPLICATION_JSON));

        Optional<GarmentSizeChart> resultAbsent = adapter.findSizeChartByGarmentId(garmentIdAbsent);
        server.verify();
        assertTrue(resultAbsent.isPresent());
        GarmentSizeChart chartAbsent = resultAbsent.get();
        assertFalse(chartAbsent.hasExplicitAvailableSizes());
        assertNull(chartAbsent.availableSizes());
        assertFalse(chartAbsent.offersSize("M"));
    }
}
