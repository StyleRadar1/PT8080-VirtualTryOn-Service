package edu.dosw.styleradar.virtualtryon.domain.port.in;

import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.UUID;

/**
 * Puerto de entrada (Use Case) para la consulta de estado y visualización del resultado
 * de alta resolución de un trabajo de prueba virtual (SR-RF-26).
 */
public interface GetJobStatusUseCase {

    /**
     * Recupera el estado y resultado del trabajo verificando la titularidad del usuario solicitante.
     */
    TryOnJob getJobStatus(UUID jobId, UserId callerUserId, boolean callerIsAdmin);
}

