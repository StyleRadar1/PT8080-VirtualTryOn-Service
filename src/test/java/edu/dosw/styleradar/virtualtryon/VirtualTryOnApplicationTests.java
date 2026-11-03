package edu.dosw.styleradar.virtualtryon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de Integración: Verificación del arranque del contexto completo de Spring Boot.
 */
@SpringBootTest
@ActiveProfiles("test")
class VirtualTryOnApplicationTests {

    @Test
    @DisplayName("HU Transversal (Integración): Dado el microservicio PT8080-VirtualTryOn-Service configurado, Cuando inicia el contexto Spring Boot, Entonces levanta exitosamente todos los beans sin errores")
    void contextLoads() {
        // Dado: Configuración de componentes y dependencias del microservicio
        // Cuando: Se arranca el contexto de Spring Boot en perfil test
        // Entonces: El contenedor se inicializa sin fallas
    }

}
