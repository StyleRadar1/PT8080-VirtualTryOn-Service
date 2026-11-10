package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnOutfit;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida (Driven Port) para la persistencia de trabajos TryOnJob, resultados TryOnOutfit
 * y registros de auditoría JobAuditLog en PostgreSQL (probador_db) (SR-RF-25 y SR-RF-26).
 */
public interface TryOnJobRepositoryPort {

    TryOnJob saveJob(TryOnJob job);

    Optional<TryOnJob> findJobById(UUID jobId);

    Optional<TryOnJob> findJobByIdForUpdate(UUID jobId);

    List<TryOnJob> findJobsByUserId(UserId userId);

    List<TryOnJob> findStaleProcessingJobs(Instant threshold);

    JobAuditLog saveAuditLog(JobAuditLog auditLog);

    List<JobAuditLog> findAuditLogsByJobId(UUID jobId);

    default TryOnOutfit saveOutfit(TryOnOutfit outfit) {
        return outfit;
    }

    default Optional<TryOnOutfit> findOutfitByJobId(UUID jobId) {
        return Optional.empty();
    }

    default void deleteOutfitByJobId(UUID jobId) {
    }
}
