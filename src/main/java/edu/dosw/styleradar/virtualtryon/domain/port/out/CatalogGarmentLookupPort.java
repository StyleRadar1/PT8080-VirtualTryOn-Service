package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

import java.util.Optional;

/**
 * Puerto de salida para consultar y sincronizar bajo demanda una prenda desde ms-catalogo
 * cuando aún no existe en la proyección local catalog_garments (SR-RF-24 / FA-01).
 */
public interface CatalogGarmentLookupPort {

    Optional<CatalogGarment> findGarmentById(GarmentId garmentId);
}
