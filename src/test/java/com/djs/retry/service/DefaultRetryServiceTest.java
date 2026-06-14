package com.djs.retry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.djs.execution.model.JobExecution;
import com.djs.execution.model.JobExecutionStatus;
import com.djs.execution.repository.JobExecutionRepository;
import com.djs.job.model.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultRetryServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-13T10:00:00Z");

    @Mock
    private JobExecutionRepository jobExecutionRepository;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void schedulesRetryWhenRetriesRemain() {
        UUID executionId = UUID.randomUUID();
        JobExecution execution = runningExecution("FIXED", 30, 3, 0);
        DefaultRetryService retryService = new DefaultRetryService(jobExecutionRepository, clock);

        when(jobExecutionRepository.findByIdForUpdate(executionId)).thenReturn(Optional.of(execution));

        RetryDecision decision = retryService.handleFailure(executionId, "boom");

        assertEquals(RetryDecisionType.RETRY_SCHEDULED, decision.type());
        assertEquals(1, decision.retryCount());
        assertEquals(NOW.plusSeconds(30), decision.nextRetryAt());
        assertEquals(JobExecutionStatus.FAILED, execution.getStatus());
        assertEquals(1, execution.getRetryCount());
        assertEquals("boom", execution.getLastError());
        assertEquals(NOW.plusSeconds(30), execution.getNextRetryAt());
        assertNull(execution.getCompletedAt());
        assertNull(execution.getLockedBy());
        assertNull(execution.getLockedUntil());
    }

    @Test
    void marksDeadWhenRetriesAreExhausted() {
        UUID executionId = UUID.randomUUID();
        JobExecution execution = runningExecution("FIXED", 30, 2, 2);
        DefaultRetryService retryService = new DefaultRetryService(jobExecutionRepository, clock);

        when(jobExecutionRepository.findByIdForUpdate(executionId)).thenReturn(Optional.of(execution));

        RetryDecision decision = retryService.handleFailure(executionId, "still failing");

        assertEquals(RetryDecisionType.DEAD, decision.type());
        assertEquals(2, decision.retryCount());
        assertNull(decision.nextRetryAt());
        assertEquals(JobExecutionStatus.DEAD, execution.getStatus());
        assertEquals(2, execution.getRetryCount());
        assertEquals("still failing", execution.getLastError());
        assertEquals(NOW, execution.getCompletedAt());
        assertNull(execution.getNextRetryAt());
        assertNull(execution.getLockedBy());
        assertNull(execution.getLockedUntil());
    }

    @Test
    void usesExponentialBackoffForRetryDelay() {
        UUID executionId = UUID.randomUUID();
        JobExecution execution = runningExecution("EXPONENTIAL", 5, 3, 1);
        DefaultRetryService retryService = new DefaultRetryService(jobExecutionRepository, clock);

        when(jobExecutionRepository.findByIdForUpdate(executionId)).thenReturn(Optional.of(execution));

        RetryDecision decision = retryService.handleFailure(executionId, "retry with backoff");

        assertEquals(RetryDecisionType.RETRY_SCHEDULED, decision.type());
        assertEquals(2, decision.retryCount());
        assertEquals(NOW.plusSeconds(10), decision.nextRetryAt());
        assertEquals(JobExecutionStatus.FAILED, execution.getStatus());
        assertEquals(2, execution.getRetryCount());
        assertEquals(NOW.plusSeconds(10), execution.getNextRetryAt());
    }

    private JobExecution runningExecution(String retryStrategy, int retryDelaySeconds, int maxRetries, int retryCount) {
        Job job = new Job();
        job.setRetryStrategy(retryStrategy);
        job.setRetryDelaySeconds(retryDelaySeconds);
        job.setMaxRetries(maxRetries);

        JobExecution execution = new JobExecution();
        execution.setJob(job);
        execution.setStatus(JobExecutionStatus.RUNNING);
        execution.setRetryCount(retryCount);
        execution.setLockedBy("worker-1");
        execution.setLockedUntil(NOW.plusSeconds(60));
        return execution;
    }
}
