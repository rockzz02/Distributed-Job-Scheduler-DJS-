package com.djs.job.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.djs.job.model.Job;
import com.djs.job.model.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    Page<Job> findByStatusNot(JobStatus status, Pageable pageable);

    Optional<Job> findByIdAndStatusNot(UUID id, JobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j
            from Job j
            where j.status = :status
              and j.nextRunAt <= :now
            order by j.nextRunAt asc
            """)
    List<Job> findDueJobsForUpdate(
            @Param("status") JobStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
