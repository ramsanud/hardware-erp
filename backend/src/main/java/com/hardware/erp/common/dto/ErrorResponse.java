package com.hardware.erp.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Error envelope. Shape is fixed for every 4xx/5xx response:
 * { "success": false, "message": "...", "code": "...", "timestamp": "...",
 *   "errors": { "field": "reason" } }
 *
 * Never carries a stack trace, SQL text, or any internal identifier.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        String message,
        String code,
        String path,
        String requestId,
        OffsetDateTime timestamp,
        Map<String, String> errors
) {
    public static ErrorResponse of(String code, String message, String path, String requestId) {
        return new ErrorResponse(false, message, code, path, requestId,
                OffsetDateTime.now(), null);
    }

    public static ErrorResponse validation(String message, String path, String requestId,
                                           Map<String, String> errors) {
        return new ErrorResponse(false, message, "VALIDATION_ERROR", path, requestId,
                OffsetDateTime.now(), errors);
    }
}
