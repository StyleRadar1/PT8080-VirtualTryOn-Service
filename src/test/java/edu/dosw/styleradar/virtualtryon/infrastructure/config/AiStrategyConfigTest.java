package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import edu.dosw.styleradar.virtualtryon.domain.port.out.AiInferenceClientPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.strategy.FashnAiStrategy;
import edu.dosw.styleradar.virtualtryon.domain.strategy.MockAiStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias para la configuración de estrategias de IA AiStrategyConfig (SR-RF-25).
 * Valida la exclusión mutua de perfiles (dev/test/swagger -> Mock, qa/prod -> Fashn)
 * evitando fallbacks silenciosos entre entornos.
 */
class AiStrategyConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiStrategyConfig.class)
            .withBean(BlobStoragePort.class, () -> Mockito.mock(BlobStoragePort.class))
            .withBean(AiInferenceClientPort.class, () -> Mockito.mock(AiInferenceClientPort.class));

    @Test
    @DisplayName("HU SR-RF-25: Dado AiStrategyConfig, Cuando se inicializan los beans, Entonces instancia MockAiStrategy y FashnAiStrategy correctamente")
    void shouldCreateStrategyBeansCorrectly() {
        AiStrategyConfig config = new AiStrategyConfig();
        BlobStoragePort blobStoragePort = Mockito.mock(BlobStoragePort.class);
        AiInferenceClientPort aiInferenceClientPort = Mockito.mock(AiInferenceClientPort.class);

        MockAiStrategy mockAiStrategy = config.mockAiStrategy(blobStoragePort);
        FashnAiStrategy fashnAiStrategy = config.fashnAiStrategy(aiInferenceClientPort, blobStoragePort);

        assertThat(mockAiStrategy).isNotNull();
        assertThat(fashnAiStrategy).isNotNull();
    }

    @Test
    @DisplayName("HU SR-RF-25: En perfiles dev, test y swagger se registra exclusivamente MockAiStrategy")
    void shouldRegisterMockStrategyInDevTestSwaggerProfiles() {
        for (String profile : List.of("dev", "test", "swagger")) {
            contextRunner.withPropertyValues("spring.profiles.active=" + profile)
                    .run(context -> {
                        assertThat(context).hasSingleBean(MockAiStrategy.class);
                        assertThat(context).doesNotHaveBean(FashnAiStrategy.class);
                    });
        }
    }

    @Test
    @DisplayName("HU SR-RF-25: En perfiles qa y prod se registra exclusivamente FashnAiStrategy")
    void shouldRegisterFashnStrategyInQaAndProdProfiles() {
        for (String profile : List.of("qa", "prod")) {
            contextRunner.withPropertyValues("spring.profiles.active=" + profile)
                    .run(context -> {
                        assertThat(context).hasSingleBean(FashnAiStrategy.class);
                        assertThat(context).doesNotHaveBean(MockAiStrategy.class);
                    });
        }
    }
}

