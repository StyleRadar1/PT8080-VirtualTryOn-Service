package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import edu.dosw.styleradar.virtualtryon.domain.port.out.AiInferenceClientPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.strategy.FashnAiStrategy;
import edu.dosw.styleradar.virtualtryon.domain.strategy.MockAiStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias para la configuración de estrategias de IA AiStrategyConfig (SR-RF-25).
 */
class AiStrategyConfigTest {

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
}

