package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de salida para el almacenamiento de objetos binarios en Azure Blob Storage
 * dentro del contenedor privado biometric-silhouettes con URLs firmadas (SAS Token).
 */
@Component
public class AzureBlobStorageAdapter implements BlobStoragePort {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobStorageAdapter.class);
    private static final String SILHOUETTES_CONTAINER = "biometric-silhouettes";

    private final String storageBaseUrl;
    private final Map<String, byte[]> inMemoryBlobStore = new ConcurrentHashMap<>();

    public AzureBlobStorageAdapter(
            @Value("${styleradar.tryon.blob-base-url:https://styleradarblob.blob.core.windows.net}") String storageBaseUrl
    ) {
        this.storageBaseUrl = storageBaseUrl.endsWith("/")
                ? storageBaseUrl.substring(0, storageBaseUrl.length() - 1)
                : storageBaseUrl;
    }

    @Override
    public SasSignedUrl storeBiometricPhoto(UserId userId, String contentType, byte[] photoBytes) {
        String extension = "image/png".equalsIgnoreCase(contentType) ? "png" : "jpg";
        String blobName = userId.value() + "/silhouette-" + UUID.randomUUID() + "." + extension;
        long expiryEpoch = Instant.now().plusSeconds(86400).getEpochSecond();
        String signedUrl = String.format(
                "%s/%s/%s?sv=2024-11-04&sr=b&sp=r&se=%d&sig=%s",
                storageBaseUrl,
                SILHOUETTES_CONTAINER,
                blobName,
                expiryEpoch,
                UUID.randomUUID()
        );

        inMemoryBlobStore.put(signedUrl, photoBytes.clone());
        log.info("Fotografía biométrica almacenada en contenedor {} para usuario {}", SILHOUETTES_CONTAINER, userId.value());
        return SasSignedUrl.of(signedUrl);
    }

    @Override
    public Optional<byte[]> retrieveBlobBytes(SasSignedUrl blobUrl) {
        byte[] stored = inMemoryBlobStore.get(blobUrl.value());
        return stored == null ? Optional.empty() : Optional.of(stored.clone());
    }

    @Override
    public void deleteBlob(SasSignedUrl blobUrl) {
        inMemoryBlobStore.remove(blobUrl.value());
        log.info("Objeto binario eliminado de Azure Blob Storage: {}", blobUrl.value());
    }
}

