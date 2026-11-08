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

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de salida para la integración real con Azure Blob Storage REST API
 * dentro del contenedor privado de siluetas biométricas mediante URLs firmadas (SAS Token).
 * Ejecuta operaciones HTTP PUT (Put BlockBlob), GET (Get Blob) y DELETE (Delete Blob).
 */
@Component
public class AzureBlobStorageAdapter implements BlobStoragePort {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobStorageAdapter.class);
    public static final String HEADER_BLOB_TYPE = "x-ms-blob-type";
    public static final String HEADER_MS_VERSION = "x-ms-version";
    public static final String HEADER_DELETE_SNAPSHOTS = "x-ms-delete-snapshots";
    public static final String BLOCK_BLOB_TYPE = "BlockBlob";

    private final RestClient restClient;
    private final String storageBaseUrl;
    private final String containerName;
    private final String sasToken;
    private final String readOnlySasToken;
    private final String apiVersion;

    @Autowired
    public AzureBlobStorageAdapter(
            @Value("${styleradar.tryon.blob-base-url}") String storageBaseUrl,
            @Value("${styleradar.tryon.blob-container:biometric-silhouettes}") String containerName,
            @Value("${styleradar.tryon.blob-sas-token:}") String sasToken,
            @Value("${styleradar.tryon.blob-read-sas-token:}") String readOnlySasToken,
            @Value("${styleradar.tryon.blob-api-version:2024-11-04}") String apiVersion
    ) {
        this(
                RestClient.builder().requestFactory(buildRequestFactory()),
                storageBaseUrl,
                containerName,
                sasToken,
                readOnlySasToken,
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
        this(restClientBuilder, storageBaseUrl, containerName, sasToken, "", apiVersion);
    }

    public AzureBlobStorageAdapter(
            RestClient.Builder restClientBuilder,
            String storageBaseUrl,
            String containerName,
            String sasToken,
            String readOnlySasToken,
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
        this.containerName = containerName;
        this.sasToken = normalizeSasToken(sasToken);
        this.readOnlySasToken = sanitizeToReadOnlySasToken(
                !normalizeSasToken(readOnlySasToken).isEmpty()
                        ? normalizeSasToken(readOnlySasToken)
                        : this.sasToken
        );
        this.apiVersion = apiVersion;
    }

    @Override
    public SasSignedUrl storeBiometricPhoto(UserId userId, String contentType, byte[] photoBytes) {
        String extension = "image/png".equalsIgnoreCase(contentType) ? "png" : "jpg";
        String resolvedContentType = "png".equals(extension) ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;
        String blobName = userId.value() + "/silhouette-" + UUID.randomUUID() + "." + extension;
        return uploadBlockBlob(userId, blobName, resolvedContentType, photoBytes);
    }

    @Override
    public SasSignedUrl storeRenderedResult(UserId userId, UUID jobId, byte[] renderedPngBytes) {
        String blobName = userId.value() + "/render-" + jobId + ".png";
        return uploadBlockBlob(userId, blobName, MediaType.IMAGE_PNG_VALUE, renderedPngBytes);
    }

    @Override
    public SasSignedUrl generateReadOnlySasUrl(SasSignedUrl existingUrl) {
        String raw = existingUrl.value();
        int queryIdx = raw.indexOf('?');
        String basePath = queryIdx >= 0 ? raw.substring(0, queryIdx) : raw;
        if (readOnlySasToken.isEmpty()) {
            return SasSignedUrl.of(basePath);
        }
        return SasSignedUrl.of(basePath + "?" + readOnlySasToken);
    }

    private SasSignedUrl uploadBlockBlob(UserId userId, String blobName, String resolvedContentType, byte[] blobBytes) {
        String writeUrl = buildWriteSignedBlobUrl(blobName);
        String readOnlyUrl = buildReadOnlySignedBlobUrl(blobName);

        try {
            restClient.put()
                    .uri(URI.create(writeUrl))
                    .header(HEADER_BLOB_TYPE, BLOCK_BLOB_TYPE)
                    .header(HEADER_MS_VERSION, apiVersion)
                    .contentType(MediaType.parseMediaType(resolvedContentType))
                    .body(blobBytes)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Fotografía biométrica cargada en Azure Blob Storage (contenedor: {}, usuario: {})",
                    containerName, userId.value());
            return SasSignedUrl.of(readOnlyUrl);
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
        if (sasToken.isEmpty()) {
            return basePath;
        }
        return basePath + "?" + sasToken;
    }

    private String buildReadOnlySignedBlobUrl(String blobName) {
        String basePath = String.format("%s/%s/%s", storageBaseUrl, containerName, blobName);
        if (readOnlySasToken.isEmpty()) {
            return basePath;
        }
        return basePath + "?" + readOnlySasToken;
    }

    private String buildDeleteTargetUrl(String rawBlobUrl) {
        if (sasToken.isEmpty()) {
            return rawBlobUrl;
        }
        int queryIdx = rawBlobUrl.indexOf('?');
        String basePath = queryIdx >= 0 ? rawBlobUrl.substring(0, queryIdx) : rawBlobUrl;
        return basePath + "?" + sasToken;
    }

    /**
     * Garantiza el principio de mínimo privilegio en URLs SAS expuestas:
     * restringe el permiso SAS a solo lectura (sp=r), recurso blob (sr=b) y protocolo HTTPS (spr=https),
     * eliminando permisos administrativos o de escritura/borrado (w, d, c, a, l, x) y alcances de cuenta (ss, srt).
     */
    static String sanitizeToReadOnlySasToken(String rawSas) {
        if (rawSas == null || rawSas.isBlank()) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder();
        boolean hasSp = false;
        boolean hasSr = false;
        boolean hasSpr = false;
        for (String part : rawSas.split("&")) {
            if (part.isBlank() || part.startsWith("ss=") || part.startsWith("srt=")) {
                continue;
            }
            if (!sanitized.isEmpty()) {
                sanitized.append('&');
            }
            if (part.startsWith("sp=")) {
                sanitized.append("sp=r");
                hasSp = true;
            } else if (part.startsWith("sr=")) {
                sanitized.append("sr=b");
                hasSr = true;
            } else if (part.startsWith("spr=")) {
                sanitized.append("spr=https");
                hasSpr = true;
            } else {
                sanitized.append(part);
            }
        }
        if (!hasSr) {
            if (!sanitized.isEmpty()) {
                sanitized.append('&');
            }
            sanitized.append("sr=b");
        }
        if (!hasSp) {
            sanitized.append("&sp=r");
        }
        if (!hasSpr) {
            sanitized.append("&spr=https");
        }
        return sanitized.toString();
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
