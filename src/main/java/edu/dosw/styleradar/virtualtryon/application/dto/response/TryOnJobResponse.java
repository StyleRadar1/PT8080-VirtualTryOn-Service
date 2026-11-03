package edu.dosw.styleradar.virtualtryon.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta con el estado y resultado del trabajo de Virtual Try-On.
 */
@Schema(description = "Información detallada del estado o resultado del ensayo virtual")
public record TryOnJobResponse(

        @Schema(description = "Identificador único del trabajo", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID jobId,

        @Schema(description = "Identificador del usuario", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        UUID userId,

        @Schema(description = "Identificador de la prenda probada", example = "f8e7d6c5-b4a3-2109-8765-43210fedcba9")
        UUID garmentId,

        @Schema(description = "Estado actual del procesamiento", example = "COMPLETADO", allowableValues = {"PENDIENTE", "PROCESANDO_IA", "COMPLETADO", "FALLIDO"})
        String status,

        @Schema(description = "URL de la imagen generada con la prenda ajustada (disponible al completar)", example = "https://styleradar.blob.core.windows.net/renders/result-3fa85f64.png")
        String resultImageUrl,

        @Schema(description = "Tiempo de inferencia en milisegundos", example = "2450")
        Long executionTimeMs,

        @Schema(description = "Detalle del error en caso de fallo", example = "null")
        String errorMessage,

        @Schema(description = "Fecha y hora de creación del trabajo", example = "2026-11-02T10:30:00Z")
        Instant createdAt,

        @Schema(description = "Fecha y hora de última actualización", example = "2026-11-02T10:30:03Z")
        Instant updatedAt
) {
}
