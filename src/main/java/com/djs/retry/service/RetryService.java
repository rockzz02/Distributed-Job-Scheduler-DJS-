package com.djs.retry.service;

import java.util.UUID;

public interface RetryService {

    RetryDecision handleFailure(UUID executionId, String failureReason);
}
