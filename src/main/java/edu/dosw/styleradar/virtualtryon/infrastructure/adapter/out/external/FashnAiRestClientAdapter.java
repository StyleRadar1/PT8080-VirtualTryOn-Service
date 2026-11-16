package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import edu.dosw.styleradar.virtualtryon.domain.exception.AiInferencePermanentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.AiProviderTimeoutException;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.port.out.AiInferenceClientPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador de salida HTTP que consume la API REST de FASHN AI (modelo tryon-v1.6: POST /run y GET /status/{id})
 * migrado desde FashnProbadorIA de StyleRadar-Backend (SR-RF-25), protegido con Circuit Breaker (Resilience4j)
 * y timeout global acotado para cumplir el SLA de inferencia.
 */
@Component
public class FashnAiRestClientAdapter implements AiInferenceClientPort {

    private static final Logger log = LoggerFactory.getLogger(FashnAiRestClientAdapter.class);
    static final String MODEL_NAME = "tryon-v1.6";
    static final String CIRCUIT_BREAKER_NAME = "fashnAi";
    static final Duration DEFAULT_TOTAL_TIMEOUT = Duration.ofSeconds(25);

    private final RestClient restClient;
    private final String apiKey;
    private final Duration pollInterval;
    private final int maxAttempts;
    private final Duration totalTimeout;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    public FashnAiRestClientAdapter(
            @Value("${styleradar.tryon.fashn-api-url:https://api.fashn.ai/v1}") String apiUrl,
            @Value("${styleradar.tryon.fashn-api-key:}") String apiKey,
            @Value("${styleradar.tryon.fashn-total-timeout:PT25S}") Duration totalTimeout,
            ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider
    ) {
        this(
                RestClient.builder().baseUrl(apiUrl).requestFactory(buildRequestFactory()),
                apiKey,
                Duration.ofSeconds(2),
                13,
                totalTimeout,
                resolveCircuitBreaker(circuitBreakerRegistryProvider)
        );
    }

    public FashnAiRestClientAdapter(String apiUrl, String apiKey) {
        this(
                RestClient.builder().baseUrl(apiUrl).requestFactory(buildRequestFactory()),
                apiKey,
                Duration.ofSeconds(2),
                13,
                DEFAULT_TOTAL_TIMEOUT,
                buildDefaultCircuitBreaker()
        );
    }

    public FashnAiRestClientAdapter(
            RestClient.Builder builder,
            String apiKey,
            Duration pollInterval,
            int maxAttempts
    ) {
        this(builder, apiKey, pollInterval, maxAttempts, DEFAULT_TOTAL_TIMEOUT, buildDefaultCircuitBreaker());
    }

    public FashnAiRestClientAdapter(
            RestClient.Builder builder,
            String apiKey,
            Duration pollInterval,
            int maxAttempts,
            Duration totalTimeout,
            CircuitBreaker circuitBreaker
    ) {
        this.restClient = builder.build();
        this.apiKey = apiKey;
        this.pollInterval = pollInterval == null ? Duration.ZERO : pollInterval;
        this.maxAttempts = maxAttempts;
        this.totalTimeout = (totalTimeout == null || totalTimeout.isNegative() || totalTimeout.isZero())
                ? DEFAULT_TOTAL_TIMEOUT
                : totalTimeout;
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : buildDefaultCircuitBreaker();
    }

