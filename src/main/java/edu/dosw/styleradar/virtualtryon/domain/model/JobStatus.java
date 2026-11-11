package edu.dosw.styleradar.virtualtryon.domain.model;

/**
 * Estados del ciclo de vida de un trabajo de Probador Virtual (SR-RF-25).
 */
public enum JobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

