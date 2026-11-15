package edu.dosw.styleradar.virtualtryon.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de respuesta con el resultado de validación de una prenda del catálogo para el Probador Virtual (SR-RF-24).
 */
@Schema(description = "Información y compatibilidad de una prenda del catálogo para prueba virtual")
public record GarmentValidationResponse(

        @Schema(description = "Identificador único de la prenda del catálogo", example = "f8e7d6c5-b4a3-2109-8765-43210fedcba9")
        UUID garmentId,

        @Schema(description = "Nombre comercial de la prenda", example = "Camisa Denim Clásica")
        String name,

        @Schema(description = "Categoría normalizada en el dominio", example = "TOP")
        String category,

        @Schema(description = "Código de categoría compatible para el motor FASHN AI", example = "tops")
        String fashnCategoryCode,

        @Schema(description = "URL de la imagen principal de la prenda", example = "https://styleradar.blob.core.windows.net/catalog/garment-01.png")
        String imageUrl,

        @Schema(description = "Indica si la categoría de la prenda admite superposición (TOP, BOTTOM, ONE_PIECE)", example = "true")
        boolean compatible,

        @Schema(description = "Indica si la prenda está visible y disponible en el catálogo", example = "true")
        boolean visible,

        @Schema(description = "Zona corporal asignada (UPPER_BODY, LOWER_BODY, FULL_BODY) o null si es incompatible", example = "UPPER_BODY")
        String zone,

        @Schema(description = "Indica si la prenda es elegible para prueba virtual (compatible y visible)", example = "true")
        boolean eligibleForTryOn,

        @Schema(description = "Mensaje descriptivo sobre compatibilidad, disponibilidad o conflicto de reemplazo", example = "Prenda compatible y disponible para prueba virtual en zona UPPER_BODY.")
        String validationMessage,

        @Schema(description = "Identificadores de prendas seleccionadas previamente que serían reemplazadas por conflicto de zona (RN-02 / FA-04)")
        List<UUID> replacedGarmentIds,

        @Schema(description = "Fecha de última sincronización desde el catálogo", example = "2026-11-08T11:30:00Z")
        Instant updatedAt
) {

    public GarmentValidationResponse(
            UUID garmentId,
            String name,
            String category,
            String fashnCategoryCode,
            String imageUrl,
            boolean eligibleForTryOn,
            Instant updatedAt
    ) {
        this(garmentId, name, category, fashnCategoryCode, imageUrl, eligibleForTryOn, true, null, eligibleForTryOn, null, List.of(), updatedAt);
    }
}

