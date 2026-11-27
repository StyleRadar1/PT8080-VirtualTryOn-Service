package edu.dosw.styleradar.virtualtryon.domain.valueobject;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object inmutable que encapsula el identificador único de una prenda del catálogo (SR-RF-24).
 * Soporta identificadores UUID estándar y conversión determinista desde IDs numéricos o cadenas históricas de Catálogo.
 * Distingue identificadores malformados (retorna null o lanza IllegalArgumentException) de UUIDs válidos.
 */
public record GarmentId(UUID value) {

    public static final String NUMERIC_GARMENT_ID_PREFIX = "styleradar-garment-";

    public GarmentId {
        Objects.requireNonNull(value, "El identificador de la prenda (GarmentId) no puede ser nulo");
    }

    public static GarmentId of(UUID value) {
        return new GarmentId(value);
    }

    public static GarmentId fromString(String rawUuid) {
        Objects.requireNonNull(rawUuid, "La cadena del identificador de la prenda no puede ser nula");
        UUID uuid = parseToUuid(rawUuid);
        if (uuid == null) {
            throw new IllegalArgumentException("Identificador de prenda malformado: " + rawUuid);
        }
        return new GarmentId(uuid);
    }

    public static GarmentId fromFlexible(Object raw) {
        UUID uuid = parseToUuid(raw);
        return uuid != null ? new GarmentId(uuid) : null;
    }

    /**
     * Parsea un identificador de prenda a UUID canónico.
     * Soporta:
     * 1. Instancias directas de UUID o GarmentId.
     * 2. Instancias numéricas (Long, Integer, etc.) deterministas con prefijo styleradar-garment-.
     * 3. Cadenas con formato UUID estándar RFC 4122.
     * 4. Cadenas numéricas puras ("123") o con prefijo histórico ("styleradar-garment-123").
     * Si la cadena no corresponde a un formato válido (malformada) o está en blanco, retorna null.
     */
    public static UUID parseToUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID u) {
            return u;
        }
        if (raw instanceof GarmentId g) {
            return g.value();
        }
        if (raw instanceof Number num) {
            String numericStr = String.valueOf(num.longValue());
            return UUID.nameUUIDFromBytes((NUMERIC_GARMENT_ID_PREFIX + numericStr).getBytes(StandardCharsets.UTF_8));
        }
        String str = raw.toString().trim();
        if (str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException ex) {
            if (str.startsWith(NUMERIC_GARMENT_ID_PREFIX)) {
                String remainder = str.substring(NUMERIC_GARMENT_ID_PREFIX.length());
                if (remainder.matches("[0-9]+")) {
                    return UUID.nameUUIDFromBytes(str.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (str.matches("[0-9]+")) {
                return UUID.nameUUIDFromBytes((NUMERIC_GARMENT_ID_PREFIX + str).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }
    }

    public UUID getValue() {
        return value;
    }
}
