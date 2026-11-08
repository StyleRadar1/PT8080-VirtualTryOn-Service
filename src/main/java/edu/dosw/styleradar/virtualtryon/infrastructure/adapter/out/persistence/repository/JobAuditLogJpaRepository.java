package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository;

import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.JobAuditLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para los registros de auditoría JobAuditLog en PostgreSQL (probador_db) (SR-RF-25).
 */
public interface JobAuditLogJpaRepository extends JpaRepository<JobAuditLogJpaEntity, UUID> {

    List<JobAuditLogJpaEntity> findByJobIdOrderByOccurredAtAsc(UUID jobId);
}

