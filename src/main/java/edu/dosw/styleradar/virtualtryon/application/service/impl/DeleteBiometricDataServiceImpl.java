package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.DeleteBiometricDataService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnEventPublisherPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación del servicio de aplicación para el Derecho al Olvido y eliminación física
 * de fotografías biométricas en Azure Blob Storage y PostgreSQL (Ley 1581 de 2012 · SR-RF-27).
 */
@Service
@Transactional
public class DeleteBiometricDataServiceImpl implements DeleteBiometricDataService {

    private static final Logger log = LoggerFactory.getLogger(DeleteBiometricDataServiceImpl.class);
    public static final String EVENT_PHOTO_PURGED = "tryon.photo.purged";

    private final TryOnJobRepositoryPort jobRepositoryPort;
    private final BiometricDataRepositoryPort biometricDataRepositoryPort;
    private final BlobStoragePort blobStoragePort;
    private final TryOnEventPublisherPort eventPublisherPort;

    public DeleteBiometricDataServiceImpl(
            TryOnJobRepositoryPort jobRepositoryPort,
            BiometricDataRepositoryPort biometricDataRepositoryPort,
            BlobStoragePort blobStoragePort,
            TryOnEventPublisherPort eventPublisherPort
    ) {
        this.jobRepositoryPort = jobRepositoryPort;
        this.biometricDataRepositoryPort = biometricDataRepositoryPort;
        this.blobStoragePort = blobStoragePort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Override
    public void deleteBiometricPhoto(UUID jobOrPhotoId, UserId callerUserId, boolean callerIsAdmin) {
        Optional<TryOnJob> maybeJob = jobRepositoryPort.findJobById(jobOrPhotoId);
        if (maybeJob.isPresent()) {
            TryOnJob job = maybeJob.get();
            if (!callerIsAdmin && !job.getUserId().equals(callerUserId)) {
                throw new AccessDeniedException(
                        "No tiene permisos para eliminar la fotografía biométrica de otro usuario"
                );
            }
            purgeSingleJobAndUserPhoto(job);
            return;
        }

        Optional<BiometricPhoto> maybePhoto = biometricDataRepositoryPort.findPhotoById(jobOrPhotoId);
        if (maybePhoto.isPresent()) {
            BiometricPhoto photo = maybePhoto.get();
            if (!callerIsAdmin && !photo.getUserId().equals(callerUserId)) {
                throw new AccessDeniedException(
                        "No tiene permisos para eliminar la fotografía biométrica de otro usuario"
                );
            }
            blobStoragePort.deleteBlob(photo.getPhotoUrl());
            biometricDataRepositoryPort.deletePhotoByUserId(photo.getUserId());
            log.info("Fotografía biométrica {} del usuario {} purgada físicamente (Ley 1581)",
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
            if (!job.isPhotoPurged()) {
                purgeSingleJobAndUserPhoto(job);
            }
        }
        log.info("Purga total de datos biométricos ejecutada para usuario {} ({} trabajos afectados)",
                userId.value(), userJobs.size());
    }

    private void purgeSingleJobAndUserPhoto(TryOnJob job) {
        if (!job.isPhotoPurged() && job.getPhotoUrl() != null && job.getPhotoUrl().startsWith("http")) {
            try {
                blobStoragePort.deleteBlob(SasSignedUrl.of(job.getPhotoUrl()));
            } catch (IllegalArgumentException ignored) {
                log.debug("URL de foto original omitida en borrado directo: {}", job.getPhotoUrl());
            }
        }
        if (job.getResultImageUrl() != null) {
            blobStoragePort.deleteBlob(job.getResultImageUrl());
        }

        biometricDataRepositoryPort.findPhotoByUserId(job.getUserId()).ifPresent(photo -> {
            blobStoragePort.deleteBlob(photo.getPhotoUrl());
            biometricDataRepositoryPort.deletePhotoByUserId(job.getUserId());
        });

        JobStatus previousStatus = job.getStatus();
        job.purgeBiometricPhoto();
        jobRepositoryPort.saveJob(job);
        jobRepositoryPort.deleteOutfitByJobId(job.getId());
        jobRepositoryPort.saveAuditLog(JobAuditLog.ofTransition(
                job.getId(),
                previousStatus,
                job.getStatus(),
                "PHOTO_PURGED: Fotografía biométrica y render eliminados físicamente por solicitud del titular (Ley 1581)"
        ));

        eventPublisherPort.publishJobEvent(TryOnJobDomainEvent.fromJob(job));
    }
}
