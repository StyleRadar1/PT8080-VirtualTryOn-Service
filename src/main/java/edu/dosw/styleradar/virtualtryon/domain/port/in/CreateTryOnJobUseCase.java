package edu.dosw.styleradar.virtualtryon.domain.port.in;

import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

/**
 * Puerto de entrada (Use Case) para la creación y despacho de un trabajo de prueba virtual con IA (SR-RF-25).
 */
public interface CreateTryOnJobUseCase {

    TryOnJob createTryOnJob(UserId userId, GarmentId garmentId, String photoUrl, boolean biometricConsent);
}

