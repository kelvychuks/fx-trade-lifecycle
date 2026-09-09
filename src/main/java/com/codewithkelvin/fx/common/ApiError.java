package com.codewithkelvin.fx.common;

import java.time.Instant;
import java.util.Map;

/**
 * One error shape for the whole API. `code` is stable and machine-readable;
 * `message` is for a human reading a log or a toast.
 */
public record ApiError(
        String code,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp
) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of(), Instant.now());
    }

    public static ApiError of(String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(code, message, fieldErrors, Instant.now());
    }
}
