package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.CatalogServiceUnavailableException;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.port.out.CatalogGarmentLookupPort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.filter.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adaptador REST de respaldo hacia ms-item-catalogo ({@code GET /api/v1/garments/{garmentId}})
 * para hidratar prendas publicadas previamente que aún no estén en {@code catalog_garments}.
 */
@Component
public class CatalogGarmentRestAdapter implements CatalogGarmentLookupPort {

    private static final Logger log = LoggerFactory.getLogger(CatalogGarmentRestAdapter.class);

    private final RestClient restClient;
    private final String baseUrl;

    @Autowired
    public CatalogGarmentRestAdapter(
            @Value("${styleradar.tryon.catalogo-service-url:http://localhost:8220}") String baseUrl
    ) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .requestInterceptor(createTracingInterceptor())
                .build();
    }

    public static ClientHttpRequestInterceptor createTracingInterceptor() {
        return (req, body, execution) -> {
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                req.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
            }
            String requestId = MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY);
            if (requestId != null && !requestId.isBlank()) {
                req.getHeaders().set(CorrelationIdFilter.REQUEST_ID_HEADER, requestId);
            }
            return execution.execute(req, body);
        };
    }

    public CatalogGarmentRestAdapter(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public Optional<CatalogGarment> findGarmentById(GarmentId garmentId) {
        if (garmentId == null) {
            return Optional.empty();
        }
        try {
            CatalogGarmentDto dto = restClient.get()
                    .uri("/api/v1/garments/{garmentId}", garmentId.value())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CatalogGarmentDto.class);

            if (dto == null) {
                return Optional.empty();
            }

            String resolvedImageUrl = CatalogGarment.sanitizeImageUrl(resolvePrimaryImageUrl(dto));
            CatalogGarmentUpdatedEvent normalized = CatalogGarmentUpdatedEvent.fromJson(
                    dto.id() != null ? dto.id() : garmentId.value(),
                    dto.nombre(),
                    dto.tipo(),
                    resolvedImageUrl,
                    dto.disponible(),
                    dto.aprobada(),
                    dto.estado(),
                    dto.fechaPublicacion(),
                    dto.tallas()
            );

            GarmentCategory category = GarmentCategory.fromRawValue(normalized.category());
            return Optional.of(new CatalogGarment(
                    garmentId,
                    normalized.name(),
                    category,
                    normalized.imageUrl(),
                    normalized.visible(),
                    normalized.updatedAt(),
                    normalized.availableSizes()
            ));
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("Prenda {} confirmada como inexistente (404) en ms-catalogo ({})",
                    garmentId.value(), baseUrl);
            return Optional.empty();
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            log.warn("Fallo temporal consultando prenda {} en ms-catalogo ({}): {}",
                    garmentId.value(), baseUrl, ex.getMessage());
            throw new CatalogServiceUnavailableException(
                    "El servicio de catálogo no está disponible temporalmente. Intente nuevamente más tarde.",
                    ex
            );
        } catch (HttpClientErrorException ex) {
            int code = ex.getStatusCode().value();
            if (code == 408 || code == 429 || code == 401 || code == 403) {
                log.warn("Respuesta transitoria/autorización HTTP {} consultando prenda {} en ms-catalogo ({}): {}",
                        code, garmentId.value(), baseUrl, ex.getMessage());
                throw new CatalogServiceUnavailableException(
                        "El servicio de catálogo no está disponible temporalmente. Intente nuevamente más tarde.",
                        ex
                );
            }
            log.debug("Error HTTP {} consultando prenda {} en ms-catalogo ({}): {}",
                    code, garmentId.value(), baseUrl, ex.getMessage());
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Error de comunicación REST consultando prenda {} en ms-catalogo ({}): {}",
                    garmentId.value(), baseUrl, ex.getMessage());
            throw new CatalogServiceUnavailableException(
                    "El servicio de catálogo no está disponible temporalmente. Intente nuevamente más tarde.",
                    ex
            );
        } catch (IllegalArgumentException ex) {
            log.warn("Payload de prenda {} inválido o categoría no soportada desde ms-catalogo ({}): {}",
                    garmentId.value(), baseUrl, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String resolvePrimaryImageUrl(CatalogGarmentDto dto) {
        if (dto.fotografias() != null) {
            for (String candidate : dto.fotografias()) {
                if (candidate != null && !candidate.isBlank()) {
                    return CatalogGarment.sanitizeImageUrl(candidate);
                }
            }
        }
        return CatalogGarment.sanitizeImageUrl(dto.imageUrl());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogGarmentDto(
            @JsonProperty("id") @JsonAlias({"garmentId", "itemId", "prendaId"}) Object id,
            @JsonProperty("nombre") @JsonAlias({"name", "nombrePrenda", "titulo"}) String nombre,
            @JsonProperty("tipo") @JsonAlias({"category", "categoria", "tipoPrenda"}) String tipo,
            @JsonProperty("fotografias") @JsonAlias({"imagenes", "images", "photos"}) List<String> fotografias,
            @JsonProperty("imageUrl") @JsonAlias({"imagenUrl", "fotoUrl", "url", "imagen"}) String imageUrl,
            @JsonProperty("disponible") @JsonAlias({"visible", "activo", "activa"}) Boolean disponible,
            @JsonProperty("aprobada") @JsonAlias({"approved", "aprobado"}) Boolean aprobada,
            @JsonProperty("estado") @JsonAlias({"estadoModeracion", "moderationStatus", "moderationState"}) String estado,
            @JsonProperty("fechaPublicacion") @JsonAlias({"updatedAt", "fechaActualizacion", "timestamp"}) Object fechaPublicacion,
            @JsonProperty("tallas") @JsonAlias({"availableSizes", "sizes", "tallasDisponibles"}) Set<String> tallas
    ) {}
}
