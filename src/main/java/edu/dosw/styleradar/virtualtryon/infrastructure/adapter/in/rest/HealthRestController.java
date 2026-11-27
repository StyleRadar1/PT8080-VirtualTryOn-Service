package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoint de verificación de salud y vitalidad para Smoke Tests en QA y PROD.
 */
@RestController
@Tag(name = "Health", description = "Verificación de estado de salud del servicio")
public class HealthRestController {

    @GetMapping({"/api/v1/health", "/api/v1/try-on/health"})
    @Operation(summary = "Comprueba el estado de vitalidad del servicio")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "PT8080-VirtualTryOn-Service",
                "timestamp", Instant.now().toString()
        ));
    }
}
