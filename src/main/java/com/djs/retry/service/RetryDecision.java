package com.djs.retry.service;

import java.time.Instant;
import java.util.UUID;

public record RetryDecision(
        UUID executionId,
        RetryDecisionType type,
        int retryCount,
        Instant nextRetryAt
) {

    public static RetryDecision retryScheduled(UUID executionId, int retryCount, Instant nextRetryAt) {
        return new RetryDecision(executionId, RetryDecisionType.RETRY_SCHEDULED, retryCount, nextRetryAt);
    }

    public static RetryDecision dead(UUID executionId, int retryCount) {
        return new RetryDecision(executionId, RetryDecisionType.DEAD, retryCount, null);
    }

    public static RetryDecision ignored(UUID executionId, int retryCount) {
        return new RetryDecision(executionId, RetryDecisionType.IGNORED, retryCount, null);
    }
}
