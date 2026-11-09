package edu.dosw.styleradar.virtualtryon.domain.port.in;

import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.UUID;

/**
 * Puerto de entrada (Use Case) para la consulta del estado y resultado de un trabajo
 * de prueba virtual con verificación de titularidad y prevención de IDOR (SR-RF-25 y SR-RF-26).
 */
public interface GetTryOnJobUseCase {

    TryOnJob getTryOnJob(UUID jobId, UserId callerUserId, boolean isAdmin);
}

