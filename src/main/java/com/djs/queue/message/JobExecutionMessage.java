package com.djs.queue.message;

import java.time.Instant;
import java.util.UUID;

public record JobExecutionMessage(
        UUID messageId,
        UUID jobId,
        UUID executionId,
        int attemptNumber,
        Instant scheduledAt,
        Instant publishedAt,
        UUID traceId
) {
}
