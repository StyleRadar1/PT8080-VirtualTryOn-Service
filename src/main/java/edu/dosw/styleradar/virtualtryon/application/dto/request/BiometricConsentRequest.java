package edu.dosw.styleradar.virtualtryon.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada para otorgar o registrar el consentimiento de tratamiento de datos biométricos
 * conforme a la Ley 1581 de 2012 (SR-RF-23 / SR-RNF-02).
 * El identificador de usuario es opcional (se infiere del JWT autenticado) y admite tanto
 * UUID estándar como IDs numéricos emitidos por Auth.
 */
@Schema(description = "Solicitud para otorgar el consentimiento informado de tratamiento de datos biométricos (Ley 1581 de 2012)")
public record BiometricConsentRequest(

        @Schema(description = "Identificador del usuario (opcional si se infiere del JWT autenticado; acepta UUID o ID numérico de Auth)", example = "12345")
        Object userId,

        @Schema(description = "Versión de los términos y condiciones aceptados", example = "v1.0-LEY1581-2026")
        @NotBlank(message = "La versión de los términos legales (termsVersion) es obligatoria")
        String termsVersion,

        @Schema(description = "Confirmación explícita de aceptación del tratamiento biométrico", example = "true")
        @NotNull(message = "El indicador de aceptación (accepted) es obligatorio")
        Boolean accepted,

        @Schema(description = "Finalidades autorizadas para el tratamiento de la silueta", example = "[\"VIRTUAL_TRY_ON_INFERENCE\", \"TEMPORARY_SILHOUETTE_STORAGE\"]")
        List<String> purposes,

        @Schema(description = "Dirección IP informada por el cliente (ignorado por seguridad: la IP autoritativa se obtiene exclusivamente de la infraestructura confiable del servidor)", example = "190.24.112.45")
        String ipAddress
) {

    public BiometricConsentRequest(UUID userId, String termsVersion, Boolean accepted, List<String> purposes, String ipAddress) {
        this((Object) userId, termsVersion, accepted, purposes, ipAddress);
    }
}
