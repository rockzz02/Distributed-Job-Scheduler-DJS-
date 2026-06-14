package com.djs.job.validation;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import com.djs.job.service.CreateJobCommand;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class JobCommandValidator {

    private final Clock clock;

    public JobCommandValidator(Clock clock) {
        this.clock = clock;
    }

    public void validateCreate(CreateJobCommand command) {
        validateRequiredFields(command);
        String scheduleType = normalize(command.scheduleType());
        validateSchedule(command, scheduleType);
        validateRetry(command);
    }

    private void validateRequiredFields(CreateJobCommand command) {
        if (!hasText(command.name())) {
            throw new JobValidationException("name is required");
        }
        if (!hasText(command.type())) {
            throw new JobValidationException("type is required");
        }
        if (command.payload() == null) {
            throw new JobValidationException("payload is required");
        }
        if (command.maxRetries() == null || command.maxRetries() < 0) {
            throw new JobValidationException("maxRetries must be >= 0");
        }
        if (command.retryDelaySeconds() == null || command.retryDelaySeconds() < 0) {
            throw new JobValidationException("retryDelaySeconds must be >= 0");
        }
        if (command.timeoutSeconds() == null || command.timeoutSeconds() <= 0) {
            throw new JobValidationException("timeoutSeconds must be > 0");
        }
    }

    private void validateSchedule(CreateJobCommand command, String scheduleType) {
        if (command.nextRunAt() == null) {
            throw new JobValidationException("nextRunAt is required");
        }
        if (command.nextRunAt().isBefore(Instant.now(clock))) {
            throw new JobValidationException("nextRunAt must be in the present or future");
        }

        switch (scheduleType) {
            case "ONE_TIME" -> {
                if (command.intervalSeconds() != null || hasText(command.cronExpression())) {
                    throw new JobValidationException("ONE_TIME jobs must not define intervalSeconds or cronExpression");
                }
            }
            case "FIXED_RATE" -> {
                if (command.intervalSeconds() == null || command.intervalSeconds() <= 0) {
                    throw new JobValidationException("FIXED_RATE jobs require intervalSeconds > 0");
                }
                if (hasText(command.cronExpression())) {
                    throw new JobValidationException("FIXED_RATE jobs must not define cronExpression");
                }
            }
            case "CRON" -> {
                if (!hasText(command.cronExpression())) {
                    throw new JobValidationException("CRON jobs require cronExpression");
                }
                validateCronExpression(command.cronExpression());
                if (command.intervalSeconds() != null) {
                    throw new JobValidationException("CRON jobs must not define intervalSeconds");
                }
            }
            default -> throw new JobValidationException("Unsupported scheduleType: " + command.scheduleType());
        }
    }

    private void validateRetry(CreateJobCommand command) {
        String retryStrategy = normalize(command.retryStrategy());
        if (!"NONE".equals(retryStrategy) && !"FIXED".equals(retryStrategy) && !"EXPONENTIAL".equals(retryStrategy)) {
            throw new JobValidationException("Unsupported retryStrategy: " + command.retryStrategy());
        }
        if ("NONE".equals(retryStrategy) && command.maxRetries() != null && command.maxRetries() > 0) {
            throw new JobValidationException("retryStrategy NONE requires maxRetries to be 0");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void validateCronExpression(String cronExpression) {
        try {
            CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException exception) {
            throw new JobValidationException("cronExpression is invalid");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
