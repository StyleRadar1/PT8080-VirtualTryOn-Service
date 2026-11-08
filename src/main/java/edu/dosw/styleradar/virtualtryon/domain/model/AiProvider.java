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
            return MOCK_LOCAL;
        }
        String normalized = rawProvider.trim().toUpperCase(Locale.ROOT);
        if ("FASHN".equals(normalized) || "FASHN_AI".equals(normalized)) {
            return FASHN_AI;
        }
        return MOCK_LOCAL;
    }
}

