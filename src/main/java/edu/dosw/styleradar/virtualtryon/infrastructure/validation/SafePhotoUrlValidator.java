package edu.dosw.styleradar.virtualtryon.infrastructure.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validador de URL segura para fotografías frontales en Try-On.
 * Mitiga Server-Side Request Forgery (SSRF - OWASP A10).
 * En producción (prod) y homologación (qa), restringe estrictamente a los hosts
 * autorizados de Azure Blob Storage pertenecientes a StyleRadar.
 * Bloquea de forma explícita cuentas de almacenamiento externas o de terceros en Azure.
 * Las excepciones para localhost / 127.0.0.1 quedan limitadas exclusivamente al entorno de desarrollo local (dev/test).
 */
@Component
public class SafePhotoUrlValidator implements ConstraintValidator<ValidPhotoUrl, String> {

    /**
     * Cuentas y hosts oficiales de Azure Blob Storage autorizados para StyleRadar.
     */
    private static final Set<String> OFFICIAL_STYLERADAR_STORAGE_HOSTS = Set.of(
            "styleradarpt8080blob.blob.core.windows.net",
            "styleradarblob.blob.core.windows.net",
            "styleradar.blob.core.windows.net"
    );

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

        // 1. Almacenamiento autorizado oficial de StyleRadar en Azure Blob Storage
        if (isAuthorizedAzureStorageUrl(value)) {
            return true;
        }

        // 2. Excepciones para desarrollo local (localhost / 127.0.0.1)
        if (isLocalhostAllowed() && LOCALHOST_PATTERN.matcher(value).matches()) {
            return true;
        }

        return false;
    }

    /**
     * Valida si la URL pertenece estrictamente al host de Azure Blob Storage autorizado para StyleRadar.
     * Rechaza cualquier cuenta externa de Azure (ej. attacker.blob.core.windows.net) o dominio no autorizado.
     */
    boolean isAuthorizedAzureStorageUrl(String url) {
        if (!url.startsWith("https://")) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase();

            // Debe ser un subdominio válido de Azure Blob Storage
            if (!host.endsWith(".blob.core.windows.net")) {
                return false;
            }

            // Validar contra el host configurado dinámicamente en el entorno (application.yml o variable de Azure)
            String configuredHost = getConfiguredAuthorizedHost();
            if (configuredHost != null && host.equalsIgnoreCase(configuredHost)) {
                return true;
            }

            // Validar contra las cuentas oficiales autorizadas de StyleRadar
            return OFFICIAL_STYLERADAR_STORAGE_HOSTS.contains(host);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extrae el host autorizado configurado en el ambiente actual (vía blob-base-url o AZURE_STORAGE_BASE_URL).
     */
    String getConfiguredAuthorizedHost() {
        if (environment == null) {
            return null;
        }
        String explicitHost = environment.getProperty("styleradar.tryon.security.authorized-storage-host");
        if (explicitHost != null && !explicitHost.isBlank()) {
            return extractHostFromUrlOrHost(explicitHost.trim());
        }
        String baseUrl = environment.getProperty("styleradar.tryon.blob-base-url");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = environment.getProperty("AZURE_STORAGE_BASE_URL");
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            return extractHostFromUrlOrHost(baseUrl.trim());
        }
        return null;
    }

    private String extractHostFromUrlOrHost(String urlOrHost) {
        try {
            if (urlOrHost.contains("://")) {
                URI uri = URI.create(urlOrHost);
                return uri.getHost() != null ? uri.getHost().toLowerCase() : null;
            }
            return urlOrHost.toLowerCase();
        } catch (Exception e) {
            return null;
        }
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
