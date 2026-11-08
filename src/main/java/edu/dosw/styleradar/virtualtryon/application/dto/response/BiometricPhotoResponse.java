package edu.dosw.styleradar.virtualtryon.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida con los metadatos validados de la fotografía frontal cargada por el usuario (SR-RF-23).
 */
@Schema(description = "Respuesta con los metadatos y URL firmada de la fotografía frontal de silueta cargada (SR-RF-23)")
public record BiometricPhotoResponse(

        @Schema(description = "Identificador único del registro de fotografía", example = "9a8b7c6d-5432-4f1e-9d8c-76543210fedc")
        UUID photoId,

        @Schema(description = "Identificador único del usuario propietario", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID userId,

        @Schema(description = "Formato MIME validado de la fotografía", example = "image/png")
        String contentType,

        @Schema(description = "Ancho de la fotografía en píxeles (mínimo 300 px)", example = "600")
        int width,

        @Schema(description = "Alto de la fotografía en píxeles (mínimo 400 px, orientación vertical)", example = "900")
        int height,

        @Schema(description = "Tamaño del archivo en bytes (máximo 5 MB)", example = "245760")
        long sizeBytes,

        @Schema(description = "URL firmada con SAS Token del objeto en Azure Blob Storage", example = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/550e8400/pose.png?sig=sas-token")
        String photoUrl,

        @Schema(description = "Fecha y hora de carga en UTC", example = "2026-11-07T22:05:00Z")
        Instant uploadedAt
) {
}

