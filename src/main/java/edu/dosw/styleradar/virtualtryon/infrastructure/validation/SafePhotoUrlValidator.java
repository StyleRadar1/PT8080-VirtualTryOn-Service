package edu.dosw.styleradar.virtualtryon.infrastructure.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validador de URL segura para fotografías frontales en Try-On.
 * Mitiga Server-Side Request Forgery (SSRF - OWASP A10).
 * En producción (prod) y homologación (qa), restringe estrictamente a hosts de Azure Blob Storage.
 * Las excepciones para localhost / 127.0.0.1 quedan limitadas exclusivamente al entorno de desarrollo local (dev).
 */
@Component
public class SafePhotoUrlValidator implements ConstraintValidator<ValidPhotoUrl, String> {

    private static final Pattern AZURE_BLOB_PATTERN =
            Pattern.compile("^https://[a-zA-Z0-9-]+\\.blob\\.core\\.windows\\.net/[^\\s]+$");

    private static final Pattern LOCALHOST_PATTERN =
            Pattern.compile("^https?://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/[^\\s]+$");

    private final Environment environment;

    public SafePhotoUrlValidator() {
        this.environment = null;
    }

    @Autowired
    public SafePhotoUrlValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // Se delega la obligatoriedad a @NotBlank
        }

        // 1. Almacenamiento autorizado oficial de Azure Blob Storage (permitido en cualquier entorno)
        if (AZURE_BLOB_PATTERN.matcher(value).matches()) {
            return true;
        }

        // 2. Excepciones para desarrollo local (localhost / 127.0.0.1)
        if (isLocalhostAllowed() && LOCALHOST_PATTERN.matcher(value).matches()) {
            return true;
        }

        return false;
    }

    boolean isLocalhostAllowed() {
        if (environment == null) {
            return false;
        }
        Boolean allowExplicit = environment.getProperty("styleradar.tryon.security.allow-localhost-urls", Boolean.class);
        if (allowExplicit != null) {
            return allowExplicit;
        }
        // Permitido en perfiles de desarrollo local o pruebas
        return environment.acceptsProfiles(Profiles.of("dev", "test"));
    }
}
