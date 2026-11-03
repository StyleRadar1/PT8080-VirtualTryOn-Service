package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import edu.dosw.styleradar.virtualtryon.application.dto.request.IniciarTryOnRequest;
import edu.dosw.styleradar.virtualtryon.application.dto.response.CuotasUsuarioResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.TryOnJobResponse;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.doc.VirtualTryOnOpenApiDoc;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Adaptador primario (Driving) REST para el microservicio de Probador Virtual.
 * Implementa la interfaz de documentación OpenAPI VirtualTryOnOpenApiDoc.
 * Expone endpoints bajo el prefijo /api/v1/probador con respuestas de contrato para Swagger UI.
 */
@RestController
@RequestMapping("/api/v1/probador")
public class VirtualTryOnRestController implements VirtualTryOnOpenApiDoc {

    @Override
    @PostMapping("/jobs")
    public ResponseEntity<TryOnJobResponse> iniciarTryOn(@Valid @RequestBody IniciarTryOnRequest request) {
        UUID newJobId = UUID.randomUUID();
        TryOnJobResponse response = new TryOnJobResponse(
                newJobId,
                request.userId(),
                request.garmentId(),
                "PENDIENTE",
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        URI location = URI.create("/api/v1/probador/jobs/" + newJobId);
        return ResponseEntity.accepted().location(location).body(response);
    }

    @Override
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<TryOnJobResponse> consultarJob(@PathVariable UUID jobId) {
        TryOnJobResponse response = new TryOnJobResponse(
                jobId,
                UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"),
                UUID.fromString("f8e7d6c5-b4a3-2109-8765-43210fedcba9"),
                "COMPLETADO",
                "https://styleradar.blob.core.windows.net/renders/render-" + jobId + ".png",
                2100L,
                null,
                Instant.now().minusSeconds(10),
                Instant.now()
        );
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/cuotas")
    public ResponseEntity<CuotasUsuarioResponse> consultarCuotas(@RequestParam(required = false) UUID userId) {
        UUID targetUserId = (userId != null) ? userId : UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d");
        CuotasUsuarioResponse response = new CuotasUsuarioResponse(
                targetUserId,
                10,
                3,
                7,
                "2026-11",
                Instant.now()
        );
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/fotos-personales/{jobId}")
    public ResponseEntity<Void> borrarFotoBiometrica(@PathVariable UUID jobId) {
        return ResponseEntity.noContent().build();
    }
}
