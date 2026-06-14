package com.djs.job.service;

import java.time.Instant;
import java.util.Map;

public record CreateJobCommand(
        String name,
        String description,
        String type,
        Map<String, Object> payload,
        String scheduleType,
        String cronExpression,
        Integer intervalSeconds,
        Instant nextRunAt,
        Integer maxRetries,
        String retryStrategy,
        Integer retryDelaySeconds,
        Integer timeoutSeconds
) {
}
