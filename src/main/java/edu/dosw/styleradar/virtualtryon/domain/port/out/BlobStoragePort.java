package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.Optional;

/**
 * Puerto de salida para el almacenamiento seguro de objetos binarios (siluetas biométricas y resultados)
 * en Azure Blob Storage mediante rutas de blob persistidas y URLs firmadas temporales con SAS Token (sp=r, sr=b).
 */
public interface BlobStoragePort {

    /**
     * Almacena físicamente la fotografía en Azure Blob Storage y retorna su ruta relativa persistente (ej: "userId/silhouette-UUID.png").
     */
    String storeBiometricPhoto(UserId userId, String contentType, byte[] photoBytes);

    /**
     * Genera una URL firmada SAS de solo lectura temporal (sr=b, sp=r, spr=https) para la ruta del blob indicada.
     */
    SasSignedUrl generateReadOnlySignedUrl(String blobPath);

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
