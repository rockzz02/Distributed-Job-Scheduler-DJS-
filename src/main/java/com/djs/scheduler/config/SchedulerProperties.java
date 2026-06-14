package com.djs.scheduler.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "djs.scheduler")
public record SchedulerProperties(
        boolean enabled,
        Duration pollInterval,
        int batchSize
) {
}
