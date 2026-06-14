package com.djs.retry.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.djs.execution.model.JobExecution;
import com.djs.execution.model.JobExecutionStatus;
import com.djs.execution.repository.JobExecutionRepository;
import com.djs.job.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultRetryService implements RetryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultRetryService.class);

    private final JobExecutionRepository jobExecutionRepository;
    private final Clock clock;

    public DefaultRetryService(JobExecutionRepository jobExecutionRepository, Clock clock) {
        this.jobExecutionRepository = jobExecutionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RetryDecision handleFailure(UUID executionId, String failureReason) {
        JobExecution execution = jobExecutionRepository.findByIdForUpdate(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Job execution not found: " + executionId));

        if (execution.getStatus() != JobExecutionStatus.RUNNING) {
            log.info(
                    "Ignoring retry handling because execution is not running executionId={} status={}",
                    executionId,
                    execution.getStatus()
            );
            return RetryDecision.ignored(executionId, execution.getRetryCount());
        }

        Job job = execution.getJob();
        Instant now = clock.instant();
        int maxRetries = Math.max(0, job.getMaxRetries());
        int currentRetryCount = execution.getRetryCount();

        execution.setLastError(normalizeFailureReason(failureReason));
        execution.setLockedBy(null);
        execution.setLockedUntil(null);

        if (shouldMarkDead(job, currentRetryCount, maxRetries)) {
            execution.setStatus(JobExecutionStatus.DEAD);
            execution.setCompletedAt(now);
            execution.setNextRetryAt(null);
            log.info(
                    "Marked job execution dead executionId={} retryCount={} maxRetries={}",
                    executionId,
                    currentRetryCount,
                    maxRetries
            );
            return RetryDecision.dead(executionId, currentRetryCount);
        }

        int nextRetryCount = currentRetryCount + 1;
        Instant nextRetryAt = now.plus(calculateRetryDelay(job, nextRetryCount));

        execution.setStatus(JobExecutionStatus.FAILED);
        execution.setRetryCount(nextRetryCount);
        execution.setNextRetryAt(nextRetryAt);
        execution.setCompletedAt(null);

        log.info(
                "Scheduled retry for job execution executionId={} retryCount={} nextRetryAt={}",
                executionId,
                nextRetryCount,
                nextRetryAt
        );
        return RetryDecision.retryScheduled(executionId, nextRetryCount, nextRetryAt);
    }

    private boolean shouldMarkDead(Job job, int currentRetryCount, int maxRetries) {
        return currentRetryCount >= maxRetries || "NONE".equals(normalize(job.getRetryStrategy()));
    }

    private Duration calculateRetryDelay(Job job, int nextRetryCount) {
        long baseDelaySeconds = Math.max(0, job.getRetryDelaySeconds());
        String retryStrategy = normalize(job.getRetryStrategy());

        if ("EXPONENTIAL".equals(retryStrategy)) {
            long multiplier = 1L << Math.min(nextRetryCount - 1, 20);
            return Duration.ofSeconds(baseDelaySeconds * multiplier);
        }

        return Duration.ofSeconds(baseDelaySeconds);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "Job execution failed";
        }
        return failureReason;
    }
}
