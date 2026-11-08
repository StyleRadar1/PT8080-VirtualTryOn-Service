package edu.dosw.styleradar.virtualtryon.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object inmutable que encapsula el identificador único del usuario dentro del dominio.
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "El identificador del usuario (UserId) no puede ser nulo");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId fromString(String rawUuid) {
        Objects.requireNonNull(rawUuid, "La cadena del identificador del usuario no puede ser nula");
        return new UserId(UUID.fromString(rawUuid));
    }
}

