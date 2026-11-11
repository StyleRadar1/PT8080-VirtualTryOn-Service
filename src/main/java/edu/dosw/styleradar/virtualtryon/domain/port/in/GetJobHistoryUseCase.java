package edu.dosw.styleradar.virtualtryon.domain.port.in;

import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.List;

/**
 * Puerto de entrada (Use Case) para consultar el historial de trabajos de prueba virtual
 * de un usuario comprador (SR-RF-26).
 */
public interface GetJobHistoryUseCase {

    List<TryOnJob> getJobHistory(UserId userId);
}

