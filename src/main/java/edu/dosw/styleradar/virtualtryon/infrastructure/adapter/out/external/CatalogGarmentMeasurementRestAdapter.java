package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentSizeChart;
import edu.dosw.styleradar.virtualtryon.domain.model.SizeMeasurementRange;
import edu.dosw.styleradar.virtualtryon.domain.port.out.GarmentSizeMeasurementPort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adaptador de salida REST que consulta las tallas disponibles y la tabla de medidas de una prenda
 * en ms-catalogo (GET /api/v1/catalogo/items/{garmentId}/tallas-medidas) para la evaluación de talla (SR-RF-24).
 * Si el microservicio de catálogo no está disponible, no responde en tiempo o no expone tabla de medidas,
 * degrada de manera segura retornando Optional.empty() según FA-02.
 */
@Component
public class CatalogGarmentMeasurementRestAdapter implements GarmentSizeMeasurementPort {

    private static final Logger log = LoggerFactory.getLogger(CatalogGarmentMeasurementRestAdapter.class);

    private final RestClient restClient;
    private final String baseUrl;

    @Autowired
    public CatalogGarmentMeasurementRestAdapter(
            @Value("${styleradar.tryon.catalogo-service-url:http://localhost:8220}") String baseUrl
    ) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();
    }

    public CatalogGarmentMeasurementRestAdapter(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public Optional<GarmentSizeChart> findSizeChartByGarmentId(GarmentId garmentId) {
        if (garmentId == null) {
            return Optional.empty();
        }
        try {
            GarmentSizeChartDto dto = restClient.get()
                    .uri("/api/v1/catalogo/items/{garmentId}/tallas-medidas", garmentId.value())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GarmentSizeChartDto.class);

            if (dto == null) {
                return Optional.empty();
            }

            Set<String> sizes = new HashSet<>();
            if (dto.availableSizes() != null) {
                sizes.addAll(dto.availableSizes());
            }

            Map<String, SizeMeasurementRange> measurements = new HashMap<>();
            if (dto.sizes() != null) {
                for (SizeEntryDto entry : dto.sizes()) {
                    if (entry.size() != null && !entry.size().isBlank()) {
                        String normSize = entry.size().trim().toUpperCase(Locale.ROOT);
                        sizes.add(normSize);
                        measurements.put(normSize, new SizeMeasurementRange(
                                entry.minChestCm(),
                                entry.maxChestCm(),
                                entry.minWaistCm(),
                                entry.maxWaistCm(),
                                entry.minHipCm(),
                                entry.maxHipCm()
                        ));
                    }
                }
            }

            return Optional.of(new GarmentSizeChart(garmentId, sizes, measurements));
        } catch (Exception ex) {
            log.debug("No se pudo obtener tabla de medidas desde ms-catalogo ({}) para prenda {}: {}",
                    baseUrl, garmentId.value(), ex.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GarmentSizeChartDto(
            @JsonProperty("availableSizes") @JsonAlias({"tallasDisponibles", "tallas"}) Set<String> availableSizes,
            @JsonProperty("sizes") @JsonAlias({"tablaMedidas", "medidas"}) List<SizeEntryDto> sizes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SizeEntryDto(
            @JsonProperty("size") @JsonAlias({"talla"}) String size,
            @JsonProperty("minChestCm") @JsonAlias({"minPecho", "pechoMin"}) Double minChestCm,
            @JsonProperty("maxChestCm") @JsonAlias({"maxPecho", "pechoMax"}) Double maxChestCm,
            @JsonProperty("minWaistCm") @JsonAlias({"minCintura", "cinturaMin"}) Double minWaistCm,
            @JsonProperty("maxWaistCm") @JsonAlias({"maxCintura", "cinturaMax"}) Double maxWaistCm,
            @JsonProperty("minHipCm") @JsonAlias({"minCadera", "caderaMin"}) Double minHipCm,
            @JsonProperty("maxHipCm") @JsonAlias({"maxCadera", "caderaMax"}) Double maxHipCm
    ) {}
}
