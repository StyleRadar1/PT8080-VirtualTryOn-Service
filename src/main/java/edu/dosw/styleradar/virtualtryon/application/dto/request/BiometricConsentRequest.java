package edu.dosw.styleradar.virtualtryon.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada para otorgar o registrar el consentimiento de tratamiento de datos biométricos
 * conforme a la Ley 1581 de 2012 (SR-RF-23 / SR-RNF-02).
 */
@Schema(description = "Solicitud para otorgar el consentimiento informado de tratamiento de datos biométricos (Ley 1581 de 2012)")
public record BiometricConsentRequest(

        @Schema(description = "Identificador único del usuario", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "El identificador del usuario (userId) es obligatorio")
        UUID userId,

        @Schema(description = "Versión de los términos y condiciones aceptados", example = "v1.0-LEY1581-2026")
        @NotBlank(message = "La versión de los términos legales (termsVersion) es obligatoria")
        String termsVersion,

        @Schema(description = "Confirmación explícita de aceptación del tratamiento biométrico", example = "true")
        @NotNull(message = "El indicador de aceptación (accepted) es obligatorio")
        Boolean accepted,

        @Schema(description = "Finalidades autorizadas para el tratamiento de la silueta", example = "[\"VIRTUAL_TRY_ON_INFERENCE\", \"TEMPORARY_SILHOUETTE_STORAGE\"]")
        List<String> purposes,

        @Schema(description = "Dirección IP desde la cual se otorga el consentimiento", example = "190.24.112.45")
        String ipAddress
) {
}

