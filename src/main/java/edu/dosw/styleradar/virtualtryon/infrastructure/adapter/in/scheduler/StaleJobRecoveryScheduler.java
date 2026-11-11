package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.scheduler;

import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Adaptador de entrada (Scheduler) que ejecuta periódicamente la recuperación y barrido
 * de trabajos de prueba virtual estancados en estado PROCESSING que hayan excedido
 * el umbral máximo de procesamiento (SR-RF-25).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "styleradar.tryon.stale-job.enabled", havingValue = "true", matchIfMissing = true)
public class StaleJobRecoveryScheduler {

    private final ProcessTryOnJobService processTryOnJobService;
    private final Duration maxProcessingAge;

    public StaleJobRecoveryScheduler(
            ProcessTryOnJobService processTryOnJobService,
            @Value("${styleradar.tryon.stale-job.max-processing-age-minutes:10}") int maxProcessingAgeMinutes
    ) {
        this.processTryOnJobService = Objects.requireNonNull(processTryOnJobService, "processTryOnJobService no puede ser nulo");
        this.maxProcessingAge = Duration.ofMinutes(Math.max(1, maxProcessingAgeMinutes));
    }

    @Scheduled(
            fixedDelayString = "${styleradar.tryon.stale-job.sweep-interval-ms:60000}",
            initialDelayString = "${styleradar.tryon.stale-job.initial-delay-ms:10000}"
    )
    public void sweepStaleProcessingJobs() {
        try {
            int recovered = processTryOnJobService.sweepStaleProcessingJobs(maxProcessingAge);
            if (recovered > 0) {
                log.info("Barrido automático completado: {} trabajos estancados en PROCESSING fueron recuperados a FAILED", recovered);
            }
        } catch (Exception ex) {
            log.error("Error durante el barrido automático de trabajos estancados en PROCESSING: {}", ex.getMessage(), ex);
        }
    }
}

