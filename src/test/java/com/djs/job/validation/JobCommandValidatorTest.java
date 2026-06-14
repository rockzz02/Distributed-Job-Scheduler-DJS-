package com.djs.job.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import com.djs.job.service.CreateJobCommand;
import org.junit.jupiter.api.Test;

class JobCommandValidatorTest {

    private static final Instant NOW = Instant.parse("2026-06-13T10:00:00Z");

    private final JobCommandValidator validator = new JobCommandValidator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsValidFixedRateJob() {
        CreateJobCommand command = baseCommand(
                "FIXED_RATE",
                null,
                60,
                3,
                "EXPONENTIAL"
        );

        assertDoesNotThrow(() -> validator.validateCreate(command));
    }

    @Test
    void rejectsFixedRateJobWithCronExpression() {
        CreateJobCommand command = baseCommand(
                "FIXED_RATE",
                "0 */5 * * * *",
                60,
                3,
                "EXPONENTIAL"
        );

        assertThrows(JobValidationException.class, () -> validator.validateCreate(command));
    }

    @Test
    void rejectsInvalidCronExpression() {
        CreateJobCommand command = baseCommand(
                "CRON",
                "not-a-cron",
                null,
                3,
                "EXPONENTIAL"
        );

        assertThrows(JobValidationException.class, () -> validator.validateCreate(command));
    }

    @Test
    void rejectsRetryNoneWithRetryCount() {
        CreateJobCommand command = baseCommand(
                "ONE_TIME",
                null,
                null,
                1,
                "NONE"
        );

        assertThrows(JobValidationException.class, () -> validator.validateCreate(command));
    }

    private CreateJobCommand baseCommand(
            String scheduleType,
            String cronExpression,
            Integer intervalSeconds,
            int maxRetries,
            String retryStrategy
    ) {
        return new CreateJobCommand(
                "test-job",
                "test job",
                "NOOP",
                Map.of("source", "test"),
                scheduleType,
                cronExpression,
                intervalSeconds,
                NOW.plusSeconds(60),
                maxRetries,
                retryStrategy,
                30,
                120
        );
    }
}
