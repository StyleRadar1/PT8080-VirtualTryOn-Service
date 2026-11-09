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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Servicio de aplicación que procesa de forma asíncrona los trabajos de prueba virtual (SR-RF-25).
 * Se ejecuta desacoplado de la petición HTTP inicial, activado por eventos AMQP en RabbitMQ:
 * 1. Pasa el trabajo a PROCESSING en PostgreSQL y registra JobAuditLog.
 * 2. Ejecuta la inferencia generativa (FASHN AI o Mock) fuera de transacciones de BD para no retener conexiones JDBC.
 * 3. Actualiza de forma atómica en PostgreSQL el estado final (COMPLETED o FAILED) con su auditoría.
 * 4. Publica en RabbitMQ POSTERIOR al commit de PostgreSQL para evitar inconsistencias Dual-Write.
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
            TransactionTemplate transactionTemplate
    ) {
        this.jobRepositoryPort = Objects.requireNonNull(jobRepositoryPort, "jobRepositoryPort no puede ser nulo");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort no puede ser nulo");
        this.inferenceStrategies = inferenceStrategies != null ? inferenceStrategies : List.of();
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate es obligatorio para garantizar atomicidad transaccional");
    }

    public ProcessTryOnJobServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            TryOnEventPublisherPort eventPublisherPort,
            List<AiInferenceStrategy> inferenceStrategies
    ) {
        this(jobRepositoryPort, eventPublisherPort, inferenceStrategies, createNoOpTransactionTemplate());
    }

    private static TransactionTemplate createNoOpTransactionTemplate() {
        return new TransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        });
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

            // 3. Persistencia de estado COMPLETED confirmada en base de datos
            TryOnJob completedJob = executeInTransaction(() -> {
                job.complete(resultUrl, elapsed);
                TryOnJob saved = jobRepositoryPort.saveJob(job);
                TryOnJob targetJob = saved != null ? saved : job;
                jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                        targetJob.getId(),
                        JobStatus.PROCESSING,
                        JobStatus.COMPLETED,
                        "Generación de prueba virtual completada exitosamente en " + elapsed + " ms (" + targetJob.getAiProvider() + ")"
                ));
                return targetJob;
            });

            // 4. Publicación en RabbitMQ POSTERIOR al commit de PostgreSQL (resuelve inconsistencias Dual-Write)
            try {
                eventPublisherPort.publishJobEvent(TryOnJobDomainEvent.fromJob(completedJob));
            } catch (Exception pubEx) {
                log.error("Fallo al publicar evento AMQP de finalización para el trabajo {}: {}", jobId, pubEx.getMessage(), pubEx);
            }

            log.info("Trabajo de prueba virtual {} finalizado con éxito en {} ms", jobId, elapsed);
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Fallo durante la generación de prueba virtual para el trabajo {}: {}", jobId, ex.getMessage(), ex);

            // Persistencia de estado FAILED confirmada en base de datos
            TryOnJob failedJob = executeInTransaction(() -> {
                job.markFailed(ex.getMessage());
                TryOnJob saved = jobRepositoryPort.saveJob(job);
                TryOnJob targetJob = saved != null ? saved : job;
                jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                        targetJob.getId(),
                        JobStatus.PROCESSING,
                        JobStatus.FAILED,
                        "Error durante la inferencia de IA: " + targetJob.getErrorMessage()
                ));
                return targetJob;
            });

            // Publicación de evento de fallo en RabbitMQ POSTERIOR al commit de PostgreSQL
            try {
                eventPublisherPort.publishJobEvent(TryOnJobDomainEvent.fromJob(failedJob));
            } catch (Exception pubEx) {
                log.error("Fallo al publicar evento AMQP de fallo para el trabajo {}: {}", jobId, pubEx.getMessage(), pubEx);
            }
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
        return transactionTemplate.execute(status -> action.get());
    }
}
