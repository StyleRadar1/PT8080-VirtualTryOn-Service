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
import edu.dosw.styleradar.virtualtryon.domain.exception.AiInferencePermanentException;
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

            if (lockedJob.getStatus() != JobStatus.QUEUED) {
                log.warn("El trabajo de prueba virtual {} no se encuentra en estado QUEUED (estado actual: {}), se omite procesamiento duplicado o fuera de secuencia",
                        jobId, lockedJob.getStatus());
                return null;
            }

            lockedJob.startAiProcessing();
            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                    lockedJob.getId(), JobStatus.QUEUED, JobStatus.PROCESSING,
                    "Iniciando inferencia generativa de prueba virtual con proveedor " + lockedJob.getAiProvider()
            ));
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

            // 3. Persistencia atómica de estado COMPLETED, auditoría y evento Outbox en la misma transacción JDBC,
            // validando el estado actualizado del trabajo contra purgas concurrentes (SR-RF-27)
            TryOnJob completedJob = executeInTransaction(() -> {
                TryOnJob lockedJob = jobRepositoryPort.findJobByIdForUpdate(jobId)
                        .orElse(null);

                if (lockedJob == null) {
                    log.warn("El trabajo {} ya no existe en la base de datos tras la inferencia; descartando render", jobId);
                    discardGeneratedImage(resultUrl.value(), jobId);
                    return null;
                }

                // Condición de carrera (RF-27): El trabajo fue purgado o cancelado concurrentemente mientras la IA ejecutaba
                if (lockedJob.isPhotoPurged() || lockedJob.getStatus() != JobStatus.PROCESSING) {
                    log.warn("El trabajo {} fue purgado o cancelado durante la inferencia IA (estado: {}, purgado: {}). Descartando render generado.",
                            jobId, lockedJob.getStatus(), lockedJob.isPhotoPurged());
                    discardGeneratedImage(resultUrl.value(), jobId);
                    return null;
                }

                // Condición de carrera (RF-27): La fotografía de perfil biométrico fue eliminada durante la inferencia
                if (biometricDataRepositoryPort != null && biometricDataRepositoryPort.findPhotoByUserId(lockedJob.getUserId()).isEmpty()) {
                    log.warn("Los datos biométricos del usuario {} fueron eliminados concurrentemente durante la inferencia del trabajo {}. Descartando render y purgando trabajo.",
                            lockedJob.getUserId().value(), jobId);
                    discardGeneratedImage(resultUrl.value(), jobId);
                    lockedJob.purgeBiometricPhoto();
                    jobRepositoryPort.saveJob(lockedJob);
                    return null;
                }

                lockedJob.complete(persistedResultUrl, elapsed);
                TryOnJob saved = jobRepositoryPort.saveJob(lockedJob);
                TryOnJob targetJob = saved != null ? saved : lockedJob;
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

            if (completedJob != null) {
                // Disparo opcional inmediato al Outbox
                try {
                    outboxPublisherPort.publishPendingEvents();
                } catch (Exception pubEx) {
                    log.warn("Despacho inmediato de evento tryon.job.completed diferido al publicador de outbox: {}", pubEx.getMessage());
                }

                log.info("Trabajo de prueba virtual {} finalizado con éxito en {} ms", jobId, elapsed);
            } else {
                log.info("Trabajo de prueba virtual {} finalizado sin persistir render debido a purga o cancelación previa", jobId);
            }
        } catch (ConsentRequiredException | InvalidPhotoException | AiInferencePermanentException ex) {
            log.error("Fallo definitivo no reintentable para el trabajo {}: {}", jobId, ex.getMessage());
            markFailedAfterRetries(jobId, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Fallo durante la generación de prueba virtual para el trabajo {}: {}", jobId, ex.getMessage(), ex);

            // Reencolar atómicamente a QUEUED para que el reintento AMQP con backoff sea procesable,
            // previniendo al mismo tiempo que entregas duplicadas concurrentes llamen a la IA en paralelo.
            try {
                executeInTransaction(() -> {
                    jobRepositoryPort.findJobByIdForUpdate(jobId).ifPresent(locked -> {
                        if (locked.getStatus() == JobStatus.PROCESSING) {
                            locked.requeueForRetry("Fallo transitorio de inferencia (" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
                            jobRepositoryPort.saveJob(locked);
                            jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                                    locked.getId(), JobStatus.PROCESSING, JobStatus.QUEUED,
                                    "Trabajo reencolado para reintento AMQP tras fallo transitorio"
                            ));
                        }
                    });
                    return null;
                });
            } catch (Exception requeueEx) {
                log.warn("No fue posible reencolar trabajo {} a QUEUED tras fallo: {}", jobId, requeueEx.getMessage());
            }

            log.warn("Inferencia fallida para trabajo {}; reencolado a QUEUED hasta que AMQP ejecute el backoff", jobId);
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

    private void discardGeneratedImage(String imagePathOrUrl, UUID jobId) {
        if (blobStoragePort == null || imagePathOrUrl == null || imagePathOrUrl.isBlank()) {
            return;
        }

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                blobStoragePort.deleteBlob(imagePathOrUrl);
                log.info("Render generado descartado y eliminado de Azure Blob Storage tras purga concurrente: {}", imagePathOrUrl);
                return;
            } catch (Exception ex) {
                log.warn("Intento {}/{} fallido al descartar render de Azure Blob Storage (ruta: {}, causa: {})",
                        attempt, maxRetries, imagePathOrUrl, ex.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(50L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Si la eliminación física falla tras los reintentos locales, registrar para limpieza persistente (SR-RF-27 / Ley 1581)
        registerPendingBlobCleanup(imagePathOrUrl, jobId);
    }

    private void registerPendingBlobCleanup(String imagePathOrUrl, UUID jobId) {
        log.error("[ORPHAN_CLEANUP_REQUIRED] Fallo definitivo al descartar render tras reintentos (blob: {}, jobId: {}). Registrando para limpieza persistente",
                imagePathOrUrl, jobId);
        if (jobRepositoryPort != null && jobId != null) {
            try {
                jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                        jobId,
                        JobStatus.PROCESSING,
                        JobStatus.FAILED,
                        "PENDING_BLOB_CLEANUP: Fallo al descartar render (" + imagePathOrUrl + ") tras purga concurrente. Registrado para reintento"
                ));
            } catch (Exception ex) {
                log.warn("No fue posible registrar log de auditoría para limpieza pendiente de blob: {}", ex.getMessage());
            }
        }
        if (outboxPublisherPort != null && jobId != null) {
            try {
                outboxPublisherPort.saveOutboxEvent(jobId, "tryon.blob.cleanup", imagePathOrUrl);
            } catch (Exception ex) {
                log.warn("No fue posible persistir evento outbox de limpieza pendiente de blob: {}", ex.getMessage());
            }
        }
    }
}
