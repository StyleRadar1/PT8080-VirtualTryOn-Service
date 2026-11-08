package edu.dosw.styleradar.virtualtryon.domain.port.in;

import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

/**
 * Puerto de entrada (Use Case) para la consulta de cuotas mensuales de prueba virtual,
 * validación de prendas seleccionadas del catálogo y sincronización de catálogo (SR-RF-24).
 */
public interface GetUserQuotaUseCase {

    UserQuota getUserQuota(UserId userId);

    CatalogGarment validateGarmentEligibility(UserId userId, GarmentId garmentId);

    CatalogGarment syncCatalogGarment(CatalogGarment garment);
}

