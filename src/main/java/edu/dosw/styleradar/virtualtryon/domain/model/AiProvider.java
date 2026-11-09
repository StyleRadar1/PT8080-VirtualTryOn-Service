package edu.dosw.styleradar.virtualtryon.domain.model;

import java.util.Locale;

/**
 * Proveedores de inferencia de IA soportados mediante el patrón Strategy (SR-RF-25).
 */
public enum AiProvider {
    FASHN_AI,
    MOCK_LOCAL;

    public static AiProvider fromConfigValue(String rawProvider) {
        if (rawProvider == null || rawProvider.isBlank()) {
            throw new IllegalArgumentException(
                    "El proveedor de IA no esta configurado. Configure 'styleradar.tryon.ai-provider' explicitamente (fashn o mock)"
            );
        }
        String normalized = rawProvider.trim().toUpperCase(Locale.ROOT);
        if ("FASHN".equals(normalized) || "FASHN_AI".equals(normalized)) {
            return FASHN_AI;
        }
        if ("MOCK".equals(normalized) || "MOCK_LOCAL".equals(normalized)) {
            return MOCK_LOCAL;
        }
        throw new IllegalArgumentException(
                "Proveedor de IA no soportado: " + rawProvider + ". Valores permitidos: fashn, mock"
        );
    }
}

