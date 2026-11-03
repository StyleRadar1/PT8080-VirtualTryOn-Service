package edu.dosw.styleradar.virtualtryon.domain.exception;

import java.util.UUID;

/**
 * Se lanza cuando un usuario intenta realizar una prueba virtual pero ya agotó su límite mensual.
 * Mapea a HTTP 429 Too Many Requests.
 */
public class QuotaExceededException extends DomainException {

    public QuotaExceededException(UUID userId, int limit) {
        super(String.format("El usuario %s ha alcanzado el límite máximo de %d pruebas virtuales del período.", userId, limit), "QUOTA_EXCEEDED");
    }

    public QuotaExceededException(UUID userId, int used, int limit) {
        super(String.format("El usuario %s ha alcanzado el límite máximo de %d pruebas virtuales (consumidas: %d).", userId, limit, used), "QUOTA_EXCEEDED");
    }

    public QuotaExceededException(String message) {
        super(message, "QUOTA_EXCEEDED");
    }
}
