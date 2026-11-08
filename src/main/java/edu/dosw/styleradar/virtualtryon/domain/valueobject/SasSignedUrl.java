package edu.dosw.styleradar.virtualtryon.domain.valueobject;

import java.util.Objects;

/**
 * Value Object inmutable que representa una URL firmada (SAS Token / HTTPS) de almacenamiento de objetos.
 */
public record SasSignedUrl(String value) {

    public SasSignedUrl {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("La URL firmada (SasSignedUrl) no puede ser nula ni vacía");
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            throw new IllegalArgumentException("La URL firmada debe utilizar el esquema HTTP o HTTPS");
        }
        value = trimmed;
    }

    public static SasSignedUrl of(String value) {
        return new SasSignedUrl(value);
    }
}

