package com.djs.common.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        String requestId,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
    public static ApiErrorResponse of(
            String requestId,
            int status,
            String error,
            String message,
            String path,
            List<String> details
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                requestId,
                status,
                error,
                message,
                path,
                details == null ? List.of() : List.copyOf(details)
        );
    }
}
