package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida (Driven Port) para la persistencia, consulta y eliminación
 * de fotografías y renderizados en Azure Blob Storage (SR-RF-23 y SR-RF-25).
 */
public interface BlobStoragePort {

    /**
     * Almacena físicamente la fotografía en Azure Blob Storage y retorna su ruta relativa persistente (ej: "userId/silhouette-UUID.png").
     */
    String storeBiometricPhoto(UserId userId, String contentType, byte[] photoBytes);

    /**
     * Almacena la imagen renderizada resultante de la prueba virtual y retorna su URL firmada de solo lectura (sp=r).
     */
    default SasSignedUrl storeRenderedResult(UserId userId, UUID jobId, byte[] renderedPngBytes) {
        String blobPath = storeBiometricPhoto(userId, "image/png", renderedPngBytes);
        return generateReadOnlySignedUrl(blobPath);
    }

    /**
     * Genera una URL firmada SAS de solo lectura temporal (sr=b, sp=r, spr=https) para la ruta del blob indicada.
     */
    SasSignedUrl generateReadOnlySignedUrl(String blobPath);

    /** Removes a temporary SAS query string before a storage reference is persisted. */
    default String normalizeBlobReference(String blobPathOrUrl) {
        if (blobPathOrUrl == null) return null;
        int queryIndex = blobPathOrUrl.indexOf('?');
        return queryIndex >= 0 ? blobPathOrUrl.substring(0, queryIndex) : blobPathOrUrl;
    }

    /**
     * Recupera los bytes binarios de una fotografía almacenada a partir de su ruta de blob o URL firmada.
     */
    Optional<byte[]> retrieveBlobBytes(String blobPathOrUrl);

    default Optional<byte[]> retrieveBlobBytes(SasSignedUrl blobUrl) {
        return retrieveBlobBytes(blobUrl != null ? blobUrl.value() : null);
    }

    /**
     * Elimina físicamente un objeto binario del contenedor de almacenamiento con reintentos controlados.
     */
    void deleteBlob(String blobPathOrUrl);

    default void deleteBlob(SasSignedUrl blobUrl) {
        deleteBlob(blobUrl != null ? blobUrl.value() : null);
    }
}
