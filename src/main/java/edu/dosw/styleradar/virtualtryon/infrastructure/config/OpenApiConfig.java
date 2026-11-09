package edu.dosw.styleradar.virtualtryon.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración centralizada de SpringDoc OpenAPI 3.0 / Swagger UI.
 * Expone la documentación interactiva en /swagger-ui/index.html y el contrato JSON en /v3/api-docs.
 * Squad Portal8080 - DOSW 2026.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("STYLE RADAR · PT8080-VirtualTryOn-Service API")
                        .version("v1.0.0")
                        .description("API REST oficial del microservicio de Probador Virtual con IA (ms-probador-virtual · Puerto 8500).\n\n" +
                                "**Squad Portal8080 (DOSW 2026)**.\n\n" +
                                "Responsable de la orquestación asíncrona de ensayos de prendas mediante modelos generativos " +
                                "(Virtual Try-On), control estricto de cuotas mensuales de usuario y cumplimiento riguroso de " +
                                "privacidad de datos biométricos (Habeas Data - Ley 1581 de 2012).")
                        .contact(new Contact()
                                .name("Squad Portal8080 (PT8080) - José Alejandro")
                                .email("josedev@styleradar.dosw.edu")
                                .url("https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8500")
                                .description("Servidor Local de Desarrollo (Acceso Directo)"),
                        new Server()
                                .url("http://localhost:8080/api/v1/try-on")
                                .description("Acceso a través del API Gateway Central")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Ingrese el token JWT obtenido del microservicio de autenticación")));
    }
}
