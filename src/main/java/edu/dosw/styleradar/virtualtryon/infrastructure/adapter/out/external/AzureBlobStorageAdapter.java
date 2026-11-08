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
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de salida para la integración real con Azure Blob Storage REST API
 * dentro del contenedor privado de siluetas biométricas.
 * Exige la firma criptográfica HMAC-SHA256 de tokens SAS exclusivos por cada blob individual (sr=b, sp=r, spr=https),
 * impidiendo el uso de tokens reutilizados o de alcance de contenedor (sr=c), y registra en logs únicamente la ruta
 * del recurso sin parámetros de autenticación.
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
    private final String sasToken;
    private final Duration readSasDuration;
    private final String apiVersion;

    @Autowired
    public AzureBlobStorageAdapter(
            @Value("${styleradar.tryon.blob-base-url}") String storageBaseUrl,
            @Value("${styleradar.tryon.blob-container:biometric-silhouettes}") String containerName,
            @Value("${styleradar.tryon.blob-account-key}") String accountKey,
            @Value("${styleradar.tryon.blob-sas-token:}") String sasToken,
            @Value("${styleradar.tryon.blob-read-sas-duration:PT60M}") Duration readSasDuration,
            @Value("${styleradar.tryon.blob-api-version:2024-11-04}") String apiVersion
    ) {
        this(
                RestClient.builder().requestFactory(buildRequestFactory()),
                storageBaseUrl,
                containerName,
                accountKey,
                sasToken,
                readSasDuration,
                apiVersion
        );
    }

    public AzureBlobStorageAdapter(
            RestClient.Builder restClientBuilder,
            String storageBaseUrl,
            String containerName,
            String accountKey,
            String sasToken,
            Duration readSasDuration,
            String apiVersion
    ) {
        if (storageBaseUrl == null || storageBaseUrl.isBlank()) {
            throw new IllegalStateException("AZURE_STORAGE_BASE_URL es obligatoria y debe configurarse por variables de entorno");
        }
        if (accountKey == null || accountKey.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_STORAGE_ACCOUNT_KEY es obligatoria para generar firmas SAS exclusivas por blob individual (sr=b, sp=r)");
        }
        this.restClient = restClientBuilder.build();
        String trimmedBaseUrl = storageBaseUrl.trim();
        this.storageBaseUrl = trimmedBaseUrl.endsWith("/")
                ? trimmedBaseUrl.substring(0, trimmedBaseUrl.length() - 1)
                : trimmedBaseUrl;
        this.accountName = extractAccountName(this.storageBaseUrl);
        this.containerName = containerName;
        this.accountKey = accountKey.trim();
        this.sasToken = normalizeSasToken(sasToken);
        this.readSasDuration = readSasDuration == null ? Duration.ofMinutes(60) : readSasDuration;
        this.apiVersion = apiVersion;
    }

    @Override
    public SasSignedUrl storeBiometricPhoto(UserId userId, String contentType, byte[] photoBytes) {
        String extension = "image/png".equalsIgnoreCase(contentType) ? "png" : "jpg";
        String resolvedContentType = "png".equals(extension) ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;
        String blobName = userId.value() + "/silhouette-" + UUID.randomUUID() + "." + extension;
        String writeUrl = buildWriteSignedBlobUrl(blobName);
        SasSignedUrl readOnlySignedUrl = SasSignedUrl.of(buildReadOnlySignedBlobUrl(blobName));

        try {
            restClient.put()
                    .uri(URI.create(writeUrl))
                    .header(HEADER_BLOB_TYPE, BLOCK_BLOB_TYPE)
                    .header(HEADER_MS_VERSION, apiVersion)
                    .contentType(MediaType.parseMediaType(resolvedContentType))
                    .body(photoBytes)
                    .retrieve()
                    .toBodilessEntity();

            // Verificación de lectura real del archivo en Azure mediante la URL SAS por recurso (sr=b, sp=r)
            Optional<byte[]> verifiedBytes = retrieveBlobBytes(readOnlySignedUrl);
            if (verifiedBytes.isEmpty()) {
                throw new IllegalStateException("La verificación de lectura del archivo en Azure Blob Storage no retornó contenido");
            }

            log.info("Fotografía biométrica cargada y verificada en lectura en Azure Blob Storage (ruta: {}/{})",
                    containerName, blobName);
            return readOnlySignedUrl;
        } catch (RestClientException ex) {
            log.error("Error al almacenar fotografía biométrica en Azure Blob Storage para ruta {}/{}: {}",
                    containerName, blobName, ex.getMessage());
            throw new IllegalStateException("No fue posible almacenar la fotografía biométrica en Azure Blob Storage", ex);
        }
    }

    @Override
    public Optional<byte[]> retrieveBlobBytes(SasSignedUrl blobUrl) {
        String safeBlobPath = stripQueryParametersForLog(blobUrl.value());
        try {
            byte[] content = restClient.get()
                    .uri(URI.create(blobUrl.value()))
                    .header(HEADER_MS_VERSION, apiVersion)
                    .retrieve()
                    .body(byte[].class);
            return content == null || content.length == 0 ? Optional.empty() : Optional.of(content);
        } catch (RestClientException ex) {
            log.warn("No fue posible recuperar el blob desde Azure Blob Storage ({}): {}",
                    safeBlobPath, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void deleteBlob(SasSignedUrl blobUrl) {
        String safeBlobPath = stripQueryParametersForLog(blobUrl.value());
        try {
            String deleteTargetUrl = buildDeleteTargetUrl(blobUrl.value());
            restClient.delete()
                    .uri(URI.create(deleteTargetUrl))
                    .header(HEADER_MS_VERSION, apiVersion)
                    .header(HEADER_DELETE_SNAPSHOTS, "include")
                    .retrieve()
                    .toBodilessEntity();
            log.info("Objeto binario eliminado de Azure Blob Storage: {}", safeBlobPath);
        } catch (RestClientException ex) {
            log.warn("Error al eliminar objeto binario en Azure Blob Storage ({}): {}",
                    safeBlobPath, ex.getMessage());
        }
    }

    private String buildWriteSignedBlobUrl(String blobName) {
        String basePath = String.format("%s/%s/%s", storageBaseUrl, containerName, blobName);
        if (!sasToken.isEmpty()) {
            return basePath + "?" + sasToken;
        }
        return basePath + "?" + generatePerBlobSasToken(blobName, "racw", Instant.now().plus(readSasDuration));
    }

    private String buildReadOnlySignedBlobUrl(String blobName) {
        String basePath = String.format("%s/%s/%s", storageBaseUrl, containerName, blobName);
        return basePath + "?" + generatePerBlobReadOnlySasToken(blobName, Instant.now().plus(readSasDuration));
    }

    private String buildDeleteTargetUrl(String rawBlobUrl) {
        String basePath = stripQueryParametersForLog(rawBlobUrl);
        if (!sasToken.isEmpty()) {
            return basePath + "?" + sasToken;
        }
        String blobName = extractBlobNameFromUrl(basePath);
        return basePath + "?" + generatePerBlobSasToken(blobName, "d", Instant.now().plus(readSasDuration));
    }

    /**
     * Genera criptográficamente (HMAC-SHA256) un Service SAS de Azure válido para un blob específico (sr=b)
     * con permisos exclusivos de solo lectura (sp=r) y protocolo HTTPS (spr=https), sin modificar posteriormente
     * sus parámetros firmados.
     */
    public String generatePerBlobReadOnlySasToken(String blobName, Instant expiry) {
        return generatePerBlobSasToken(blobName, "r", expiry);
    }

    String generatePerBlobSasToken(String blobName, String permissions, Instant expiry) {
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

    private String extractBlobNameFromUrl(String basePath) {
        String prefix = storageBaseUrl + "/" + containerName + "/";
        if (basePath.startsWith(prefix)) {
            return basePath.substring(prefix.length());
        }
        return basePath;
    }

    static String extractAccountName(String baseUrl) {
        try {
            String host = URI.create(baseUrl).getHost();
            if (host != null && host.contains(".")) {
                return host.substring(0, host.indexOf('.'));
            }
        } catch (IllegalArgumentException ignored) {
            // Fallback por defecto si la URL no tiene host estándar
        }
        return "styleradarblob";
    }

    private static String normalizeSasToken(String rawSasToken) {
        if (rawSasToken == null || rawSasToken.isBlank()) {
            return "";
        }
        String trimmed = rawSasToken.trim();
        return trimmed.startsWith("?") ? trimmed.substring(1) : trimmed;
    }

    private static SimpleClientHttpRequestFactory buildRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
