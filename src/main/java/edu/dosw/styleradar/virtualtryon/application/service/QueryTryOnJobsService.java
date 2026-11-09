package edu.dosw.styleradar.virtualtryon.application.service;

import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.port.in.GetTryOnJobUseCase;
import edu.dosw.styleradar.virtualtryon.domain.port.in.GetUserQuotaUseCase;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.UUID;

/**
 * Interfaz de servicio de aplicación que unifica las consultas de cuotas mensuales,
 * validación de prendas seleccionadas del catálogo, consulta de trabajos y purga biométrica (SR-RF-23 a SR-RF-25).
 */
public interface QueryTryOnJobsService extends GetUserQuotaUseCase, GetTryOnJobUseCase {

    void purgeJobBiometricPhoto(UUID jobId, UserId callerUserId, boolean isAdmin);
}

