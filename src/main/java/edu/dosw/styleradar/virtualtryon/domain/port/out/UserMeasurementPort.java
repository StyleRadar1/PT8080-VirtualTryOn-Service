package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.model.UserBodyMeasurements;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.Optional;

/**
 * Puerto de salida para consultar las medidas corporales del usuario
 * desde ms-usuarios para la evaluación orientativa de talla (SR-RF-24).
 */
public interface UserMeasurementPort {

    Optional<UserBodyMeasurements> findMeasurementsByUserId(UserId userId);
}
