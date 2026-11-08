package edu.dosw.styleradar.virtualtryon.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de salida con el estado y trazabilidad legal del consentimiento biométrico del usuario (SR-RF-23).
 */
@Schema(description = "Respuesta con la información de auditoría del consentimiento biométrico del usuario")
public record BiometricConsentResponse(

        @Schema(description = "Identificador único del registro de consentimiento", example = "7f8e9d10-1234-4a5b-8c9d-0123456789ab")
        UUID consentId,

        @Schema(description = "Identificador único del usuario", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID userId,

        @Schema(description = "Versión de los términos aceptados", example = "v1.0-LEY1581-2026")
        String termsVersion,

        @Schema(description = "Indica si el consentimiento fue aceptado", example = "true")
        boolean accepted,

        @Schema(description = "Indica si el consentimiento está vigente para procesar imágenes", example = "true")
        boolean validForInference,

        @Schema(description = "Finalidades autorizadas", example = "[\"VIRTUAL_TRY_ON_INFERENCE\"]")
        List<String> purposes,

        @Schema(description = "Fecha y hora de otorgamiento en UTC", example = "2026-11-07T22:00:00Z")
        Instant consentedAt,

        @Schema(description = "Fecha y hora de revocación en UTC (nulo si continúa activo)", nullable = true)
        Instant revokedAt
) {
}

