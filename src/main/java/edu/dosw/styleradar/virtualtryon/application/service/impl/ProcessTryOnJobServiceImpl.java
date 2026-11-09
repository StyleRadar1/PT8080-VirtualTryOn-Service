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
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
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
    private final BiometricDataRepositoryPort biometricDataRepositoryPort;
    private final BlobStoragePort blobStoragePort;

    @Autowired
    public ProcessTryOnJobServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            OutboxPublisherPort outboxPublisherPort,
            List<AiInferenceStrategy> inferenceStrategies,
            TransactionTemplate transactionTemplate,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            BlobStoragePort blobStoragePort
    ) {
        this.jobRepositoryPort = Objects.requireNonNull(jobRepositoryPort, "jobRepositoryPort no puede ser nulo");
        this.outboxPublisherPort = Objects.requireNonNull(outboxPublisherPort, "outboxPublisherPort es obligatorio para consistencia de eventos (Transactional Outbox)");
        this.inferenceStrategies = inferenceStrategies != null ? inferenceStrategies : List.of();
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate es obligatorio para garantizar atomicidad transaccional");
        this.biometricDataRepositoryPort = Objects.requireNonNull(biometricDataRepositoryPort, "biometricDataRepositoryPort es obligatorio antes de inferencia");
        this.blobStoragePort = Objects.requireNonNull(blobStoragePort, "blobStoragePort es obligatorio antes de inferencia");
    }

    /** Constructor de compatibilidad para pruebas unitarias sin infraestructura biométrica. */
    public ProcessTryOnJobServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            OutboxPublisherPort outboxPublisherPort,
            List<AiInferenceStrategy> inferenceStrategies,
            TransactionTemplate transactionTemplate
    ) {
        this.jobRepositoryPort = Objects.requireNonNull(jobRepositoryPort, "jobRepositoryPort no puede ser nulo");
        this.outboxPublisherPort = Objects.requireNonNull(outboxPublisherPort, "outboxPublisherPort no puede ser nulo");
        this.inferenceStrategies = inferenceStrategies != null ? inferenceStrategies : List.of();
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate no puede ser nulo");
        this.biometricDataRepositoryPort = null;
        this.blobStoragePort = null;
    }

    @Override
    public void processJob(UUID jobId) {
        // 1. Bloqueo pesimista y transición atómica a PROCESSING registrada en PostgreSQL
        TryOnJob job = executeInTransaction(() -> {
            TryOnJob lockedJob = jobRepositoryPort.findJobByIdForUpdate(jobId)
                    .orElseThrow(() -> new JobNotFoundException(jobId));

            if (lockedJob.getStatus() == JobStatus.COMPLETED || lockedJob.getStatus() == JobStatus.FAILED
                    || lockedJob.getStatus() == JobStatus.CANCELLED) {
                log.warn("El trabajo de prueba virtual {} no se encuentra en estado QUEUED (estado actual: {}), se omite procesamiento duplicado o fuera de secuencia",
                        jobId, lockedJob.getStatus());
                return null;
            }

            if (lockedJob.getStatus() == JobStatus.QUEUED) {
                lockedJob.startAiProcessing();
                jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                        lockedJob.getId(), JobStatus.QUEUED, JobStatus.PROCESSING,
                        "Iniciando inferencia generativa de prueba virtual con proveedor " + lockedJob.getAiProvider()
                ));
            }
            TryOnJob saved = jobRepositoryPort.saveJob(lockedJob);
            return saved != null ? saved : lockedJob;
        });

        if (job == null) {
            return;
        }

        // 2. Inferencia generativa ejecutada fuera del contexto transaccional JDBC
        long startTime = System.currentTimeMillis();
        try {
            revalidateBiometricAuthorization(job);
            AiInferenceStrategy strategy = selectStrategy(job.getAiProvider());
            SasSignedUrl resultUrl = strategy.submitTryOn(job);
            final SasSignedUrl persistedResultUrl = SasSignedUrl.of(blobStoragePort != null
                    ? blobStoragePort.normalizeBlobReference(resultUrl.value())
                    : resultUrl.value().split("\\?", 2)[0]);
            long elapsed = System.currentTimeMillis() - startTime;

            // 3. Persistencia atómica de estado COMPLETED, auditoría y evento Outbox en la misma transacción JDBC
            executeInTransaction(() -> {
                job.complete(persistedResultUrl, elapsed);
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
        } catch (ConsentRequiredException | InvalidPhotoException ex) {
            log.error("Fallo definitivo de validación biométrica para el trabajo {}: {}", jobId, ex.getMessage());
            markFailedAfterRetries(jobId, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Fallo durante la generación de prueba virtual para el trabajo {}: {}", jobId, ex.getMessage(), ex);

            log.warn("Inferencia fallida para trabajo {}; queda reintentable hasta que AMQP agote el backoff", jobId);
            throw ex;
        }
    }

    @Override
    public void markFailedAfterRetries(UUID jobId, String reason) {
        executeInTransaction(() -> {
            TryOnJob job = jobRepositoryPort.findJobByIdForUpdate(jobId)
                    .orElseThrow(() -> new JobNotFoundException(jobId));
            if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED
                    || job.getStatus() == JobStatus.CANCELLED) return job;
            JobStatus previousStatus = job.getStatus();
            job.markFailed(reason);
            TryOnJob saved = jobRepositoryPort.saveJob(job);
            TryOnJob targetJob = saved != null ? saved : job;
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    targetJob.getId(), previousStatus, JobStatus.FAILED,
                    "Reintentos AMQP agotados o fallo no recuperable: " + targetJob.getErrorMessage()
            ));
            outboxPublisherPort.saveOutboxEvent(targetJob.getId(), "tryon.job.failed", TryOnJobDomainEvent.fromJob(targetJob));
            return targetJob;
        });
    }

    @Override
    public int sweepStaleProcessingJobs(Duration maxProcessingAge) {
        Instant threshold = Instant.now().minus(maxProcessingAge != null ? maxProcessingAge : Duration.ofMinutes(10));
        List<TryOnJob> staleJobs = jobRepositoryPort.findStaleProcessingJobs(threshold);
        int recovered = 0;
        for (TryOnJob candidate : staleJobs) {
            try {
                boolean wasRecovered = Boolean.TRUE.equals(executeInTransaction(() -> {
                    // Adquirir bloqueo pesimista en base de datos para evitar condición de carrera con el worker en ejecución
                    TryOnJob lockedJob = jobRepositoryPort.findJobByIdForUpdate(candidate.getId())
                            .orElse(null);

                    if (lockedJob == null) {
                        return false;
                    }

                    // Verificar que siga estrictamente en PROCESSING y que su última actualización siga siendo anterior al threshold
                    if (lockedJob.getStatus() != JobStatus.PROCESSING) {
                        log.info("Trabajo {} ya no se encuentra en PROCESSING (estado actual: {}), se omite recuperación concurrente",
                                lockedJob.getId(), lockedJob.getStatus());
                        return false;
                    }

                    if (lockedJob.getUpdatedAt() != null && !lockedJob.getUpdatedAt().isBefore(threshold)) {
                        log.info("Trabajo {} fue actualizado recientemente ({}), el worker aún está activo. Se omite recuperación",
                                lockedJob.getId(), lockedJob.getUpdatedAt());
                        return false;
                    }

                    lockedJob.markFailed("Timeout de procesamiento: trabajo interrumpido por caída de worker");
                    jobRepositoryPort.saveJob(lockedJob);
                    jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                            lockedJob.getId(),
                            JobStatus.PROCESSING,
                            JobStatus.FAILED,
                            "Recuperación automática de trabajo bloqueado por caída de nodo ejecutor"
                    ));
                    outboxPublisherPort.saveOutboxEvent(lockedJob.getId(), "tryon.job.failed", TryOnJobDomainEvent.fromJob(lockedJob));
                    return true;
                }));

                if (wasRecovered) {
                    recovered++;
                }
            } catch (Exception ex) {
                log.error("Fallo al recuperar trabajo huérfano {}: {}", candidate.getId(), ex.getMessage());
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

    private void revalidateBiometricAuthorization(TryOnJob job) {
        if (biometricDataRepositoryPort == null || blobStoragePort == null) return;
        boolean validConsent = biometricDataRepositoryPort.findConsentByUserId(job.getUserId())
                .map(BiometricConsent::isValidForInference).orElse(false);
        if (!validConsent) throw new ConsentRequiredException("El consentimiento biométrico ya no está vigente");
        var photo = biometricDataRepositoryPort.findPhotoByUserId(job.getUserId())
                .orElseThrow(() -> new InvalidPhotoException("La fotografía biométrica ya no está disponible"));
        String currentPath = blobStoragePort.normalizeBlobReference(photo.getBlobPath());
        String jobPath = blobStoragePort.normalizeBlobReference(job.getPhotoUrl());
        if (!job.getUserId().equals(photo.getUserId()) || currentPath == null || jobPath == null
                || !(jobPath.equals(currentPath) || jobPath.endsWith("/" + currentPath))) {
            throw new InvalidPhotoException("La fotografía del trabajo no pertenece al titular autenticado");
        }
    }
}
