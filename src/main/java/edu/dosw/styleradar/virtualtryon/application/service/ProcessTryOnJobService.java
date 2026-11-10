package edu.dosw.styleradar.virtualtryon.application.service;

import edu.dosw.styleradar.virtualtryon.domain.port.in.ProcessTryOnJobUseCase;

import java.time.Duration;

/**
 * Interfaz de servicio de aplicación para el procesamiento asíncrono de trabajos
 * de prueba virtual disparados por eventos de RabbitMQ (SR-RF-25).
 */
public interface ProcessTryOnJobService extends ProcessTryOnJobUseCase {

    void markFailedAfterRetries(java.util.UUID jobId, String reason);

    int sweepStaleProcessingJobs(Duration maxProcessingAge);
}
