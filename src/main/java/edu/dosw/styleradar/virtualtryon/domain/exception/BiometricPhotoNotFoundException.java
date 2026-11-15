package edu.dosw.styleradar.virtualtryon.domain.exception;

import java.util.UUID;

/**
 * Excepción de dominio lanzada cuando el usuario no posee una fotografía biométrica activa
 * registrada en el Probador Virtual (SR-RF-23 / SR-RF-27).
 * Mapea a HTTP 404 Not Found.
 */
public class BiometricPhotoNotFoundException extends DomainException {

    public BiometricPhotoNotFoundException(UUID userId) {
        super("No existe una fotografía biométrica activa para el usuario: " + userId, "BIOMETRIC_PHOTO_NOT_FOUND");
    }

    public BiometricPhotoNotFoundException(String message) {
        super(message, "BIOMETRIC_PHOTO_NOT_FOUND");
    }
}

