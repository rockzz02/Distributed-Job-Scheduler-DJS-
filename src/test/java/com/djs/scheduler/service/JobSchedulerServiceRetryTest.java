package com.djs.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class JobSchedulerServiceRetryTest {

    private static final Instant NOW = Instant.parse("2026-06-13T10:00:00Z");

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionRepository jobExecutionRepository;

    @Mock
    private JobExecutionPublisher jobExecutionPublisher;

    @Mock
    private RetryService retryService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private TransactionStatus transactionStatus;

    private JobSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        SchedulerProperties properties = new SchedulerProperties(
                true,
                Duration.ofSeconds(10),
                100
        );

        schedulerService = new JobSchedulerService(
                jobRepository,
                jobExecutionRepository,
                jobExecutionPublisher,
                retryService,
                properties,
                transactionTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(transactionStatus);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void republishesDueFailedExecutionAsRetry() {
        UUID jobId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", jobId);

        JobExecution execution = new JobExecution();
        ReflectionTestUtils.setField(execution, "id", executionId);
        execution.setJob(job);
        execution.setStatus(JobExecutionStatus.FAILED);
        execution.setRetryCount(1);
        execution.setAttemptCount(1);
        execution.setScheduledAt(NOW.minusSeconds(60));
        execution.setNextRetryAt(NOW.minusSeconds(1));

        when(jobRepository.findDueJobsForUpdate(eq(JobStatus.ACTIVE), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of());
        when(jobExecutionRepository.findTimedOutRunningExecutionsForUpdate(eq(JobExecutionStatus.RUNNING), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of());
        when(jobExecutionRepository.findUnpublishedPendingExecutionsForUpdate(eq(JobExecutionStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());
        when(jobExecutionRepository.findRetryDueExecutionsForUpdate(eq(JobExecutionStatus.FAILED), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(execution));
        when(jobExecutionRepository.findByIdForUpdate(executionId)).thenReturn(Optional.of(execution));

        schedulerService.scheduleRunnableJobs();

        ArgumentCaptor<JobExecutionMessage> messageCaptor = ArgumentCaptor.forClass(JobExecutionMessage.class);
        verify(jobExecutionPublisher).publish(messageCaptor.capture());

        JobExecutionMessage message = messageCaptor.getValue();
        assertEquals(jobId, message.jobId());
        assertEquals(executionId, message.executionId());
        assertEquals(2, message.attemptNumber());
        assertEquals(JobExecutionStatus.PENDING, execution.getStatus());
        assertNotNull(execution.getQueuedAt());
        assertNull(execution.getNextRetryAt());
    }

    @Test
    void sendsTimedOutRunningExecutionThroughRetryPolicy() {
        UUID executionId = UUID.randomUUID();
        JobExecution execution = new JobExecution();
        ReflectionTestUtils.setField(execution, "id", executionId);
        execution.setStatus(JobExecutionStatus.RUNNING);
        execution.setLockedUntil(NOW.minusSeconds(1));

        when(jobExecutionRepository.findTimedOutRunningExecutionsForUpdate(eq(JobExecutionStatus.RUNNING), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(execution));
        when(retryService.handleFailure(executionId, "Worker lock expired before completion"))
                .thenReturn(RetryDecision.retryScheduled(executionId, 1, NOW.plusSeconds(30)));
        when(jobExecutionRepository.findUnpublishedPendingExecutionsForUpdate(eq(JobExecutionStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());
        when(jobRepository.findDueJobsForUpdate(eq(JobStatus.ACTIVE), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of());
        when(jobExecutionRepository.findRetryDueExecutionsForUpdate(eq(JobExecutionStatus.FAILED), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of());

        schedulerService.scheduleRunnableJobs();

        verify(retryService).handleFailure(executionId, "Worker lock expired before completion");
    }
}
