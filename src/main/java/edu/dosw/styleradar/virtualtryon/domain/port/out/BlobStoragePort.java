package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para el almacenamiento seguro de objetos binarios (siluetas biométricas y resultados)
 * en Azure Blob Storage mediante URLs firmadas con SAS Token.
 */
public interface BlobStoragePort {

    /**
     * Almacena la fotografía frontal de silueta del usuario y retorna su URL firmada (SAS).
     */
    SasSignedUrl storeBiometricPhoto(UserId userId, String contentType, byte[] photoBytes);

    /**
     * Almacena la imagen renderizada resultante de la prueba virtual y retorna su URL firmada de solo lectura (sp=r).
     */
    default SasSignedUrl storeRenderedResult(UserId userId, UUID jobId, byte[] renderedPngBytes) {
        return storeBiometricPhoto(userId, "image/png", renderedPngBytes);
    }

    /**
     * Renueva o construye una URL firmada temporal de solo lectura (sp=r, sr=b, spr=https) para visualizar un resultado.
     */
    default SasSignedUrl generateReadOnlySasUrl(SasSignedUrl existingUrl) {
        return existingUrl;
    }

    /**
     * Recupera los bytes binarios de una fotografía almacenada a partir de su URL firmada.
     */
    Optional<byte[]> retrieveBlobBytes(SasSignedUrl blobUrl);

    /**
     * Elimina físicamente un objeto binario del contenedor de almacenamiento.
     */
    void deleteBlob(SasSignedUrl blobUrl);
}

