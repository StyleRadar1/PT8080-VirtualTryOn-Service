package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import edu.dosw.styleradar.virtualtryon.domain.port.out.AiInferenceClientPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.strategy.FashnAiStrategy;
import edu.dosw.styleradar.virtualtryon.domain.strategy.MockAiStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de infraestructura para la instanciación de beans de estrategias de IA (Patrón Strategy).
 * Permite mantener las clases de estrategia en el dominio como POJOs puros sin anotaciones del framework Spring (SR-RF-25).
 */
@Configuration
public class AiStrategyConfig {

    @Bean
    public MockAiStrategy mockAiStrategy(BlobStoragePort blobStoragePort) {
        return new MockAiStrategy(blobStoragePort);
    }

    @Bean
    public FashnAiStrategy fashnAiStrategy(
            AiInferenceClientPort aiInferenceClientPort,
            BlobStoragePort blobStoragePort,
            MockAiStrategy mockAiStrategy
    ) {
        return new FashnAiStrategy(aiInferenceClientPort, blobStoragePort, mockAiStrategy);
    }
}

