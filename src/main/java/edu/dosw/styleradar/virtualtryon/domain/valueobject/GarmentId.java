package edu.dosw.styleradar.virtualtryon.domain.valueobject;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object inmutable que encapsula el identificador único de una prenda del catálogo (SR-RF-24).
 * Soporta identificadores UUID estándar y conversión determinista desde IDs numéricos o cadenas de Catálogo.
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
        return new GarmentId(parseToUuid(rawUuid));
    }

    public static GarmentId fromFlexible(Object raw) {
        UUID uuid = parseToUuid(raw);
        return uuid != null ? new GarmentId(uuid) : null;
    }

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
        String str = raw.toString().trim();
        if (str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException ex) {
            String normalizedKey = str.startsWith(NUMERIC_GARMENT_ID_PREFIX)
                    ? str
                    : NUMERIC_GARMENT_ID_PREFIX + str;
            return UUID.nameUUIDFromBytes(normalizedKey.getBytes(StandardCharsets.UTF_8));
        }
    }

    public UUID getValue() {
        return value;
    }
}


