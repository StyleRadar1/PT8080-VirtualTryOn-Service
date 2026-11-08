package edu.dosw.styleradar.virtualtryon.application.service;

import edu.dosw.styleradar.virtualtryon.domain.port.in.ManageBiometricConsentUseCase;
import edu.dosw.styleradar.virtualtryon.domain.port.in.UploadBiometricPhotoUseCase;

/**
 * Interfaz del servicio de aplicación que orquesta el consentimiento de datos biométricos
 * y la carga validada de la fotografía frontal de silueta del usuario (SR-RF-23).
 */
public interface BiometricConsentService extends ManageBiometricConsentUseCase, UploadBiometricPhotoUseCase {
}

