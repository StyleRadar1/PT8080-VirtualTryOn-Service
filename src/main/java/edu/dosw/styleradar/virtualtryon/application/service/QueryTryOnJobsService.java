package edu.dosw.styleradar.virtualtryon.application.service;

import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.port.in.GetJobHistoryUseCase;
import edu.dosw.styleradar.virtualtryon.domain.port.in.GetJobStatusUseCase;
import edu.dosw.styleradar.virtualtryon.domain.port.in.GetTryOnJobUseCase;
import edu.dosw.styleradar.virtualtryon.domain.port.in.GetUserQuotaUseCase;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.UUID;

/**
 * Interfaz de servicio de aplicación que unifica las consultas de cuotas mensuales,
 * validación de prendas seleccionadas del catálogo, consulta de estado/resultado de trabajos,
 * consulta de historial y purga biométrica (SR-RF-23 a SR-RF-26).
 */
public interface QueryTryOnJobsService extends GetUserQuotaUseCase, GetTryOnJobUseCase, GetJobStatusUseCase, GetJobHistoryUseCase {

    void purgeJobBiometricPhoto(UUID jobId, UserId callerUserId, boolean isAdmin);
}
