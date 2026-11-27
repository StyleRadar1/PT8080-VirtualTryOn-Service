package edu.dosw.styleradar.virtualtryon.domain.valueobject;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Value Object inmutable que encapsula el identificador único del usuario dentro del dominio.
 * Unifica la conversión determinista de identificadores de usuario (UUID o IDs numéricos de Auth)
 * para garantizar consistencia en todo el ciclo de vida: autenticación, cuotas, consentimientos,
 * fotografías, trabajos y eventos de eliminación de cuenta (Ley 1581 · SR-RF-27).
 */
public record UserId(UUID value) {

    public static final String NUMERIC_USER_ID_PREFIX = "styleradar-user-";
    private static final Map<UUID, String> EXTERNAL_NUMERIC_IDS = new ConcurrentHashMap<>();

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
            String numericStr = String.valueOf(num.longValue());
            UUID uuid = UUID.nameUUIDFromBytes((NUMERIC_USER_ID_PREFIX + numericStr).getBytes(StandardCharsets.UTF_8));
            EXTERNAL_NUMERIC_IDS.put(uuid, numericStr);
            return uuid;
        }
        String str = raw.toString().trim();
        if (str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException ex) {
            if (str.startsWith(NUMERIC_USER_ID_PREFIX)) {
                UUID uuid = UUID.nameUUIDFromBytes(str.getBytes(StandardCharsets.UTF_8));
                String digits = str.substring(NUMERIC_USER_ID_PREFIX.length());
                if (digits.matches("[0-9]+")) {
                    EXTERNAL_NUMERIC_IDS.put(uuid, digits);
                }
                return uuid;
            }
            if (str.matches("[0-9]+")) {
                UUID uuid = UUID.nameUUIDFromBytes((NUMERIC_USER_ID_PREFIX + str).getBytes(StandardCharsets.UTF_8));
                EXTERNAL_NUMERIC_IDS.put(uuid, str);
                return uuid;
            }
            return null;
        }
    }

    /**
     * Resuelve el identificador externo original (ID numérico Long de ms-auth/ms-usuarios si aplica)
     * para llamadas internas servidor-a-servidor, manteniendo el UUID canónico en el dominio y API pública.
     */
    public static String resolveExternalIdentifier(UserId userId) {
        if (userId == null || userId.value() == null) {
            return null;
        }
        return EXTERNAL_NUMERIC_IDS.getOrDefault(userId.value(), userId.value().toString());
    }
}

