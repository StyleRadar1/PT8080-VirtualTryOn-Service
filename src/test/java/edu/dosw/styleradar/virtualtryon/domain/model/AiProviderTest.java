package edu.dosw.styleradar.virtualtryon.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias para el enumerador de proveedores de IA AiProvider (SR-RF-25).
 */
class AiProviderTest {

    @ParameterizedTest
    @ValueSource(strings = {"fashn", "FASHN", "fashn_ai", "FASHN_AI", " fashn "})
    @DisplayName("HU SR-RF-25: Dado un valor configurado para FASHN, Cuando se analiza con fromConfigValue, Entonces resuelve FASHN_AI")
    void shouldResolveFashnAi(String value) {
        assertThat(AiProvider.fromConfigValue(value)).isEqualTo(AiProvider.FASHN_AI);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mock", "MOCK", "mock_local", "MOCK_LOCAL", " mock "})
    @DisplayName("HU SR-RF-25: Dado un valor configurado para MOCK, Cuando se analiza con fromConfigValue, Entonces resuelve MOCK_LOCAL")
    void shouldResolveMockLocal(String value) {
        assertThat(AiProvider.fromConfigValue(value)).isEqualTo(AiProvider.MOCK_LOCAL);
    }

    @Test
    @DisplayName("HU SR-RF-25: Dado un valor nulo o en blanco, Cuando se analiza con fromConfigValue, Entonces lanza IllegalArgumentException para exigir configuración explícita")
    void shouldThrowWhenBlankOrNull() {
        assertThatThrownBy(() -> AiProvider.fromConfigValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El proveedor de IA no esta configurado");

        assertThatThrownBy(() -> AiProvider.fromConfigValue("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El proveedor de IA no esta configurado");
    }

    @Test
    @DisplayName("HU SR-RF-25: Dado un proveedor desconocido, Cuando se analiza con fromConfigValue, Entonces lanza IllegalArgumentException")
    void shouldThrowWhenUnsupported() {
        assertThatThrownBy(() -> AiProvider.fromConfigValue("deepfake_ai"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Proveedor de IA no soportado");
    }
}

