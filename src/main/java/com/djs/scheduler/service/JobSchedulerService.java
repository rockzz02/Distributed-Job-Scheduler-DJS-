package com.djs.scheduler.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.djs.execution.model.JobExecution;
import com.djs.execution.model.JobExecutionStatus;
import com.djs.execution.repository.JobExecutionRepository;
import com.djs.job.model.Job;
import com.djs.job.model.JobStatus;
import com.djs.job.repository.JobRepository;
import com.djs.queue.message.JobExecutionMessage;
import com.djs.queue.publisher.JobExecutionPublisher;
import com.djs.retry.service.RetryDecision;
import com.djs.retry.service.RetryService;
import com.djs.scheduler.config.SchedulerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class JobSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulerService.class);

    private static final int FIRST_ATTEMPT_NUMBER = 1;

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobExecutionPublisher jobExecutionPublisher;
    private final RetryService retryService;
    private final SchedulerProperties schedulerProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public JobSchedulerService(
            JobRepository jobRepository,
            JobExecutionRepository jobExecutionRepository,
            JobExecutionPublisher jobExecutionPublisher,
            RetryService retryService,
            SchedulerProperties schedulerProperties,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.jobExecutionPublisher = jobExecutionPublisher;
        this.retryService = retryService;
        this.schedulerProperties = schedulerProperties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${djs.scheduler.poll-interval:10s}")
    public void scheduleRunnableJobs() {
        if (!schedulerProperties.enabled()) {
            return;
        }

        try {
            Instant now = clock.instant();
            List<ScheduledExecution> scheduledExecutions = transactionTemplate.execute(
                    status -> createRunnableExecutions(now)
            );

            if (scheduledExecutions == null || scheduledExecutions.isEmpty()) {
                log.debug("Scheduler tick completed with no runnable jobs");
                return;
            }

            scheduledExecutions.forEach(this::publishExecution);
            log.info("Scheduler tick published {} job executions", scheduledExecutions.size());
        } catch (RuntimeException exception) {
            log.error("Scheduler tick failed", exception);
        }
    }

    private List<ScheduledExecution> createRunnableExecutions(Instant now) {
        recoverTimedOutRunningExecutions(now);

        List<ScheduledExecution> scheduledExecutions = new ArrayList<>();
        scheduledExecutions.addAll(recoverUnpublishedPendingExecutions());
        scheduledExecutions.addAll(createExecutionsForDueJobs(now));
        scheduledExecutions.addAll(scheduleDueRetries(now));
        return scheduledExecutions;
    }

    private void recoverTimedOutRunningExecutions(Instant now) {
        PageRequest pageRequest = PageRequest.of(0, Math.max(1, schedulerProperties.batchSize()));
        List<JobExecution> timedOutExecutions = jobExecutionRepository.findTimedOutRunningExecutionsForUpdate(
                JobExecutionStatus.RUNNING,
                now,
                pageRequest
        );

        for (JobExecution execution : timedOutExecutions) {
            RetryDecision decision = retryService.handleFailure(
                    execution.getId(),
                    "Worker lock expired before completion"
            );
            log.warn(
                    "Recovered timed-out running execution executionId={} decision={} retryCount={}",
                    execution.getId(),
                    decision.type(),
                    decision.retryCount()
            );
        }
    }

    private List<ScheduledExecution> recoverUnpublishedPendingExecutions() {
        PageRequest pageRequest = PageRequest.of(0, Math.max(1, schedulerProperties.batchSize()));
        List<JobExecution> unpublishedExecutions = jobExecutionRepository.findUnpublishedPendingExecutionsForUpdate(
                JobExecutionStatus.PENDING,
                pageRequest
        );
        List<ScheduledExecution> scheduledExecutions = new ArrayList<>(unpublishedExecutions.size());

        for (JobExecution execution : unpublishedExecutions) {
            scheduledExecutions.add(new ScheduledExecution(
                    execution.getJob().getId(),
                    execution.getId(),
                    execution.getAttemptCount() + 1,
                    execution.getScheduledAt()
            ));
        }

        if (!scheduledExecutions.isEmpty()) {
            log.info("Recovered {} unpublished pending executions", scheduledExecutions.size());
        }

        return scheduledExecutions;
    }

    private List<ScheduledExecution> createExecutionsForDueJobs(Instant now) {
        PageRequest pageRequest = PageRequest.of(0, Math.max(1, schedulerProperties.batchSize()));
        List<Job> dueJobs = jobRepository.findDueJobsForUpdate(JobStatus.ACTIVE, now, pageRequest);
        List<ScheduledExecution> scheduledExecutions = new ArrayList<>(dueJobs.size());

        for (Job job : dueJobs) {
            if (!advanceJobSchedule(job, now)) {
                continue;
            }

            JobExecution execution = new JobExecution();
            execution.setJob(job);
            execution.setScheduledAt(now);
            execution.setStatus(JobExecutionStatus.PENDING);
            execution.setAttemptCount(0);

            JobExecution savedExecution = jobExecutionRepository.save(execution);
            scheduledExecutions.add(new ScheduledExecution(
                    job.getId(),
                    savedExecution.getId(),
                    FIRST_ATTEMPT_NUMBER,
                    now
            ));
        }

        return scheduledExecutions;
    }

    private List<ScheduledExecution> scheduleDueRetries(Instant now) {
        PageRequest pageRequest = PageRequest.of(0, Math.max(1, schedulerProperties.batchSize()));
        List<JobExecution> retryDueExecutions = jobExecutionRepository.findRetryDueExecutionsForUpdate(
                JobExecutionStatus.FAILED,
                now,
                pageRequest
        );
        List<ScheduledExecution> scheduledExecutions = new ArrayList<>(retryDueExecutions.size());

        for (JobExecution execution : retryDueExecutions) {
            execution.setStatus(JobExecutionStatus.PENDING);
            execution.setQueuedAt(null);
            execution.setStartedAt(null);
            execution.setCompletedAt(null);
            execution.setNextRetryAt(null);
            execution.setLockedBy(null);
            execution.setLockedUntil(null);

            scheduledExecutions.add(new ScheduledExecution(
                    execution.getJob().getId(),
                    execution.getId(),
                    execution.getAttemptCount() + 1,
                    execution.getScheduledAt()
            ));
        }

        return scheduledExecutions;
    }

    private boolean advanceJobSchedule(Job job, Instant now) {
        String scheduleType = normalize(job.getScheduleType());

        return switch (scheduleType) {
            case "ONE_TIME" -> completeOneTimeJob(job);
            case "FIXED_RATE" -> advanceFixedRateJob(job, now);
            case "CRON" -> advanceCronJob(job, now);
            default -> {
                log.warn("Skipping job id={} because scheduleType={} is unsupported", job.getId(), job.getScheduleType());
                yield false;
            }
        };
    }

    private boolean completeOneTimeJob(Job job) {
        job.setStatus(JobStatus.COMPLETED);
        job.setNextRunAt(null);
        return true;
    }

    private boolean advanceFixedRateJob(Job job, Instant now) {
        Integer intervalSeconds = job.getIntervalSeconds();
        if (intervalSeconds == null || intervalSeconds <= 0) {
            log.warn("Skipping fixed-rate job id={} because intervalSeconds is invalid", job.getId());
            return false;
        }

        job.setNextRunAt(now.plusSeconds(intervalSeconds));
        return true;
    }

    private boolean advanceCronJob(Job job, Instant now) {
        String cronExpression = job.getCronExpression();
        if (cronExpression == null || cronExpression.isBlank()) {
            log.warn("Skipping cron job id={} because cronExpression is blank", job.getId());
            return false;
        }

        try {
            ZonedDateTime nextRunAt = CronExpression.parse(cronExpression)
                    .next(ZonedDateTime.ofInstant(now, ZoneOffset.UTC));
            if (nextRunAt == null) {
                log.warn("Skipping cron job id={} because no next run time could be calculated", job.getId());
                return false;
            }

            job.setNextRunAt(nextRunAt.toInstant());
            return true;
        } catch (IllegalArgumentException exception) {
            log.warn("Skipping cron job id={} because cronExpression is invalid", job.getId());
            return false;
        }
    }

    private void publishExecution(ScheduledExecution scheduledExecution) {
        Instant publishedAt = clock.instant();
        JobExecutionMessage message = new JobExecutionMessage(
                UUID.randomUUID(),
                scheduledExecution.jobId(),
                scheduledExecution.executionId(),
                scheduledExecution.attemptNumber(),
                scheduledExecution.scheduledAt(),
                publishedAt,
                UUID.randomUUID()
        );

        try {
            jobExecutionPublisher.publish(message);
            markExecutionPublished(scheduledExecution.executionId(), publishedAt);
        } catch (AmqpException exception) {
            log.error(
                    "Failed to publish job execution message jobId={} executionId={}",
                    scheduledExecution.jobId(),
                    scheduledExecution.executionId(),
                    exception
            );
        }
    }

    private void markExecutionPublished(UUID executionId, Instant queuedAt) {
        transactionTemplate.executeWithoutResult(status -> jobExecutionRepository.findByIdForUpdate(executionId)
                .filter(execution -> execution.getQueuedAt() == null)
                .ifPresent(execution -> {
                    execution.setQueuedAt(queuedAt);
                }));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record ScheduledExecution(
            UUID jobId,
            UUID executionId,
            int attemptNumber,
            Instant scheduledAt
    ) {
    }
}
