package edu.dosw.styleradar.virtualtryon.domain.exception;

import java.util.UUID;

/**
 * Se lanza cuando se consulta un trabajo de Virtual Try-On que no existe en el sistema.
 * Mapea a HTTP 404 Not Found.
 */
public class JobNotFoundException extends DomainException {

    public JobNotFoundException(UUID jobId) {
        super("No se encontró el trabajo de probador virtual con identificador: " + jobId, "JOB_NOT_FOUND");
    }

    public JobNotFoundException(String message) {
        super(message, "JOB_NOT_FOUND");
    }
}
