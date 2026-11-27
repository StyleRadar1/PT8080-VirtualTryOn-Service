package edu.dosw.styleradar.virtualtryon.application.dto.request;

import edu.dosw.styleradar.virtualtryon.infrastructure.validation.ValidPhotoUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO de solicitud para iniciar un trabajo asíncrono de probador virtual.
 * El userId es opcional: si el cliente no lo envía, se extrae de forma autoritativa
 * desde el JWT del usuario autenticado (SR-RF-24).
 * Acepta tanto identificadores canónicos UUID como IDs numéricos emitidos por Auth.
 */
@Schema(description = "Datos requeridos para despachar una prueba virtual con foto y prenda")
public record StartTryOnRequest(

        @Schema(description = "Identificador opcional del usuario comprador (si se omite, se infiere del JWT autenticado; acepta UUID o ID numérico de Auth)", example = "12345")
        Object userId,

        @Schema(description = "Identificador único de la prenda a probar del catálogo (acepta UUID o ID numérico/texto de Catálogo)", example = "f8e7d6c5-b4a3-2109-8765-43210fedcba9")
        @NotNull(message = "El identificador de la prenda es obligatorio")
        Object garmentId,

        @Schema(description = "URL segura HTTPS de la fotografía frontal subida por el usuario", example = "https://styleradarpt8080blob.blob.core.windows.net/biometric-silhouettes-qa/user-front-123.jpg")
        @NotBlank(message = "La URL de la fotografía frontal es obligatoria")
        @ValidPhotoUrl
        String photoUrl,

        @Schema(description = "Consentimiento explícito de tratamiento de datos biométricos según Ley 1581 de 2012", example = "true")
        @NotNull(message = "El consentimiento informado es obligatorio")
        Boolean biometricConsent
) {

    public StartTryOnRequest(UUID userId, UUID garmentId, String photoUrl, Boolean biometricConsent) {
        this((Object) userId, (Object) garmentId, photoUrl, biometricConsent);
    }

    public StartTryOnRequest(Object garmentId, String photoUrl, Boolean biometricConsent) {
        this(null, garmentId, photoUrl, biometricConsent);
    }
}
