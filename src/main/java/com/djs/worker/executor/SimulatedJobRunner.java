package com.djs.worker.executor;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulatedJobRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatedJobRunner.class);

    private static final int SUCCESS_PERCENTAGE = 70;
    private static final long MIN_EXECUTION_MILLIS = 250;
    private static final long MAX_EXECUTION_MILLIS = 1_500;

    public boolean run() {
        long executionMillis = ThreadLocalRandom.current().nextLong(MIN_EXECUTION_MILLIS, MAX_EXECUTION_MILLIS + 1);
        sleep(executionMillis);

        boolean success = ThreadLocalRandom.current().nextInt(100) < SUCCESS_PERCENTAGE;
        log.debug("Simulated job execution completed success={} durationMs={}", success, executionMillis);
        return success;
    }

    private void sleep(long executionMillis) {
        try {
            Thread.sleep(executionMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulated job execution was interrupted", exception);
        }
    }
}
