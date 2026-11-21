package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.model.GarmentSizeChart;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;

import java.util.Optional;

/**
 * Puerto de salida para consultar las tallas disponibles y la tabla de medidas de una prenda
 * desde ms-catalogo (Ítem-Catálogo) para la validación orientativa de talla (RN-02, RN-07 · SR-RF-24).
 */
public interface GarmentSizeMeasurementPort {

    Optional<GarmentSizeChart> findSizeChartByGarmentId(GarmentId garmentId);
}