    @Override
    public Optional<byte[]> generateTryOnRender(String modelImageUrl, String garmentImageUrl, GarmentCategory category) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] renderBytes = circuitBreaker.executeSupplier(
                    () -> executeWithDeadline(modelImageUrl, garmentImageUrl, category)
            );
            return Optional.ofNullable(renderBytes);
        } catch (CallNotPermittedException ex) {
            log.warn("Circuit Breaker '{}' en estado OPEN; llamada a FASHN AI omitida temporalmente",
                    circuitBreaker.getName());
            return Optional.empty();
        } catch (AiInferencePermanentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Fallo transitorio al invocar FASHN AI para prueba virtual: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private byte[] executeWithDeadline(String modelImageUrl, String garmentImageUrl, GarmentCategory category) {
        Instant deadline = Instant.now().plus(totalTimeout);
        try {
            Map<?, ?> runResponse = restClient.post()
                    .uri("/run")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model_name", MODEL_NAME,
                            "inputs", Map.of(
                                    "model_image", modelImageUrl,
                                    "garment_image", garmentImageUrl,
                                    "category", resolveCategoryCode(category),
                                    "return_base64", true
                            )
                    ))
                    .retrieve()
                    .body(Map.class);

            Object executionId = runResponse == null ? null : runResponse.get("id");
            if (executionId == null) {
                log.warn("FASHN AI no devolvió un identificador de ejecución válido");
                throw new AiProviderTimeoutException("FASHN AI no devolvió un identificador de ejecución válido");
            }

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                sleepWithinDeadline(deadline);
                ensureWithinDeadline(deadline);
                Map<?, ?> statusResponse = restClient.get()
                        .uri("/status/{id}", executionId)
                        .header("Authorization", "Bearer " + apiKey)
                        .retrieve()
                        .body(Map.class);
                Object statusValue = statusResponse == null ? null : statusResponse.get("status");
                if ("completed".equals(statusValue)
                        && statusResponse.get("output") instanceof List<?> outputList
                        && !outputList.isEmpty()) {
                    log.info("Prueba virtual generada exitosamente con FASHN AI (ejecución {})", executionId);
                    return decodeBase64Output(String.valueOf(outputList.get(0)));
                }
                if ("failed".equals(statusValue) || "canceled".equals(statusValue)) {
                    String errorMsg = String.valueOf(statusResponse.get("error"));
                    log.error("FASHN AI finalizó definitivamente la ejecución {} con estado {}: {}",
                            executionId, statusValue, errorMsg);
                    throw new AiInferencePermanentException("Ejecución en FASHN AI rechazada (" + statusValue + "): " + errorMsg);
                }
            }
            log.warn("FASHN AI agotó los intentos de sondeo para la ejecución {}", executionId);
            throw new AiProviderTimeoutException("FASHN AI agotó los intentos de sondeo para la ejecución " + executionId);
        } catch (AiInferencePermanentException | AiProviderTimeoutException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderTimeoutException("Espera interrumpida al consultar estado en FASHN AI", ex);
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.is4xxClientError()) {
                log.error("Error definitivo HTTP {} devuelto por FASHN AI: {}", status, ex.getResponseBodyAsString());
                throw new AiInferencePermanentException("Credenciales o solicitud inválida en FASHN AI (HTTP " + status + ")", ex);
            }
            throw new AiProviderTimeoutException("Error HTTP " + status + " transitorio devuelto por FASHN AI", ex);
        } catch (RuntimeException ex) {
            throw new AiProviderTimeoutException("Error de comunicación con FASHN AI: " + ex.getMessage(), ex);
        }
    }

    private void sleepWithinDeadline(Instant deadline) throws InterruptedException {
        if (pollInterval.isZero() || pollInterval.isNegative()) {
            return;
        }
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new AiProviderTimeoutException("Timeout global de inferencia en FASHN AI excedido");
        }
        Duration sleepDuration = pollInterval.compareTo(remaining) <= 0 ? pollInterval : remaining;
        Thread.sleep(sleepDuration);
    }

    private void ensureWithinDeadline(Instant deadline) {
        if (!Instant.now().isBefore(deadline)) {
            throw new AiProviderTimeoutException("Timeout global de inferencia en FASHN AI excedido");
        }
    }

    private static CircuitBreaker resolveCircuitBreaker(ObjectProvider<CircuitBreakerRegistry> registryProvider) {
        CircuitBreakerRegistry registry = registryProvider != null ? registryProvider.getIfAvailable() : null;
        return registry != null ? registry.circuitBreaker(CIRCUIT_BREAKER_NAME) : buildDefaultCircuitBreaker();
    }

    private static CircuitBreaker buildDefaultCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .ignoreExceptions(AiInferencePermanentException.class)
                .build();
        return CircuitBreaker.of(CIRCUIT_BREAKER_NAME, config);
    }

    @Override
    public JobStatus queryExternalJobStatus(String externalJobId) {
        if (apiKey == null || apiKey.isBlank()) {
            return JobStatus.FAILED;
        }
        try {
            Map<?, ?> statusResponse = restClient.get()
                    .uri("/status/{id}", externalJobId)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(Map.class);
            Object statusValue = statusResponse == null ? null : statusResponse.get("status");
            if ("completed".equals(statusValue)) {
                return JobStatus.COMPLETED;
            }
            if ("failed".equals(statusValue) || "canceled".equals(statusValue)) {
                return JobStatus.FAILED;
            }
            return JobStatus.PROCESSING;
        } catch (RuntimeException ex) {
            log.warn("No fue posible consultar estado externo en FASHN AI ({}): {}", externalJobId, ex.getMessage());
            return JobStatus.FAILED;
        }
    }

    public static String resolveCategoryCode(GarmentCategory category) {
        if (category == null) {
            return "auto";
        }
        return category.getFashnCategoryCode();
    }

    private static byte[] decodeBase64Output(String rawOutput) {
        int commaIndex = rawOutput.indexOf(',');
        String base64Payload = commaIndex >= 0 ? rawOutput.substring(commaIndex + 1) : rawOutput;
        return Base64.getDecoder().decode(base64Payload);
    }

    private static SimpleClientHttpRequestFactory buildRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}

