package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida (Driven Port) para la persistencia de trabajos TryOnJob y registros de
 * auditoría JobAuditLog en PostgreSQL (probador_db) (SR-RF-25).
 */
public interface TryOnJobRepositoryPort {

    TryOnJob saveJob(TryOnJob job);

    Optional<TryOnJob> findJobById(UUID jobId);

    List<TryOnJob> findJobsByUserId(UserId userId);

    JobAuditLog saveAuditLog(JobAuditLog auditLog);

    List<JobAuditLog> findAuditLogsByJobId(UUID jobId);
}

