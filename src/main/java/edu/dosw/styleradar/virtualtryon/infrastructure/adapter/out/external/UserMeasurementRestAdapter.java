package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.dosw.styleradar.virtualtryon.domain.model.UserBodyMeasurements;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserMeasurementPort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;

/**
 * Adaptador de salida REST que consulta las medidas corporales autorizadas del usuario
 * en ms-usuarios (GET /api/v1/usuarios/{userId}/medidas) para la evaluación de talla (SR-RF-24).
 * Si el microservicio de usuarios no está disponible, no responde en tiempo o retorna 404,
 * degrada de manera segura retornando Optional.empty() según FA-02.
 */
@Component
public class UserMeasurementRestAdapter implements UserMeasurementPort {

    private static final Logger log = LoggerFactory.getLogger(UserMeasurementRestAdapter.class);

    private final RestClient restClient;
    private final String baseUrl;

    @Autowired
    public UserMeasurementRestAdapter(
            @Value("${styleradar.tryon.usuarios-service-url:http://localhost:8120}") String baseUrl
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

    public UserMeasurementRestAdapter(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public Optional<UserBodyMeasurements> findMeasurementsByUserId(UserId userId) {
        String targetId = UserId.resolveExternalIdentifier(userId);
        if (targetId == null || targetId.isBlank()) {
            return Optional.empty();
        }
        try {
            String authorizationHeader = resolveCurrentAuthorizationHeader();
            UserMeasurementDto dto = restClient.get()
                    .uri("/api/v1/usuarios/{userId}/medidas", targetId)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
                        }
                    })
                    .retrieve()
                    .body(UserMeasurementDto.class);

            if (dto == null) {
                return Optional.empty();
            }
            UserBodyMeasurements measurements = new UserBodyMeasurements(
                    dto.heightCm(),
                    dto.chestCm(),
                    dto.waistCm(),
                    dto.hipCm()
            );
            return Optional.of(measurements);
        } catch (Exception ex) {
            log.debug("No se pudieron obtener medidas autorizadas desde ms-usuarios ({}) para usuario {}: {}",
                    baseUrl, targetId, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String resolveCurrentAuthorizationHeader() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserMeasurementDto(
            @JsonProperty("heightCm") @JsonAlias({"altura", "estatura", "height"}) Double heightCm,
            @JsonProperty("chestCm") @JsonAlias({"pecho", "busto", "torso", "chest"}) Double chestCm,
            @JsonProperty("waistCm") @JsonAlias({"cintura", "waist"}) Double waistCm,
            @JsonProperty("hipCm") @JsonAlias({"cadera", "hip"}) Double hipCm
    ) {}
}
