package edu.dosw.styleradar.virtualtryon.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada (Use Case) para el procesamiento asíncrono y ejecución de inferencia
 * de un trabajo de prueba virtual (SR-RF-25).
 */
public interface ProcessTryOnJobUseCase {

    void processJob(UUID jobId);
}

