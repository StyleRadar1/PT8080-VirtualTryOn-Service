package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.DeleteBiometricDataService;
import edu.dosw.styleradar.virtualtryon.domain.event.PhotoPurgedDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobAccessDeniedException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.OutboxPublisherPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnEventPublisherPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación del servicio de aplicación para el Derecho al Olvido y eliminación física
 * de fotografías biométricas en Azure Blob Storage y PostgreSQL (Ley 1581 de 2012 · SR-RF-27).
 * Utiliza Transactional Outbox para publicar el evento tryon.photo.purged de forma consistente.
 */
@Service
@Transactional
public class DeleteBiometricDataServiceImpl implements DeleteBiometricDataService {

    private static final Logger log = LoggerFactory.getLogger(DeleteBiometricDataServiceImpl.class);
    public static final String EVENT_PHOTO_PURGED = PhotoPurgedDomainEvent.ROUTING_KEY;

    private final TryOnJobRepositoryPort jobRepositoryPort;
    private final BiometricDataRepositoryPort biometricDataRepositoryPort;
    private final BlobStoragePort blobStoragePort;
    private final OutboxPublisherPort outboxPublisherPort;
    private final TryOnEventPublisherPort eventPublisherPort;

    @Autowired
    public DeleteBiometricDataServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            BlobStoragePort blobStoragePort,
            OutboxPublisherPort outboxPublisherPort,
            @Autowired(required = false) TryOnEventPublisherPort eventPublisherPort
    ) {
        this.jobRepositoryPort = jobRepositoryPort;
        this.biometricDataRepositoryPort = biometricDataRepositoryPort;
        this.blobStoragePort = blobStoragePort;
        this.outboxPublisherPort = outboxPublisherPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    public DeleteBiometricDataServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            BlobStoragePort blobStoragePort,
            OutboxPublisherPort outboxPublisherPort
    ) {
        this(jobRepositoryPort, biometricDataRepositoryPort, blobStoragePort, outboxPublisherPort, null);
    }

    public DeleteBiometricDataServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            BlobStoragePort blobStoragePort,
            TryOnEventPublisherPort eventPublisherPort
    ) {
        this(jobRepositoryPort, biometricDataRepositoryPort, blobStoragePort, null, eventPublisherPort);
    }

    @Override
    public void deleteBiometricPhoto(UUID jobOrPhotoId, UserId callerUserId, boolean callerIsAdmin) {
        Optional<TryOnJob> maybeJob = jobRepositoryPort.findJobById(jobOrPhotoId);
        if (maybeJob.isPresent()) {
            TryOnJob job = maybeJob.get();
            if (!callerIsAdmin && !job.getUserId().equals(callerUserId)) {
                throw new JobAccessDeniedException(
                        "No tiene permisos para eliminar la fotografía biométrica de otro usuario"
                );
            }
            if (job.isPhotoPurged() && job.getResultImageUrl() == null) {
                throw new JobNotFoundException(jobOrPhotoId);
            }

            String targetPhotoUrl = job.getPhotoUrl();
            Optional<BiometricPhoto> profilePhotoOpt = biometricDataRepositoryPort.findPhotoByUserId(job.getUserId());
            String profileBlobPath = profilePhotoOpt.map(BiometricPhoto::getBlobPath).orElse(null);
            String profilePhotoUrl = profilePhotoOpt.map(BiometricPhoto::getPhotoUrl).map(SasSignedUrl::value).orElse(null);

            boolean isJobPhotoMatchingProfile = targetPhotoUrl != null && (
                    sharesSamePhoto(targetPhotoUrl, profileBlobPath)
                            || sharesSamePhoto(targetPhotoUrl, profilePhotoUrl)
            );

            // 1. Purgar el trabajo solicitado directamente
            purgeSingleJobAndUserPhoto(job);

            // 2. SR-RF-27: Buscar y purgar todos los demás trabajos del usuario que compartan la misma fotografía biométrica,
            // eliminando sus renders físicos en Azure Blob Storage y revocando cualquier resultado asociado accesible.
            List<TryOnJob> userJobs = jobRepositoryPort.findJobsByUserId(job.getUserId());
            for (TryOnJob otherJob : userJobs) {
                if (otherJob.getId().equals(job.getId())) {
                    continue;
                }
                if (otherJob.isPhotoPurged() && otherJob.getResultImageUrl() == null) {
                    continue;
                }

                boolean sharesPhoto = sharesSamePhoto(targetPhotoUrl, otherJob.getPhotoUrl())
                        || (isJobPhotoMatchingProfile && (
                        sharesSamePhoto(profileBlobPath, otherJob.getPhotoUrl())
                                || sharesSamePhoto(profilePhotoUrl, otherJob.getPhotoUrl())
                ));

                if (sharesPhoto) {
                    purgeSingleJobAndUserPhoto(otherJob);
                    log.info("Trabajo asociado/compartido {} del usuario {} purgado por eliminación de fotografía común (SR-RF-27)",
                            otherJob.getId(), otherJob.getUserId().value());
                }
            }
            return;
        }

        Optional<BiometricPhoto> maybePhoto = biometricDataRepositoryPort.findPhotoById(jobOrPhotoId);
        if (maybePhoto.isPresent()) {
            BiometricPhoto photo = maybePhoto.get();
            if (!callerIsAdmin && !photo.getUserId().equals(callerUserId)) {
                throw new JobAccessDeniedException(
                        "No tiene permisos para eliminar la fotografía biométrica de otro usuario"
                );
            }
            blobStoragePort.deleteBlob(photo.getPhotoUrl());
            biometricDataRepositoryPort.deletePhotoByUserId(photo.getUserId());

            // Limpiar y purgar todos los trabajos del usuario para no dejar referencias biométricas pendientes
            List<TryOnJob> userJobs = jobRepositoryPort.findJobsByUserId(photo.getUserId());
            for (TryOnJob userJob : userJobs) {
                if (!userJob.isPhotoPurged() || userJob.getResultImageUrl() != null) {
                    purgeSingleJobAndUserPhoto(userJob);
                }
            }

            if (userJobs.isEmpty()) {
                publishPurgeEvent(null, photo.getUserId().value(), photo.getPhotoId());
            }

            log.info("Fotografía biométrica {} del usuario {} purgada físicamente y referencias en trabajos limpiadas (Ley 1581)",
                    photo.getPhotoId(), photo.getUserId().value());
            return;
        }

        throw new JobNotFoundException(jobOrPhotoId);
    }

    @Override
    public void purgeAllBiometricDataForUser(UserId userId) {
        biometricDataRepositoryPort.findPhotoByUserId(userId).ifPresent(photo -> {
            blobStoragePort.deleteBlob(photo.getPhotoUrl());
            biometricDataRepositoryPort.deletePhotoByUserId(userId);
        });

        List<TryOnJob> userJobs = jobRepositoryPort.findJobsByUserId(userId);
        for (TryOnJob job : userJobs) {
            if (!job.isPhotoPurged() || job.getResultImageUrl() != null) {
                purgeSingleJobAndUserPhoto(job);
            }
        }

        if (userJobs.isEmpty()) {
            publishPurgeEvent(null, userId.value(), UUID.randomUUID());
        }

        log.info("Purga total de datos biométricos ejecutada para usuario {} ({} trabajos afectados)",
                userId.value(), userJobs.size());
    }

    private void purgeSingleJobAndUserPhoto(TryOnJob job) {
        // 1. Borrado físico de la fotografía frontal (URL absoluta o ruta relativa en Azure Blob Storage)
        // Las excepciones de almacenamiento NO se capturan silenciosamente: deben propagarse para abortar la transacción
        // y evitar responder HTTP 204 o marcar purga exitosa cuando la eliminación física no ocurrió (Ley 1581 / GDPR).
        if (job.getPhotoUrl() != null && !job.getPhotoUrl().startsWith("PURGED")) {
            blobStoragePort.deleteBlob(job.getPhotoUrl());
        }

        // 2. Borrado físico de imagen de resultado generada
        if (job.getResultImageUrl() != null) {
            blobStoragePort.deleteBlob(job.getResultImageUrl());
        }

        // 3. Eliminar fotografía de perfil biométrico si existe
        biometricDataRepositoryPort.findPhotoByUserId(job.getUserId()).ifPresent(photo -> {
            blobStoragePort.deleteBlob(photo.getPhotoUrl());
            biometricDataRepositoryPort.deletePhotoByUserId(job.getUserId());
        });

        JobStatus previousStatus = job.getStatus();
        if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.PROCESSING) {
            job.cancel("PHOTO_PURGED: Cancelado por solicitud de purga de datos biométricos");
        }
        job.purgeBiometricPhoto();
        jobRepositoryPort.saveJob(job);
        jobRepositoryPort.deleteOutfitByJobId(job.getId());
        jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                job.getId(),
                previousStatus,
                job.getStatus(),
                "PHOTO_PURGED: Fotografía biométrica y render eliminados físicamente por solicitud del titular (Ley 1581)"
        ));

        publishPurgeEvent(job.getId(), job.getUserId().value(), job.getId());
    }

    private void publishPurgeEvent(UUID jobId, UUID userId, UUID aggregateId) {
        PhotoPurgedDomainEvent purgeEvent = PhotoPurgedDomainEvent.of(jobId, userId);
        if (outboxPublisherPort != null) {
            outboxPublisherPort.saveOutboxEvent(aggregateId, PhotoPurgedDomainEvent.ROUTING_KEY, purgeEvent);
            try {
                outboxPublisherPort.publishPendingEvents();
            } catch (Exception ex) {
                log.warn("Fallo temporal al despachar evento outbox de purga inmediatamente: {}", ex.getMessage());
            }
        } else if (eventPublisherPort != null) {
            try {
                eventPublisherPort.publishPhotoPurgedEvent(purgeEvent);
            } catch (Exception ex) {
                log.warn("Fallo al publicar evento de purga directo: {}", ex.getMessage());
            }
        }
    }

    static boolean sharesSamePhoto(String photoUrl1, String photoUrl2) {
        if (photoUrl1 == null || photoUrl2 == null || photoUrl1.isBlank() || photoUrl2.isBlank()) {
            return false;
        }
        if (photoUrl1.startsWith("PURGED") || photoUrl2.startsWith("PURGED")) {
            return false;
        }
        if (photoUrl1.trim().equalsIgnoreCase(photoUrl2.trim())) {
            return true;
        }
        String blob1 = extractBlobNameOrPath(photoUrl1);
        String blob2 = extractBlobNameOrPath(photoUrl2);
        return !blob1.isBlank() && blob1.equalsIgnoreCase(blob2);
    }

    static String extractBlobNameOrPath(String urlOrPath) {
        if (urlOrPath == null || urlOrPath.isBlank()) {
            return "";
        }
        String clean = urlOrPath.trim();
        int queryIdx = clean.indexOf('?');
        if (queryIdx >= 0) {
            clean = clean.substring(0, queryIdx);
        }
        try {
            if (clean.contains("://")) {
                URI uri = URI.create(clean).normalize();
                String path = uri.getPath();
                if (path != null && !path.isBlank()) {
                    while (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    int firstSlash = path.indexOf('/');
                    if (firstSlash >= 0) {
                        return path.substring(firstSlash + 1);
                    }
                    return path;
                }
            }
        } catch (Exception ignored) {
            // fallback al camino estándar
        }
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        return clean;
    }
}
