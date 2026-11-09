package edu.dosw.styleradar.virtualtryon.application.service;

import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.port.in.GetTryOnJobUseCase;
import edu.dosw.styleradar.virtualtryon.domain.port.in.GetUserQuotaUseCase;

/**
 * Interfaz de servicio de aplicación que unifica las consultas de cuotas mensuales,
 * validación de prendas seleccionadas del catálogo y consulta de trabajos de prueba virtual (SR-RF-24 y SR-RF-25).
 */
public interface QueryTryOnJobsService extends GetUserQuotaUseCase, GetTryOnJobUseCase {
}

