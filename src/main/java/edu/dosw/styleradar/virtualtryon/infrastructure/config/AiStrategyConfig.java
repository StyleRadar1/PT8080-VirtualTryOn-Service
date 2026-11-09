package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import edu.dosw.styleradar.virtualtryon.domain.port.out.AiInferenceClientPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.strategy.FashnAiStrategy;
import edu.dosw.styleradar.virtualtryon.domain.strategy.MockAiStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuración de infraestructura para la instanciación de beans de estrategias de IA (Patrón Strategy).
 * Permite mantener las clases de estrategia en el dominio como POJOs puros sin anotaciones del framework Spring (SR-RF-25).
 * El bean MockAiStrategy está estrictamente excluido del perfil de producción (!prod).
 */
@Configuration
public class AiStrategyConfig {

    @Bean
    @Profile("!prod")
    public MockAiStrategy mockAiStrategy(BlobStoragePort blobStoragePort) {
        return new MockAiStrategy(blobStoragePort);
    }

    @Bean
    public FashnAiStrategy fashnAiStrategy(
            AiInferenceClientPort aiInferenceClientPort,
            BlobStoragePort blobStoragePort
    ) {
        return new FashnAiStrategy(aiInferenceClientPort, blobStoragePort);
    }
}
