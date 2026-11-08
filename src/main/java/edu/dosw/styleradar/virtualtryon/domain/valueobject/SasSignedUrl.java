package edu.dosw.styleradar.virtualtryon.domain.valueobject;

/**
 * Value Object inmutable que representa una URL firmada (SAS Token / HTTPS) de almacenamiento de objetos.
 * Garantiza el principio de mínimo privilegio verificando que los parámetros SAS expuestos (sp)
 * otorguen exclusivamente permiso de lectura ('r') sobre el blob.
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
        int queryIndex = trimmed.indexOf('?');
        if (queryIndex >= 0 && queryIndex < trimmed.length() - 1) {
            String query = trimmed.substring(queryIndex + 1);
            for (String param : query.split("&")) {
                if (param.startsWith("sp=") && !"sp=r".equals(param)) {
                    throw new IllegalArgumentException(
                            "La URL SAS expuesta debe contener únicamente permiso de lectura (sp=r), pero se recibió: " + param
                    );
                }
                if (param.startsWith("ss=") || param.startsWith("srt=")) {
                    throw new IllegalArgumentException(
                            "La URL SAS expuesta debe tener alcance exclusivo de blob (sr=b) y no exponer permisos de cuenta: " + param
                    );
                }
            }
        }
        value = trimmed;
    }

    public static SasSignedUrl of(String value) {
        return new SasSignedUrl(value);
    }
}

