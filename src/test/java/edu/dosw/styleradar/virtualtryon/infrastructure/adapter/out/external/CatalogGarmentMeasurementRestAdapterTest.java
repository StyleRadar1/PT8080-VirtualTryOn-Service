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

        server.expect(requestTo("http://localhost:8220/api/v1/catalogo/items/" + garmentId.value() + "/tallas-medidas"))
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

        server.expect(requestTo("http://localhost:8220/api/v1/catalogo/items/" + garmentId.value() + "/tallas-medidas"))
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
}
