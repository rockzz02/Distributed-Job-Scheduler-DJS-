package com.djs.worker.executor;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.djs.execution.model.JobExecution;
import com.djs.execution.model.JobExecutionStatus;
import com.djs.execution.repository.JobExecutionRepository;
import com.djs.queue.message.JobExecutionMessage;
import com.djs.retry.service.RetryService;
import com.djs.worker.config.WorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DefaultJobExecutionWorker implements JobExecutionWorker {

    private static final Logger log = LoggerFactory.getLogger(DefaultJobExecutionWorker.class);

    private final JobExecutionRepository jobExecutionRepository;
    private final SimulatedJobRunner simulatedJobRunner;
    private final RetryService retryService;
    private final WorkerProperties workerProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DefaultJobExecutionWorker(
            JobExecutionRepository jobExecutionRepository,
            SimulatedJobRunner simulatedJobRunner,
            RetryService retryService,
            WorkerProperties workerProperties,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.jobExecutionRepository = jobExecutionRepository;
        this.simulatedJobRunner = simulatedJobRunner;
        this.retryService = retryService;
        this.workerProperties = workerProperties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public void process(JobExecutionMessage message) {
        ClaimedExecution claimedExecution = claimExecution(message);
        if (claimedExecution == null) {
            return;
        }

        boolean success;
        try {
            success = simulatedJobRunner.run();
        } catch (RuntimeException exception) {
            retryService.handleFailure(claimedExecution.executionId(), exception.getMessage());
            return;
        }

        if (success) {
            markSuccess(claimedExecution.executionId());
        } else {
            retryService.handleFailure(claimedExecution.executionId(), "Simulated job failure");
        }
    }

    private ClaimedExecution claimExecution(JobExecutionMessage message) {
        return transactionTemplate.execute(status -> {
            JobExecution execution = jobExecutionRepository.findByIdForUpdate(message.executionId())
                    .orElse(null);
            if (execution == null) {
                log.warn("Ignoring job execution message because execution was not found executionId={}", message.executionId());
                return null;
            }

            if (execution.getStatus() != JobExecutionStatus.PENDING) {
                log.info(
                        "Ignoring job execution message because execution is not pending executionId={} status={}",
                        execution.getId(),
                        execution.getStatus()
                );
                return null;
            }

            Instant now = clock.instant();
            execution.setStatus(JobExecutionStatus.RUNNING);
            execution.setStartedAt(now);
            execution.setCompletedAt(null);
            execution.setNextRetryAt(null);
            execution.setAttemptCount(Math.max(execution.getAttemptCount() + 1, message.attemptNumber()));
            execution.setLockedBy(workerProperties.id());
            execution.setLockedUntil(now.plusSeconds(Math.max(1, execution.getJob().getTimeoutSeconds())));

            log.info(
                    "Claimed job execution executionId={} jobId={} workerId={}",
                    execution.getId(),
                    message.jobId(),
                    workerProperties.id()
            );
            return new ClaimedExecution(execution.getId());
        });
    }

    private void markSuccess(UUID executionId) {
        transactionTemplate.executeWithoutResult(status -> jobExecutionRepository.findByIdForUpdate(executionId)
                .filter(execution -> execution.getStatus() == JobExecutionStatus.RUNNING)
                .ifPresent(execution -> {
                    Instant now = clock.instant();
                    execution.setStatus(JobExecutionStatus.SUCCESS);
                    execution.setCompletedAt(now);
                    execution.setLockedBy(null);
                    execution.setLockedUntil(null);
                    execution.setLastError(null);
                    execution.setNextRetryAt(null);
                    log.info("Completed job execution successfully executionId={}", executionId);
                }));
    }

    private record ClaimedExecution(UUID executionId) {
    }
}
