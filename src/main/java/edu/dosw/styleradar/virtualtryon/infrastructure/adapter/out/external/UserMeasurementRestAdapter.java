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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Adaptador de salida REST que consulta las medidas corporales autorizadas del usuario
 * en ms-usuarios (por defecto {@code GET /api/v1/internal/usuarios/{userId}/medidas})
 * para la evaluación de talla (SR-RF-24).
 * Autentica preferentemente mediante HTTP Basic Auth ({@code USUARIOS_INTERNAL_AUTH_USERNAME} y
 * {@code USUARIOS_INTERNAL_AUTH_PASSWORD}) o, en ausencia de credenciales internas, reenvía el
 * encabezado {@code Authorization} de la petición entrante.
 * Si el microservicio de usuarios no está disponible, no responde en tiempo o retorna 4xx/5xx,
 * degrada de manera segura retornando Optional.empty() según FA-02.
 */
@Component
public class UserMeasurementRestAdapter implements UserMeasurementPort {

    private static final Logger log = LoggerFactory.getLogger(UserMeasurementRestAdapter.class);
    public static final String DEFAULT_MEASUREMENTS_PATH = "/api/v1/internal/usuarios/{userId}/medidas";

    private final RestClient restClient;
    private final String baseUrl;
    private final String measurementsPath;
    private final String internalAuthUsername;
    private final String internalAuthPassword;

    @Autowired
    public UserMeasurementRestAdapter(
            @Value("${styleradar.tryon.usuarios-service-url:http://localhost:8120}") String baseUrl,
            @Value("${styleradar.tryon.usuarios-measurements-path:" + DEFAULT_MEASUREMENTS_PATH + "}") String measurementsPath,
            @Value("${styleradar.tryon.usuarios-internal-auth-username:}") String internalAuthUsername,
            @Value("${styleradar.tryon.usuarios-internal-auth-password:}") String internalAuthPassword
    ) {
        this(
                buildDefaultRestClient(baseUrl),
                baseUrl,
                measurementsPath,
                internalAuthUsername,
                internalAuthPassword
        );
    }

    public UserMeasurementRestAdapter(String baseUrl) {
        this(baseUrl, DEFAULT_MEASUREMENTS_PATH, null, null);
    }

    public UserMeasurementRestAdapter(RestClient restClient, String baseUrl) {
        this(restClient, baseUrl, DEFAULT_MEASUREMENTS_PATH, null, null);
    }

    public UserMeasurementRestAdapter(
            RestClient restClient,
            String baseUrl,
            String measurementsPath,
            String internalAuthUsername,
            String internalAuthPassword
    ) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.measurementsPath = normalizeMeasurementsPath(measurementsPath);
        this.internalAuthUsername = trimToNull(internalAuthUsername);
        this.internalAuthPassword = trimToNull(internalAuthPassword);
    }

    @Override
    public Optional<UserBodyMeasurements> findMeasurementsByUserId(UserId userId) {
        String targetId = UserId.resolveExternalIdentifier(userId);
        if (targetId == null || targetId.isBlank()) {
            return Optional.empty();
        }
        try {
            UserMeasurementDto dto = restClient.get()
                    .uri(measurementsPath, targetId)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (hasInternalBasicAuth()) {
                            headers.setBasicAuth(internalAuthUsername, internalAuthPassword, StandardCharsets.UTF_8);
                        } else {
                            String authorizationHeader = resolveCurrentAuthorizationHeader();
                            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                                headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
                            }
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

    private static RestClient buildDefaultRestClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();
    }

    private static String normalizeMeasurementsPath(String path) {
        if (path == null || path.isBlank()) {
            return DEFAULT_MEASUREMENTS_PATH;
        }
        return path.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean hasInternalBasicAuth() {
        return internalAuthUsername != null && internalAuthPassword != null;
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
