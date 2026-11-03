package edu.dosw.styleradar.virtualtryon.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta con el saldo de cuotas mensuales de prueba virtual.
 */
@Schema(description = "Saldo y uso de cuotas de prueba virtual del usuario para el período")
public record CuotasUsuarioResponse(

        @Schema(description = "Identificador del usuario", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        UUID userId,

        @Schema(description = "Límite máximo de pruebas mensuales según su plan", example = "10")
        int monthlyLimit,

        @Schema(description = "Pruebas virtuales consumidas en el período actual", example = "3")
        int usedCount,

        @Schema(description = "Pruebas virtuales restantes disponibles", example = "7")
        int remainingCount,

        @Schema(description = "Período de facturación (año-mes)", example = "2026-11")
        String billingPeriod,

        @Schema(description = "Fecha y hora de última consulta", example = "2026-11-02T10:45:00Z")
        Instant lastCheckedAt
) {
}
