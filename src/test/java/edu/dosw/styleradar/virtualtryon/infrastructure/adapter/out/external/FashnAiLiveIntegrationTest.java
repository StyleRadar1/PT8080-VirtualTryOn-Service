package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración viva contra la API real de FASHN AI (https://api.fashn.ai/v1) (SR-RF-25).
 * Se activa únicamente cuando la variable de entorno FASHN_LIVE_API_KEY está definida en el host o pipeline.
 */
class FashnAiLiveIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "FASHN_LIVE_API_KEY", matches = ".+")
    @DisplayName("HU SR-RF-25 (Integración Real FASHN AI): Envía solicitud real a api.fashn.ai y valida retorno de imagen generada")
    void shouldExecuteRealInferenceOnFashnAi() {
        String liveApiKey = System.getenv("FASHN_LIVE_API_KEY");
        FashnAiRestClientAdapter adapter = new FashnAiRestClientAdapter(
                RestClient.builder().baseUrl("https://api.fashn.ai/v1"),
                liveApiKey,
                Duration.ofSeconds(3),
                20
        );

        String modelUrl = "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=800&q=80";
        String garmentUrl = "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?auto=format&fit=crop&w=800&q=80";

        Optional<byte[]> renderedBytes = adapter.generateTryOnRender(modelUrl, garmentUrl, GarmentCategory.TOPS);

        assertThat(renderedBytes).isPresent();
        assertThat(renderedBytes.get().length).isGreaterThan(10000);
    }
}

