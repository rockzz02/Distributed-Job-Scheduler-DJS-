package com.djs.api.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.djs.job.model.JobStatus;

public record JobResponse(
        UUID id,
        String name,
        String description,
        String type,
        Map<String, Object> payload,
        String scheduleType,
        String cronExpression,
        Integer intervalSeconds,
        Instant nextRunAt,
        JobStatus status,
        int maxRetries,
        String retryStrategy,
        int retryDelaySeconds,
        int timeoutSeconds,
        Instant createdAt,
        Instant updatedAt
) {
}
