package edu.dosw.styleradar.virtualtryon.domain.port.in;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.UUID;

/**
 * Puerto de entrada (Use Case) para la eliminación y purga física definitiva de datos biométricos
 * (silueta frontal y resultados renderizados) en cumplimiento de la Ley 1581 de 2012 (Habeas Data) (SR-RF-27).
 */
public interface DeleteBiometricPhotoUseCase {

    /**
     * Purga físicamente la fotografía frontal y el resultado de un trabajo o foto biométrica específica,
     * verificando la titularidad del solicitante.
     */
    void deleteBiometricPhoto(UUID jobOrPhotoId, UserId callerUserId, boolean callerIsAdmin);

    /**
     * Purga todos los datos biométricos y trabajos asociados a una cuenta de usuario eliminada (evento user.account.deleted).
     */
    void purgeAllBiometricDataForUser(UserId userId);
}
