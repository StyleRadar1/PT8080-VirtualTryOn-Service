package edu.dosw.styleradar.virtualtryon.domain.port.in;

import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentValidationResult;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de entrada (Use Case) para la consulta de cuotas mensuales de prueba virtual,
 * validación de prendas seleccionadas del catálogo y sincronización de catálogo (SR-RF-24).
 */
public interface GetUserQuotaUseCase {

    UserQuota getUserQuota(UserId userId);

    GarmentValidationResult validateGarmentEligibility(
            UserId userId,
            GarmentId garmentId,
            List<GarmentId> selectedGarmentIds
    );

    default GarmentValidationResult validateGarmentEligibility(UserId userId, GarmentId garmentId) {
        return validateGarmentEligibility(userId, garmentId, List.of());
    }

    CatalogGarment syncCatalogGarment(CatalogGarment garment);

    default Optional<CatalogGarment> updateCatalogGarmentVisibility(
            GarmentId garmentId,
            boolean visible,
            Instant updatedAt
    ) {
        return Optional.empty();
    }
}

