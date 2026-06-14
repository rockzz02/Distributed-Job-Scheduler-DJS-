package com.djs.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "djs.worker")
public record WorkerProperties(
        boolean enabled,
        String id,
        int concurrency
) {
}
