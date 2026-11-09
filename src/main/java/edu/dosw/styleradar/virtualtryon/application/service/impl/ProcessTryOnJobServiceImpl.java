package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnEventPublisherPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.strategy.AiInferenceStrategy;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Servicio de aplicación que procesa de forma asíncrona los trabajos de prueba virtual (SR-RF-25).
 * Se ejecuta desacoplado de la petición HTTP inicial, activado por eventos AMQP en RabbitMQ:
 * 1. Pasa el trabajo a PROCESSING en PostgreSQL y registra JobAuditLog.
 * 2. Ejecuta la inferencia generativa (FASHN AI o Mock) fuera de transacciones de BD para no retener conexiones JDBC.
 * 3. Si la inferencia tiene éxito, actualiza a COMPLETED con SAS URL, persiste auditoría y publica tryon.job.completed.
 * 4. Si la inferencia falla, persiste FAILED con el motivo de error, persiste auditoría y publica tryon.job.failed.
 */
@Service
public class ProcessTryOnJobServiceImpl implements ProcessTryOnJobService {

    private static final Logger log = LoggerFactory.getLogger(ProcessTryOnJobServiceImpl.class);

    private final TryOnJobRepositoryPort jobRepositoryPort;
    private final TryOnEventPublisherPort eventPublisherPort;
    private final List<AiInferenceStrategy> inferenceStrategies;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public ProcessTryOnJobServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            TryOnEventPublisherPort eventPublisherPort,
            List<AiInferenceStrategy> inferenceStrategies,
            ObjectProvider<TransactionTemplate> transactionTemplateProvider
    ) {
        this.jobRepositoryPort = jobRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.inferenceStrategies = inferenceStrategies;
        this.transactionTemplate = transactionTemplateProvider != null ? transactionTemplateProvider.getIfAvailable() : null;
    }

    public ProcessTryOnJobServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            TryOnEventPublisherPort eventPublisherPort,
            List<AiInferenceStrategy> inferenceStrategies
    ) {
        this(jobRepositoryPort, eventPublisherPort, inferenceStrategies, null);
    }

    @Override
    public void processJob(UUID jobId) {
        TryOnJob job = jobRepositoryPort.findJobById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (job.getStatus() != JobStatus.QUEUED) {
            log.warn("El trabajo de prueba virtual {} no se encuentra en estado QUEUED (estado actual: {}), se omite procesamiento duplicado o fuera de secuencia",
                    jobId, job.getStatus());
            return;
        }

        // 1. Transición a PROCESSING registrada en PostgreSQL
        executeInTransaction(() -> {
            job.startAiProcessing();
            jobRepositoryPort.saveJob(job);
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    job.getId(),
                    JobStatus.QUEUED,
                    JobStatus.PROCESSING,
                    "Iniciando inferencia generativa de prueba virtual con proveedor " + job.getAiProvider()
            ));
            return null;
        });

        // 2. Inferencia generativa ejecutada fuera del contexto transaccional JDBC
        long startTime = System.currentTimeMillis();
        try {
            AiInferenceStrategy strategy = selectStrategy(job.getAiProvider());
            SasSignedUrl resultUrl = strategy.submitTryOn(job);
            long elapsed = System.currentTimeMillis() - startTime;

            executeInTransaction(() -> {
                job.complete(resultUrl, elapsed);
                TryOnJob completedJob = jobRepositoryPort.saveJob(job);
                TryOnJob targetJob = completedJob != null ? completedJob : job;
                jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                        targetJob.getId(),
                        JobStatus.PROCESSING,
                        JobStatus.COMPLETED,
                        "Generación de prueba virtual completada exitosamente en " + elapsed + " ms (" + targetJob.getAiProvider() + ")"
                ));
                eventPublisherPort.publishJobEvent(TryOnJobDomainEvent.fromJob(targetJob));
                return null;
            });
            log.info("Trabajo de prueba virtual {} finalizado con éxito en {} ms", jobId, elapsed);
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Fallo durante la generación de prueba virtual para el trabajo {}: {}", jobId, ex.getMessage(), ex);
            executeInTransaction(() -> {
                job.markFailed(ex.getMessage());
                TryOnJob failedJob = jobRepositoryPort.saveJob(job);
                TryOnJob targetJob = failedJob != null ? failedJob : job;
                jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                        targetJob.getId(),
                        JobStatus.PROCESSING,
                        JobStatus.FAILED,
                        "Error durante la inferencia de IA: " + targetJob.getErrorMessage()
                ));
                eventPublisherPort.publishJobEvent(TryOnJobDomainEvent.fromJob(targetJob));
                return null;
            });
            throw ex;
        }
    }

    private AiInferenceStrategy selectStrategy(AiProvider provider) {
        return inferenceStrategies.stream()
                .filter(strategy -> strategy.supportsProvider(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró una estrategia de inferencia registrada para el proveedor " + provider
                ));
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }
}

