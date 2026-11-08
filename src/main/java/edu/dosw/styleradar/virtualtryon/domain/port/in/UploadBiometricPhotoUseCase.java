package edu.dosw.styleradar.virtualtryon.domain.port.in;

import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

/**
 * Puerto de entrada (Caso de Uso) para la carga, validación y consulta de la fotografía frontal
 * de silueta del usuario en el probador virtual (SR-RF-23).
 */
public interface UploadBiometricPhotoUseCase {

    /**
     * Valida y almacena la fotografía frontal del usuario verificando consentimiento biométrico,
     * formato JPEG/PNG, peso máximo de 5 MB, dimensiones mínimas 300x400 px y orientación vertical.
     */
    BiometricPhoto uploadPhoto(UserId userId, boolean biometricConsentAccepted, String contentType, byte[] photoBytes);

    /**
     * Consulta los metadatos y la URL firmada de la fotografía frontal activa del usuario.
     */
    BiometricPhoto getPhotoByUserId(UserId userId);
}

