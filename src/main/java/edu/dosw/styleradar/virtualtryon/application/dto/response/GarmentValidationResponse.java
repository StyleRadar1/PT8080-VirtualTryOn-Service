package edu.dosw.styleradar.virtualtryon.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta con el resultado de validación de una prenda del catálogo para el Probador Virtual (SR-RF-24).
 */
@Schema(description = "Información y compatibilidad de una prenda del catálogo para prueba virtual")
public record GarmentValidationResponse(

        @Schema(description = "Identificador único de la prenda del catálogo", example = "f8e7d6c5-b4a3-2109-8765-43210fedcba9")
        UUID garmentId,

        @Schema(description = "Nombre comercial de la prenda", example = "Chaqueta Denim Clásica")
        String name,

        @Schema(description = "Categoría normalizada en el dominio", example = "TOPS")
        String category,

        @Schema(description = "Código de categoría compatible para el motor FASHN AI", example = "tops")
        String fashnCategoryCode,

        @Schema(description = "URL de la imagen principal de la prenda", example = "https://styleradar.blob.core.windows.net/catalog/garment-01.png")
        String imageUrl,

        @Schema(description = "Indica si la prenda es elegible y compatible con el Probador Virtual", example = "true")
        boolean eligibleForTryOn,

        @Schema(description = "Fecha de última sincronización desde el catálogo", example = "2026-11-08T11:30:00Z")
        Instant updatedAt
) {
}

