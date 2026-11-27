package edu.dosw.styleradar.virtualtryon.domain.model;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Enumeración de categorías de prendas del catálogo y su compatibilidad con el motor de prueba virtual (SR-RF-24).
 */
public enum GarmentCategory {

    TOP("tops", BodyZone.UPPER_BODY, true),
    BOTTOM("bottoms", BodyZone.LOWER_BODY, true),
    ONE_PIECE("one-pieces", BodyZone.FULL_BODY, true),
    OUTERWEAR("auto", null, false),
    FOOTWEAR("auto", null, false),
    ACCESSORIES("auto", null, false);

    private final String fashnCategoryCode;
    private final BodyZone bodyZone;
    private final boolean compatibleWithTryOn;

    GarmentCategory(String fashnCategoryCode, BodyZone bodyZone, boolean compatibleWithTryOn) {
        this.fashnCategoryCode = fashnCategoryCode;
        this.bodyZone = bodyZone;
        this.compatibleWithTryOn = compatibleWithTryOn;
    }

    public String getFashnCategoryCode() {
        return fashnCategoryCode;
    }

    public BodyZone getBodyZone() {
        return bodyZone;
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
        String normalized = Normalizer.normalize(rawCategory.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "TOP", "TOPS", "UPPER", "UPPER_BODY", "SUPERIOR", "PRENDA_SUPERIOR",
                    "CAMISA", "CAMISAS", "CAMISETA", "CAMISETAS", "BLUSA", "BLUSAS",
                    "POLO", "SACO", "SACOS", "BLAZER", "BLAZERS",
                    "SUETER", "SWEATER", "HOODIE", "BUSO", "BUZO", "CHALECO" -> TOP;
            case "BOTTOM", "BOTTOMS", "LOWER", "LOWER_BODY", "INFERIOR", "PRENDA_INFERIOR",
                    "PANTALON", "PANTALONES", "FALDA", "FALDAS", "JEAN", "JEANS",
                    "SHORT", "SHORTS", "BERMUDA", "BERMUDAS", "LEGGINGS", "SUDADERA" -> BOTTOM;
            case "ONE_PIECE", "ONEPIECE", "ONE_PIECES", "ONEPIECES", "FULL_BODY", "CUERPO_COMPLETO",
                    "VESTIDO", "VESTIDOS", "ENTERIZO", "ENTERIZOS", "OVEROL", "JUMPSUIT" -> ONE_PIECE;
            case "OUTERWEAR", "ABRIGO", "ABRIGOS", "CHAQUETA", "CHAQUETAS", "GABARDINA" -> OUTERWEAR;
            case "FOOTWEAR", "CALZADO", "ZAPATOS", "ZAPATO", "TENIS", "BOTAS", "SANDALIAS" -> FOOTWEAR;
            case "ACCESSORIES", "ACCESSORY", "ACCESORIO", "ACCESORIOS",
                    "BOLSO", "GORRA", "SOMBRERO", "GAFAS", "JOYERIA" -> ACCESSORIES;
            default -> throw new IllegalArgumentException("Categoría de prenda desconocida: " + rawCategory);
        };
    }
}

