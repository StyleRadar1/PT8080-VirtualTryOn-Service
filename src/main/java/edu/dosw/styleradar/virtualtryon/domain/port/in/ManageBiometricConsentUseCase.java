package edu.dosw.styleradar.virtualtryon.domain.port.in;

import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.List;

/**
 * Puerto de entrada (Caso de Uso) para el otorgamiento, consulta y revocación del consentimiento
 * de tratamiento de datos biométricos según la Ley 1581 de 2012 (SR-RF-23 / SR-RNF-02).
 */
public interface ManageBiometricConsentUseCase {

    /**
     * Registra o actualiza el consentimiento biométrico explícito otorgado por el usuario.
     */
    BiometricConsent grantConsent(UserId userId, String termsVersion, boolean accepted, List<String> purposes, String ipAddress);

    /**
     * Consulta el consentimiento biométrico activo más reciente del usuario.
     */
    BiometricConsent getConsentByUserId(UserId userId);

    /**
     * Revoca el consentimiento biométrico activo del usuario.
     */
    BiometricConsent revokeConsent(UserId userId);
}

