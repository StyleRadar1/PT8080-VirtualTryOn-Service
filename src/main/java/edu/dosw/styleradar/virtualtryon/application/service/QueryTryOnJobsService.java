package edu.dosw.styleradar.virtualtryon.application.service;

import edu.dosw.styleradar.virtualtryon.domain.port.in.GetJobHistoryUseCase;
import edu.dosw.styleradar.virtualtryon.domain.port.in.GetJobStatusUseCase;
import edu.dosw.styleradar.virtualtryon.domain.port.in.GetUserQuotaUseCase;

/**
 * Interfaz de servicio de aplicación que unifica las consultas de cuotas mensuales,
 * validación de prendas seleccionadas, consulta de estado/resultado de trabajos y
 * consulta del historial del usuario (SR-RF-24 y SR-RF-26).
 */
public interface QueryTryOnJobsService extends GetUserQuotaUseCase, GetJobStatusUseCase, GetJobHistoryUseCase {
}
