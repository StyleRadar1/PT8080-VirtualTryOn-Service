package edu.dosw.styleradar.virtualtryon.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * DTO de solicitud para iniciar un trabajo asíncrono de probador virtual.
 */
@Schema(description = "Datos requeridos para despachar una prueba virtual con foto y prenda")
public record StartTryOnRequest(

        @Schema(description = "Identificador único del usuario comprador", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        @NotNull(message = "El identificador de usuario es obligatorio")
        UUID userId,

        @Schema(description = "Identificador único de la prenda a probar del catálogo", example = "f8e7d6c5-b4a3-2109-8765-43210fedcba9")
        @NotNull(message = "El identificador de la prenda es obligatorio")
        UUID garmentId,

        @Schema(description = "URL segura HTTPS de la fotografía frontal subida por el usuario", example = "https://styleradar.blob.core.windows.net/photos/user-front-123.jpg")
        @NotBlank(message = "La URL de la fotografía frontal es obligatoria")
        @Pattern(
                regexp = "^https?://[^\\s]+$",
                message = "La URL de la fotografía frontal debe utilizar el esquema HTTP o HTTPS válido (prevención SSRF)"
        )
        String photoUrl,

        @Schema(description = "Consentimiento explícito de tratamiento de datos biométricos según Ley 1581 de 2012", example = "true")
        @NotNull(message = "El consentimiento informado es obligatorio")
        Boolean biometricConsent
) {
}

