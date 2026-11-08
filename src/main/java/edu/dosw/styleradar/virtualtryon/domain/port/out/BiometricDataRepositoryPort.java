package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.Optional;

/**
 * Puerto de salida para la persistencia de consentimientos biométricos y metadatos de fotografías
 * de silueta en la base de datos del servicio (SR-RF-23).
 */
public interface BiometricDataRepositoryPort {

    /**
     * Persiste un consentimiento biométrico en el almacén de datos.
     */
    BiometricConsent saveConsent(BiometricConsent consent);

    /**
     * Busca el consentimiento biométrico registrado para un usuario específico.
     */
    Optional<BiometricConsent> findConsentByUserId(UserId userId);

    /**
     * Persiste o reemplaza el registro de fotografía frontal validada del usuario.
     */
    BiometricPhoto savePhoto(BiometricPhoto photo);

    /**
     * Busca los metadatos de la fotografía frontal cargada por el usuario.
     */
    Optional<BiometricPhoto> findPhotoByUserId(UserId userId);
}

