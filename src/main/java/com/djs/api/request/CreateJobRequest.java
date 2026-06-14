package com.djs.api.request;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 2_000)
        String description,

        @NotBlank
        @Size(max = 80)
        String type,

        @NotNull
        @Size(max = 100)
        Map<String, Object> payload,

        @NotBlank
        @Pattern(regexp = "ONE_TIME|FIXED_RATE|CRON")
        String scheduleType,

        @Size(max = 120)
        String cronExpression,

        @Positive
        Integer intervalSeconds,

        @NotNull
        @FutureOrPresent
        Instant nextRunAt,

        @NotNull
        @Min(0)
        Integer maxRetries,

        @NotBlank
        @Pattern(regexp = "NONE|FIXED|EXPONENTIAL")
        String retryStrategy,

        @NotNull
        @PositiveOrZero
        Integer retryDelaySeconds,

        @NotNull
        @Positive
        Integer timeoutSeconds
) {
}
