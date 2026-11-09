package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de salida para la integración real con Azure Blob Storage REST API
 * dentro del contenedor privado de siluetas biométricas.
 * Exige la firma criptográfica HMAC-SHA256 de tokens SAS exclusivos por cada blob individual (sr=b, sp=r, spr=https),
 * persiste únicamente rutas de blob limpias sin credenciales, previene fugas en registros de log y garantiza
 * una política de reintentos y propagación de errores en eliminaciones bajo la Ley 1581.
 */
@Component
public class AzureBlobStorageAdapter implements BlobStoragePort {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobStorageAdapter.class);
    public static final String HEADER_BLOB_TYPE = "x-ms-blob-type";
    public static final String HEADER_MS_VERSION = "x-ms-version";
    public static final String HEADER_DELETE_SNAPSHOTS = "x-ms-delete-snapshots";
    public static final String BLOCK_BLOB_TYPE = "BlockBlob";
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter ISO_UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final RestClient restClient;
    private final String storageBaseUrl;
    private final String accountName;
    private final String containerName;
    private final String accountKey;
    private final Duration readSasDuration;
    private final String apiVersion;

    @Autowired
    public AzureBlobStorageAdapter(
            @Value("${styleradar.tryon.blob-base-url}") String storageBaseUrl,
            @Value("${styleradar.tryon.blob-container:biometric-silhouettes}") String containerName,
            @Value("${styleradar.tryon.blob-account-key}") String accountKey,
            @Value("${styleradar.tryon.blob-read-sas-duration:PT60M}") Duration readSasDuration,
            @Value("${styleradar.tryon.blob-api-version:2024-11-04}") String apiVersion
    ) {
        this(
                RestClient.builder().requestFactory(buildRequestFactory()),
                storageBaseUrl,
                containerName,
                accountKey,
                readSasDuration,
                apiVersion
        );
    }

    public AzureBlobStorageAdapter(
            RestClient.Builder restClientBuilder,
            String storageBaseUrl,
            String containerName,
            String accountKey,
            Duration readSasDuration,
            String apiVersion
    ) {
        validateInitializationParameters(storageBaseUrl, containerName, accountKey, readSasDuration);

        this.restClient = restClientBuilder.build();
        String trimmedBaseUrl = storageBaseUrl.trim();
        this.storageBaseUrl = trimmedBaseUrl.endsWith("/")
                ? trimmedBaseUrl.substring(0, trimmedBaseUrl.length() - 1)
                : trimmedBaseUrl;
        this.accountName = parseAccountNameStrict(this.storageBaseUrl);
        this.containerName = containerName.trim();
        this.accountKey = accountKey.trim();
        this.readSasDuration = readSasDuration;
        this.apiVersion = (apiVersion == null || apiVersion.isBlank()) ? "2024-11-04" : apiVersion.trim();
    }

    private static void validateInitializationParameters(
            String storageBaseUrl,
            String containerName,
            String accountKey,
            Duration readSasDuration
    ) {
        if (storageBaseUrl == null || storageBaseUrl.isBlank()) {
            throw new IllegalStateException("AZURE_STORAGE_BASE_URL es obligatoria y debe configurarse por variables de entorno");
        }
        if (!storageBaseUrl.startsWith("https://")) {
            throw new IllegalStateException(
                    "AZURE_STORAGE_BASE_URL debe iniciar con protocolo seguro https:// para garantizar el cifrado en tránsito de datos biométricos");
        }
        if (containerName == null || containerName.isBlank()) {
            throw new IllegalStateException("AZURE_STORAGE_CONTAINER es obligatorio");
        }
        if (accountKey == null || accountKey.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_STORAGE_ACCOUNT_KEY es obligatoria para generar firmas SAS exclusivas por blob individual (sr=b, sp=r)");
        }
        try {
            byte[] decodedKey = Base64.getDecoder().decode(accountKey.trim());
            if (decodedKey.length < 32) {
                throw new IllegalStateException("AZURE_STORAGE_ACCOUNT_KEY debe tener una longitud mínima de 32 bytes (256 bits)");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("AZURE_STORAGE_ACCOUNT_KEY debe tener un formato Base64 válido", ex);
        }
        if (readSasDuration == null || readSasDuration.isNegative() || readSasDuration.isZero()) {
            throw new IllegalStateException("La duración del token SAS de lectura (readSasDuration) debe ser positiva");
        }
    }

    @Override
    public String storeBiometricPhoto(UserId userId, String contentType, byte[] photoBytes) {
        String extension = "image/png".equalsIgnoreCase(contentType) ? "png" : "jpg";
        String resolvedContentType = "png".equals(extension) ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;
        String blobName = userId.value() + "/silhouette-" + UUID.randomUUID() + "." + extension;
        return uploadBlockBlob(userId, blobName, resolvedContentType, photoBytes);
    }

    @Override
    public SasSignedUrl storeRenderedResult(UserId userId, UUID jobId, byte[] renderedPngBytes) {
        String blobName = userId.value() + "/render-" + jobId + ".png";
        uploadBlockBlob(userId, blobName, MediaType.IMAGE_PNG_VALUE, renderedPngBytes);
        return generateReadOnlySignedUrl(blobName);
    }

    @Override
    public SasSignedUrl generateReadOnlySasUrl(SasSignedUrl existingUrl) {
        if (existingUrl == null || existingUrl.value() == null) {
            return existingUrl;
        }
        return generateReadOnlySignedUrl(existingUrl.value());
    }

    private String uploadBlockBlob(UserId userId, String blobName, String resolvedContentType, byte[] blobBytes) {
        String writeUrl = buildWriteSignedBlobUrl(blobName);
        SasSignedUrl readOnlySignedUrl = generateReadOnlySignedUrl(blobName);
        boolean uploadSucceeded = false;

        try {
            restClient.put()
                    .uri(URI.create(writeUrl))
                    .header(HEADER_BLOB_TYPE, BLOCK_BLOB_TYPE)
                    .header(HEADER_MS_VERSION, apiVersion)
                    .contentType(MediaType.parseMediaType(resolvedContentType))
                    .body(blobBytes)
                    .retrieve()
                    .toBodilessEntity();
            uploadSucceeded = true;

            // Verificación inmediata de lectura real mediante el token SAS por recurso generado
            Optional<byte[]> verifiedBytes = retrieveBlobBytes(readOnlySignedUrl.value());
            if (verifiedBytes.isEmpty()) {
                compensateOrphanedBlob(blobName);
                throw new IllegalStateException("La verificación de lectura del archivo en Azure Blob Storage no retornó contenido");
            }

            log.info("Fotografía biométrica cargada y verificada en lectura en Azure Blob Storage (ruta: {}/{})",
                    containerName, blobName);
            return blobName;
        } catch (RestClientException ex) {
            if (uploadSucceeded) {
                compensateOrphanedBlob(blobName);
            }
            log.error("Error al almacenar fotografía biométrica en Azure Blob Storage (ruta: {}/{}, causa: {})",
                    containerName, blobName, ex.getClass().getSimpleName());
            throw new IllegalStateException("No fue posible almacenar la fotografía biométrica en Azure Blob Storage", ex);
        }
    }

    private void compensateOrphanedBlob(String blobName) {
        try {
            String deleteTargetUrl = buildDeleteSignedBlobUrl(blobName);
            restClient.delete()
                    .uri(URI.create(deleteTargetUrl))
                    .header(HEADER_MS_VERSION, apiVersion)
                    .header(HEADER_DELETE_SNAPSHOTS, "include")
                    .retrieve()
                    .toBodilessEntity();
            log.info("Compensación ejecutada: archivo huérfano eliminado de Azure Blob Storage (blob: {})", blobName);
        } catch (Exception ex) {
            log.warn("Fallo en compensación de archivo huérfano en Azure (blob: {}, causa: {})",
                    blobName, ex.getClass().getSimpleName());
        }
    }

    @Override
    public SasSignedUrl generateReadOnlySignedUrl(String blobPath) {
        Objects.requireNonNull(blobPath, "La ruta del blob no puede ser nula");
        String cleanBlobName = extractBlobNameFromUrl(stripQueryParametersForLog(blobPath));
        String readToken = generatePerBlobReadOnlySasToken(cleanBlobName, Instant.now().plus(readSasDuration));
        String fullUrl = String.format("%s/%s/%s?%s", storageBaseUrl, containerName, cleanBlobName, readToken);
        return SasSignedUrl.of(fullUrl);
    }

    @Override
    public Optional<byte[]> retrieveBlobBytes(String blobPathOrUrl) {
        if (blobPathOrUrl == null || blobPathOrUrl.isBlank()) {
            return Optional.empty();
        }
        String safeBlobPath = stripQueryParametersForLog(blobPathOrUrl);
        String targetUrl = blobPathOrUrl.contains("?")
                ? blobPathOrUrl
                : generateReadOnlySignedUrl(safeBlobPath).value();

        try {
            byte[] content = restClient.get()
                    .uri(URI.create(targetUrl))
                    .header(HEADER_MS_VERSION, apiVersion)
                    .retrieve()
                    .body(byte[].class);
            return content == null || content.length == 0 ? Optional.empty() : Optional.of(content);
        } catch (RestClientException ex) {
            log.warn("No fue posible recuperar el blob desde Azure Blob Storage (ruta: {}, causa: {})",
                    safeBlobPath, ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void deleteBlob(String blobPathOrUrl) {
        if (blobPathOrUrl == null || blobPathOrUrl.isBlank()) {
            return;
        }
        String safeBlobPath = stripQueryParametersForLog(blobPathOrUrl);
        String blobName = extractBlobNameFromUrl(safeBlobPath);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String deleteTargetUrl = buildDeleteSignedBlobUrl(blobName);
                restClient.delete()
                        .uri(URI.create(deleteTargetUrl))
                        .header(HEADER_MS_VERSION, apiVersion)
                        .header(HEADER_DELETE_SNAPSHOTS, "include")
                        .retrieve()
                        .toBodilessEntity();
                log.info("Objeto binario eliminado exitosamente de Azure Blob Storage: {}", safeBlobPath);
                return;
            } catch (RestClientException ex) {
                log.warn("Intento {}/{} fallido al eliminar objeto binario en Azure Blob Storage (ruta: {}, causa: {})",
                        attempt, maxAttempts, safeBlobPath, ex.getClass().getSimpleName());
                if (attempt == maxAttempts) {
                    log.error("[ORPHAN_CLEANUP_REQUIRED] Fallo definitivo tras {} reintentos al eliminar fotografía biométrica: {}",
                            maxAttempts, safeBlobPath);
                    throw new IllegalStateException("No fue posible eliminar la fotografía biométrica en Azure Blob Storage", ex);
                }
                try {
                    Thread.sleep(100L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupción durante la eliminación de fotografía biométrica", ie);
                }
            }
        }
    }

    private String buildWriteSignedBlobUrl(String blobName) {
        String writeToken = generatePerBlobSasToken(blobName, "racw", Instant.now().plus(Duration.ofMinutes(15)));
        return String.format("%s/%s/%s?%s", storageBaseUrl, containerName, blobName, writeToken);
    }

    private String buildDeleteSignedBlobUrl(String blobName) {
        String deleteToken = generatePerBlobSasToken(blobName, "d", Instant.now().plus(Duration.ofMinutes(15)));
        return String.format("%s/%s/%s?%s", storageBaseUrl, containerName, blobName, deleteToken);
    }

    /**
     * Genera criptográficamente (HMAC-SHA256) un Service SAS de Azure válido para un blob específico (sr=b)
     * con permisos exclusivos de solo lectura (sp=r) y protocolo HTTPS (spr=https), sin modificar posteriormente
     * sus parámetros firmados.
     */
    public String generatePerBlobReadOnlySasToken(String blobName, Instant expiry) {
        return generatePerBlobSasToken(blobName, "r", expiry);
    }

    public String generatePerBlobSasToken(String blobName, String permissions, Instant expiry) {
        String formattedExpiry = ISO_UTC_FORMATTER.format(expiry.truncatedTo(ChronoUnit.SECONDS));
        String canonicalizedResource = String.format("/blob/%s/%s/%s", accountName, containerName, blobName);
        String stringToSign = String.join("\n",
                permissions,
                "",
                formattedExpiry,
                canonicalizedResource,
                "",
                "",
                "https",
                apiVersion,
                "b",
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        );
        String signature = computeHmacSha256Base64(stringToSign, accountKey);
        return String.format(
                "se=%s&sp=%s&spr=https&sv=%s&sr=b&sig=%s",
                URLEncoder.encode(formattedExpiry, StandardCharsets.UTF_8),
                permissions,
                apiVersion,
                URLEncoder.encode(signature, StandardCharsets.UTF_8)
        );
    }

    private static String computeHmacSha256Base64(String stringToSign, String base64Key) {
        try {
            byte[] decodedKey = Base64.getDecoder().decode(base64Key);
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(new SecretKeySpec(decodedKey, HMAC_SHA256_ALGORITHM));
            byte[] hash = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Error al firmar criptográficamente el SAS Token de Azure Blob Storage", ex);
        }
    }

    /**
     * Remueve todos los parámetros query (?se=...&sig=...) de una URL SAS para que en los logs
     * solo se registre la ruta del archivo sin exponer credenciales de autenticación.
     */
    public static String stripQueryParametersForLog(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        int queryIdx = rawUrl.indexOf('?');
        return queryIdx >= 0 ? rawUrl.substring(0, queryIdx) : rawUrl;
    }

    /**
     * Extrae de forma canónica el nombre relativo del blob (ej: userId/silhouette-UUID.png)
     * a partir de URLs completas con o sin parámetros query SAS, rutas absolutas con contenedor,
     * o rutas relativas directas, preservando invariablemente todos los segmentos de usuario.
     */
    public String extractBlobNameFromUrl(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }
        String path = stripQueryParametersForLog(rawPath.trim());

        // Si es una URL absoluta (http:// o https://), extraemos el componente path del URI
        if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                URI uri = URI.create(path);
                String uriPath = uri.getPath();
                if (uriPath != null && !uriPath.isBlank()) {
                    path = uriPath;
                }
            } catch (Exception ignored) {
                // Si la URI no es válida sintácticamente, se continúa con la cadena limpia
            }
        }

        // Eliminamos slashes iniciales redundantes (ej: /container/user/photo.png -> container/user/photo.png)
        while (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Si la ruta inicia con el contenedor configurado o con prefijo de contenedor, se remueve únicamente el contenedor
        String configuredContainerPrefix = containerName + "/";
        if (path.startsWith(configuredContainerPrefix)) {
            return path.substring(configuredContainerPrefix.length());
        }

        // Si la ruta era una URL absoluta con un contenedor diferente pero conocido, removemos el primer segmento (el contenedor)
        // SOLO si la cadena original era una URL absoluta (http:// o https://) y contiene al menos otro slash
        if (rawPath.trim().startsWith("http://") || rawPath.trim().startsWith("https://")) {
            int firstSlash = path.indexOf('/');
            if (firstSlash >= 0 && firstSlash < path.length() - 1) {
                return path.substring(firstSlash + 1);
            }
        }

        // Si es una ruta relativa (ej: userId/silhouette-123.png o silhouette-123.png), se preserva íntegramente
        return path;
    }

    public static String parseAccountNameStrict(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host != null && host.contains(".")) {
                String extracted = host.substring(0, host.indexOf('.'));
                if (!extracted.isBlank()) {
                    return extracted;
                }
            }
        } catch (Exception ignored) {
        }
        throw new IllegalStateException(
                "No fue posible determinar un nombre de cuenta válido a partir de AZURE_STORAGE_BASE_URL: " + baseUrl);
    }

    private static SimpleClientHttpRequestFactory buildRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
