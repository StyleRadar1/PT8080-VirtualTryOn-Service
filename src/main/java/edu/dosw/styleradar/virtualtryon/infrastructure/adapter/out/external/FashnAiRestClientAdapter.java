package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.port.out.AiInferenceClientPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador de salida HTTP que consume la API REST de FASHN AI (modelo tryon-v1.6: POST /run y GET /status/{id})
 * migrado desde FashnProbadorIA de StyleRadar-Backend (SR-RF-25).
 */
@Component
public class FashnAiRestClientAdapter implements AiInferenceClientPort {

    private static final Logger log = LoggerFactory.getLogger(FashnAiRestClientAdapter.class);
    static final String MODEL_NAME = "tryon-v1.6";

    private final RestClient restClient;
    private final String apiKey;
    private final Duration pollInterval;
    private final int maxAttempts;

    @Autowired
    public FashnAiRestClientAdapter(
            @Value("${styleradar.tryon.fashn-api-url:https://api.fashn.ai/v1}") String apiUrl,
            @Value("${styleradar.tryon.fashn-api-key:}") String apiKey
    ) {
        this(
                RestClient.builder().baseUrl(apiUrl).requestFactory(buildRequestFactory()),
                apiKey,
                Duration.ofSeconds(2),
                13
        );
    }

    public FashnAiRestClientAdapter(
            RestClient.Builder builder,
            String apiKey,
            Duration pollInterval,
            int maxAttempts
    ) {
        this.restClient = builder.build();
        this.apiKey = apiKey;
        this.pollInterval = pollInterval;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public Optional<byte[]> generateTryOnRender(String modelImageUrl, String garmentImageUrl, GarmentCategory category) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
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
                return Optional.empty();
            }

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                if (!pollInterval.isZero() && !pollInterval.isNegative()) {
                    Thread.sleep(pollInterval);
                }
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
                    return Optional.of(decodeBase64Output(String.valueOf(outputList.get(0))));
                }
                if ("failed".equals(statusValue) || "canceled".equals(statusValue)) {
                    log.warn("FASHN AI finalizó la ejecución {} con estado {}: {}",
                            executionId, statusValue, statusResponse.get("error"));
                    return Optional.empty();
                }
            }
            log.warn("FASHN AI agotó los intentos de sondeo para la ejecución {}", executionId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ex) {
            log.warn("Error al invocar FASHN AI para prueba virtual: {}", ex.getMessage());
        }
        return Optional.empty();
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
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}

