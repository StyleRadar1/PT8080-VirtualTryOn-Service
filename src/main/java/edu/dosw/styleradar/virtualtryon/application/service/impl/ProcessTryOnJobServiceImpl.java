package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.port.out.OutboxPublisherPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.strategy.AiInferenceStrategy;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Servicio de aplicación que procesa trabajos encolados de prueba virtual invocando la estrategia de IA
 * correspondiente y actualizando su estado a COMPLETED o FAILED de manera atómica (SR-RF-25).
 * Emplea bloqueo pesimista en PostgreSQL para evitar condiciones de carrera por mensajes duplicados
 * y persiste los eventos de finalización (COMPLETED) o fallo (FAILED) en la tabla outbox_events dentro
 * de la misma transacción JDBC, garantizando consistencia y entrega At-Least-Once.
 */
@Service
public class ProcessTryOnJobServiceImpl implements ProcessTryOnJobService {

    private static final Logger log = LoggerFactory.getLogger(ProcessTryOnJobServiceImpl.class);

    private final TryOnJobRepositoryPort jobRepositoryPort;
    private final OutboxPublisherPort outboxPublisherPort;
    private final List<AiInferenceStrategy> inferenceStrategies;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public ProcessTryOnJobServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            OutboxPublisherPort outboxPublisherPort,
            List<AiInferenceStrategy> inferenceStrategies,
            TransactionTemplate transactionTemplate
    ) {
        this.jobRepositoryPort = Objects.requireNonNull(jobRepositoryPort, "jobRepositoryPort no puede ser nulo");
        this.outboxPublisherPort = Objects.requireNonNull(outboxPublisherPort, "outboxPublisherPort es obligatorio para consistencia de eventos (Transactional Outbox)");
        this.inferenceStrategies = inferenceStrategies != null ? inferenceStrategies : List.of();
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate es obligatorio para garantizar atomicidad transaccional");
    }

    @Override
    public void processJob(UUID jobId) {
        // 1. Bloqueo pesimista y transición atómica a PROCESSING registrada en PostgreSQL
        TryOnJob job = executeInTransaction(() -> {
            TryOnJob lockedJob = jobRepositoryPort.findJobByIdForUpdate(jobId)
                    .orElseThrow(() -> new JobNotFoundException(jobId));

            if (lockedJob.getStatus() != JobStatus.QUEUED) {
                log.warn("El trabajo de prueba virtual {} no se encuentra en estado QUEUED (estado actual: {}), se omite procesamiento duplicado o fuera de secuencia",
                        jobId, lockedJob.getStatus());
                return null;
            }

            lockedJob.startAiProcessing();
            TryOnJob saved = jobRepositoryPort.saveJob(lockedJob);
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    lockedJob.getId(),
                    JobStatus.QUEUED,
                    JobStatus.PROCESSING,
                    "Iniciando inferencia generativa de prueba virtual con proveedor " + lockedJob.getAiProvider()
            ));
            return saved != null ? saved : lockedJob;
        });

        if (job == null) {
            return;
        }

        // 2. Inferencia generativa ejecutada fuera del contexto transaccional JDBC
        long startTime = System.currentTimeMillis();
        try {
            AiInferenceStrategy strategy = selectStrategy(job.getAiProvider());
            SasSignedUrl resultUrl = strategy.submitTryOn(job);
            long elapsed = System.currentTimeMillis() - startTime;

            // 3. Persistencia atómica de estado COMPLETED, auditoría y evento Outbox en la misma transacción JDBC
            executeInTransaction(() -> {
                job.complete(resultUrl, elapsed);
                TryOnJob saved = jobRepositoryPort.saveJob(job);
                TryOnJob targetJob = saved != null ? saved : job;
                jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                        targetJob.getId(),
                        JobStatus.PROCESSING,
                        JobStatus.COMPLETED,
                        "Generación de prueba virtual completada exitosamente en " + elapsed + " ms (" + targetJob.getAiProvider() + ")"
                ));
                // Persistencia de evento en Transactional Outbox
                outboxPublisherPort.saveOutboxEvent(targetJob.getId(), "tryon.job.completed", TryOnJobDomainEvent.fromJob(targetJob));
                return targetJob;
            });

            // Disparo opcional inmediato al Outbox
            try {
                outboxPublisherPort.publishPendingEvents();
            } catch (Exception pubEx) {
                log.warn("Despacho inmediato de evento tryon.job.completed diferido al publicador de outbox: {}", pubEx.getMessage());
            }

            log.info("Trabajo de prueba virtual {} finalizado con éxito en {} ms", jobId, elapsed);
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Fallo durante la generación de prueba virtual para el trabajo {}: {}", jobId, ex.getMessage(), ex);

            // Persistencia atómica de estado FAILED, auditoría y evento Outbox en la misma transacción JDBC
            executeInTransaction(() -> {
                job.markFailed(ex.getMessage());
                TryOnJob saved = jobRepositoryPort.saveJob(job);
                TryOnJob targetJob = saved != null ? saved : job;
                jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                        targetJob.getId(),
                        JobStatus.PROCESSING,
                        JobStatus.FAILED,
                        "Error durante la inferencia de IA: " + targetJob.getErrorMessage()
                ));
                // Persistencia de evento de fallo en Transactional Outbox
                outboxPublisherPort.saveOutboxEvent(targetJob.getId(), "tryon.job.failed", TryOnJobDomainEvent.fromJob(targetJob));
                return targetJob;
            });

            // Disparo opcional inmediato al Outbox
            try {
                outboxPublisherPort.publishPendingEvents();
            } catch (Exception pubEx) {
                log.warn("Despacho inmediato de evento tryon.job.failed diferido al publicador de outbox: {}", pubEx.getMessage());
            }

            throw ex;
        }
    }

    @Override
    public int sweepStaleProcessingJobs(Duration maxProcessingAge) {
        Instant threshold = Instant.now().minus(maxProcessingAge != null ? maxProcessingAge : Duration.ofMinutes(10));
        List<TryOnJob> staleJobs = jobRepositoryPort.findStaleProcessingJobs(threshold);
        int recovered = 0;
        for (TryOnJob staleJob : staleJobs) {
            try {
                executeInTransaction(() -> {
                    staleJob.markFailed("Timeout de procesamiento: trabajo interrumpido por caída de worker");
                    jobRepositoryPort.saveJob(staleJob);
                    jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                            staleJob.getId(),
                            JobStatus.PROCESSING,
                            JobStatus.FAILED,
                            "Recuperación automática de trabajo bloqueado por caída de nodo ejecutor"
                    ));
                    outboxPublisherPort.saveOutboxEvent(staleJob.getId(), "tryon.job.failed", TryOnJobDomainEvent.fromJob(staleJob));
                    return null;
                });
                recovered++;
            } catch (Exception ex) {
                log.error("Fallo al recuperar trabajo huérfano {}: {}", staleJob.getId(), ex.getMessage());
            }
        }
        return recovered;
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private AiInferenceStrategy selectStrategy(AiProvider provider) {
        return inferenceStrategies.stream()
                .filter(strategy -> strategy.supportsProvider(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No existe una estrategia configurada para el proveedor: " + provider));
    }
}
