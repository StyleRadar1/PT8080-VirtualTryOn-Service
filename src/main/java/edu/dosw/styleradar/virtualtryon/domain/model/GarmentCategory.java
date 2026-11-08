package edu.dosw.styleradar.virtualtryon.domain.model;

import java.util.Locale;

/**
 * Enumeración de categorías de prendas del catálogo y su compatibilidad con el motor de prueba virtual (SR-RF-24).
 */
public enum GarmentCategory {

    TOPS("tops", true),
    BOTTOMS("bottoms", true),
    ONE_PIECES("one-pieces", true),
    OUTERWEAR("tops", true),
    FOOTWEAR("auto", false),
    ACCESSORIES("auto", false);

    private final String fashnCategoryCode;
    private final boolean compatibleWithTryOn;

    GarmentCategory(String fashnCategoryCode, boolean compatibleWithTryOn) {
        this.fashnCategoryCode = fashnCategoryCode;
        this.compatibleWithTryOn = compatibleWithTryOn;
    }

    public String getFashnCategoryCode() {
        return fashnCategoryCode;
    }

    public boolean isCompatibleWithTryOn() {
        return compatibleWithTryOn;
    }

    /**
     * Convierte una categoría textual proveniente de eventos de catálogo o peticiones HTTP
     * (en inglés o español del monolito StyleRadar) hacia la enumeración de dominio.
     */
    public static GarmentCategory fromRawValue(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            throw new IllegalArgumentException("La categoría de la prenda no puede ser nula o vacía");
        }
        String normalized = rawCategory.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "TOPS", "SUPERIOR", "CAMISA", "CAMISETA", "BLUSA" -> TOPS;
            case "BOTTOMS", "INFERIOR", "PANTALON", "FALDA" -> BOTTOMS;
            case "ONE_PIECES", "ONEPIECES", "VESTIDO", "ENTERIZO" -> ONE_PIECES;
            case "OUTERWEAR", "ABRIGO", "CHAQUETA" -> OUTERWEAR;
            case "FOOTWEAR", "CALZADO", "ZAPATOS" -> FOOTWEAR;
            case "ACCESSORIES", "ACCESORIO", "ACCESORIOS" -> ACCESSORIES;
            default -> throw new IllegalArgumentException("Categoría de prenda desconocida: " + rawCategory);
        };
    }
}

