package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.Optional;

/**
 * Puerto de salida (Driven Port) para la persistencia de cuotas mensuales de usuarios
 * y prendas sincronizadas del catálogo en PostgreSQL (probador_db) (SR-RF-24).
 */
public interface UserQuotaRepositoryPort {

    UserQuota saveQuota(UserQuota userQuota);

    Optional<UserQuota> findQuotaByUserIdAndPeriod(UserId userId, BillingPeriod billingPeriod);

    Optional<UserQuota> findQuotaByUserIdAndPeriodForUpdate(UserId userId, BillingPeriod billingPeriod);

    CatalogGarment saveCatalogGarment(CatalogGarment garment);

    Optional<CatalogGarment> findCatalogGarmentById(GarmentId garmentId);
}

