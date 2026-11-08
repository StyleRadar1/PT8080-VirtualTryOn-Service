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
 * dentro del contenedor privado de siluetas biométricas mediante URLs firmadas (SAS Token).
 * Genera tokens SAS de solo lectura (sp=r, sr=b, spr=https) firmados criptográficamente (HMAC-SHA256)
 * por cada recurso individual sin alterar parámetros firmados posteriormente, y verifica la lectura del archivo.
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
    private final String readOnlySasToken;
    private final Duration readSasDuration;
    private final String apiVersion;

    @Autowired
    public AzureBlobStorageAdapter(
            @Value("${styleradar.tryon.blob-base-url}") String storageBaseUrl,
            @Value("${styleradar.tryon.blob-container:biometric-silhouettes}") String containerName,
            @Value("${styleradar.tryon.blob-account-key:}") String accountKey,
            @Value("${styleradar.tryon.blob-sas-token:}") String sasToken,
            @Value("${styleradar.tryon.blob-read-sas-token:}") String readOnlySasToken,
            @Value("${styleradar.tryon.blob-read-sas-duration:PT60M}") Duration readSasDuration,
            @Value("${styleradar.tryon.blob-api-version:2024-11-04}") String apiVersion
    ) {
        this(
                RestClient.builder().requestFactory(buildRequestFactory()),
                storageBaseUrl,
                containerName,
                accountKey,
                sasToken,
                readOnlySasToken,
                readSasDuration,
                apiVersion
        );
    }

    public AzureBlobStorageAdapter(
            RestClient.Builder restClientBuilder,
            String storageBaseUrl,
            String containerName,
            String sasToken,
            String apiVersion
    ) {
        this(restClientBuilder, storageBaseUrl, containerName, "", sasToken, "", Duration.ofMinutes(60), apiVersion);
    }

    public AzureBlobStorageAdapter(
            RestClient.Builder restClientBuilder,
            String storageBaseUrl,
            String containerName,
            String sasToken,
            String readOnlySasToken,
            String apiVersion
    ) {
        this(restClientBuilder, storageBaseUrl, containerName, "", sasToken, readOnlySasToken, Duration.ofMinutes(60), apiVersion);
    }

    public AzureBlobStorageAdapter(
            RestClient.Builder restClientBuilder,
            String storageBaseUrl,
            String containerName,
            String accountKey,
            String sasToken,
            String readOnlySasToken,
            Duration readSasDuration,
            String apiVersion
    ) {
        if (storageBaseUrl == null || storageBaseUrl.isBlank()) {
            throw new IllegalStateException("AZURE_STORAGE_BASE_URL es obligatoria y debe configurarse por variables de entorno");
        }
        this.restClient = restClientBuilder.build();
        String trimmedBaseUrl = storageBaseUrl.trim();
        this.storageBaseUrl = trimmedBaseUrl.endsWith("/")
                ? trimmedBaseUrl.substring(0, trimmedBaseUrl.length() - 1)
                : trimmedBaseUrl;
        this.accountName = extractAccountName(this.storageBaseUrl);
        this.containerName = containerName;
        this.accountKey = accountKey == null ? "" : accountKey.trim();
        this.sasToken = normalizeSasToken(sasToken);
        this.readOnlySasToken = validatePreSignedReadOnlyToken(normalizeSasToken(readOnlySasToken));
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

            // Verificación de lectura real del archivo en Azure mediante la URL SAS de solo lectura generada
            Optional<byte[]> verifiedBytes = retrieveBlobBytes(readOnlySignedUrl);
            if (verifiedBytes.isEmpty()) {
                throw new IllegalStateException("La verificación de lectura del archivo en Azure Blob Storage no retornó contenido");
            }

            log.info("Fotografía biométrica cargada y verificada en lectura en Azure Blob Storage (contenedor: {}, usuario: {})",
                    containerName, userId.value());
            return readOnlySignedUrl;
        } catch (RestClientException ex) {
            log.error("Error al almacenar fotografía biométrica en Azure Blob Storage para usuario {}: {}",
                    userId.value(), ex.getMessage());
            throw new IllegalStateException("No fue posible almacenar la fotografía biométrica en Azure Blob Storage", ex);
        }
    }

    @Override
    public Optional<byte[]> retrieveBlobBytes(SasSignedUrl blobUrl) {
        try {
            byte[] content = restClient.get()
                    .uri(URI.create(blobUrl.value()))
                    .header(HEADER_MS_VERSION, apiVersion)
                    .retrieve()
                    .body(byte[].class);
            return content == null || content.length == 0 ? Optional.empty() : Optional.of(content);
        } catch (RestClientException ex) {
            log.warn("No fue posible recuperar el blob desde Azure Blob Storage ({}): {}",
                    blobUrl.value(), ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void deleteBlob(SasSignedUrl blobUrl) {
        try {
            String deleteTargetUrl = buildDeleteTargetUrl(blobUrl.value());
            restClient.delete()
                    .uri(URI.create(deleteTargetUrl))
                    .header(HEADER_MS_VERSION, apiVersion)
                    .header(HEADER_DELETE_SNAPSHOTS, "include")
                    .retrieve()
                    .toBodilessEntity();
            log.info("Objeto binario eliminado de Azure Blob Storage: {}", blobUrl.value());
        } catch (RestClientException ex) {
            log.warn("Error al eliminar objeto binario en Azure Blob Storage ({}): {}",
                    blobUrl.value(), ex.getMessage());
        }
    }

    private String buildWriteSignedBlobUrl(String blobName) {
        String basePath = String.format("%s/%s/%s", storageBaseUrl, containerName, blobName);
        if (!sasToken.isEmpty()) {
            return basePath + "?" + sasToken;
        }
        if (!accountKey.isEmpty()) {
            return basePath + "?" + generatePerBlobSasToken(blobName, "racw", Instant.now().plus(readSasDuration));
        }
        return basePath;
    }

    private String buildReadOnlySignedBlobUrl(String blobName) {
        String basePath = String.format("%s/%s/%s", storageBaseUrl, containerName, blobName);
        if (!accountKey.isEmpty()) {
            return basePath + "?" + generatePerBlobReadOnlySasToken(blobName, Instant.now().plus(readSasDuration));
        }
        if (!readOnlySasToken.isEmpty()) {
            return basePath + "?" + readOnlySasToken;
        }
        return basePath;
    }

    private String buildDeleteTargetUrl(String rawBlobUrl) {
        int queryIdx = rawBlobUrl.indexOf('?');
        String basePath = queryIdx >= 0 ? rawBlobUrl.substring(0, queryIdx) : rawBlobUrl;
        if (!sasToken.isEmpty()) {
            return basePath + "?" + sasToken;
        }
        if (!accountKey.isEmpty()) {
            String blobName = extractBlobNameFromUrl(basePath);
            return basePath + "?" + generatePerBlobSasToken(blobName, "d", Instant.now().plus(readSasDuration));
        }
        return rawBlobUrl;
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
        if (accountKey.isEmpty()) {
            throw new IllegalStateException("Se requiere AZURE_STORAGE_ACCOUNT_KEY para firmar un SAS por recurso");
        }
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
     * Valida que un token SAS pre-firmado sea estrictamente de solo lectura (sp=r) sin modificar
     * ninguno de sus parámetros firmados para preservar la integridad de su firma (&sig=...).
     */
    static String validatePreSignedReadOnlyToken(String rawReadSas) {
        if (rawReadSas == null || rawReadSas.isBlank()) {
            return "";
        }
        for (String param : rawReadSas.split("&")) {
            if (param.startsWith("sp=") && !"sp=r".equals(param)) {
                throw new IllegalArgumentException(
                        "El token AZURE_READ_SAS_TOKEN debe estar firmado originalmente con permisos exclusivos de lectura (sp=r)");
            }
        }
        return rawReadSas;
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
