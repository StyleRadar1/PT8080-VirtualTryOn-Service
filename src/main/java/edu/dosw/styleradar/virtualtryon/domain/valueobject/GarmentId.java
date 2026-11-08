package edu.dosw.styleradar.virtualtryon.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object inmutable que encapsula el identificador único de una prenda del catálogo (SR-RF-24).
 */
public record GarmentId(UUID value) {

    public GarmentId {
        Objects.requireNonNull(value, "El identificador de la prenda (GarmentId) no puede ser nulo");
    }

    public static GarmentId of(UUID value) {
        return new GarmentId(value);
    }

    public static GarmentId fromString(String rawUuid) {
        Objects.requireNonNull(rawUuid, "La cadena del identificador de la prenda no puede ser nula");
        return new GarmentId(UUID.fromString(rawUuid));
    }

    public UUID getValue() {
        return value;
    }
}

