package edu.dosw.styleradar.virtualtryon.domain.valueobject;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object inmutable que encapsula el identificador único del usuario dentro del dominio.
 * Unifica la conversión determinista de identificadores de usuario (UUID o IDs numéricos de Auth)
 * para garantizar consistencia en todo el ciclo de vida: autenticación, cuotas, consentimientos,
 * fotografías, trabajos y eventos de eliminación de cuenta (Ley 1581 · SR-RF-27).
 */
public record UserId(UUID value) {

    public static final String NUMERIC_USER_ID_PREFIX = "styleradar-user-";

    public UserId {
        Objects.requireNonNull(value, "El identificador del usuario (UserId) no puede ser nulo");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId fromString(String rawUuid) {
        Objects.requireNonNull(rawUuid, "La cadena del identificador del usuario no puede ser nula");
        return new UserId(parseToUuid(rawUuid));
    }

    public static UserId fromFlexible(Object raw) {
        UUID uuid = parseToUuid(raw);
        return uuid != null ? new UserId(uuid) : null;
    }

    /**
     * Parsea cualquier representación de identificador de usuario a un UUID canónico y determinista.
     * Si es un UUID estándar, lo preserva.
     * Si es un ID numérico proveniente de Auth (ej: "123", 123L) o cualquier identificador no-UUID,
     * genera un UUID v3 determinista mediante namespace RFC 4122 ("styleradar-user-" + id).
     */
    public static UUID parseToUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID u) {
            return u;
        }
        if (raw instanceof UserId u) {
            return u.value();
        }
        if (raw instanceof Number num) {
            return UUID.nameUUIDFromBytes((NUMERIC_USER_ID_PREFIX + num).getBytes(StandardCharsets.UTF_8));
        }
        String str = raw.toString().trim();
        if (str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException ex) {
            if (str.startsWith(NUMERIC_USER_ID_PREFIX)) {
                return UUID.nameUUIDFromBytes(str.getBytes(StandardCharsets.UTF_8));
            }
            if (str.matches("[0-9]+")) {
                return UUID.nameUUIDFromBytes((NUMERIC_USER_ID_PREFIX + str).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }
    }
}

