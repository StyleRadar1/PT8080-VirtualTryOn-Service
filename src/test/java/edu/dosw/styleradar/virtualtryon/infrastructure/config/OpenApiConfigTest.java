package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba Unitaria de Configuración de Documentación OpenAPI / Swagger.
 */
class OpenApiConfigTest {

    @Test
    @DisplayName("HU Transversal (Contrato): Dado el bean de configuración OpenApiConfig, Cuando se inicializa, Entonces genera la metadata de StyleRadar y el esquema de seguridad Bearer JWT")
    void testOpenApi() {
        // Dado: La configuración de SpringDoc OpenAPI
        OpenApiConfig config = new OpenApiConfig();

        // Cuando: Se genera la instancia OpenAPI
        OpenAPI api = config.customOpenAPI();

        // Entonces: Contiene el título oficial, servidor relativo "/" primero y sin rutas duplicadas
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getTitle()).contains("PT8080-VirtualTryOn-Service");
        assertThat(api.getServers()).isNotEmpty();
        assertThat(api.getServers().get(0).getUrl()).isEqualTo("/");
        assertThat(api.getServers())
                .extracting(io.swagger.v3.oas.models.servers.Server::getUrl)
                .contains(
                        "https://styleradar-virtualtryon-qa.azurewebsites.net",
                        "https://styleradar-gateway-qa.azurewebsites.net",
                        "https://styleradar-virtualtryon-prod.azurewebsites.net",
                        "https://styleradar-gateway-prod.azurewebsites.net"
                )
                .noneMatch(url -> url.endsWith("/api/v1/try-on"));
    }
}
