package com.djs.execution.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.djs.execution.model.JobExecution;
import com.djs.execution.model.JobExecutionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {

    Page<JobExecution> findByJob_IdOrderByCreatedAtDesc(UUID jobId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from JobExecution e
            where e.id = :executionId
            """)
    Optional<JobExecution> findByIdForUpdate(@Param("executionId") UUID executionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from JobExecution e
            join fetch e.job
            where e.status = :status
              and e.queuedAt is null
            order by e.scheduledAt asc
            """)
    List<JobExecution> findUnpublishedPendingExecutionsForUpdate(
            @Param("status") JobExecutionStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from JobExecution e
            join fetch e.job
            where e.status = :status
              and e.nextRetryAt <= :now
            order by e.nextRetryAt asc
            """)
    List<JobExecution> findRetryDueExecutionsForUpdate(
            @Param("status") JobExecutionStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from JobExecution e
            where e.status = :status
              and (e.lockedUntil is null or e.lockedUntil <= :now)
            order by e.lockedUntil asc
            """)
    List<JobExecution> findTimedOutRunningExecutionsForUpdate(
            @Param("status") JobExecutionStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
