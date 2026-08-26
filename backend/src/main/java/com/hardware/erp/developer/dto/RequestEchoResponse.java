package com.hardware.erp.developer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Echoes back how the server saw the caller's request. The question it exists
 * to answer is "is the proxy in front of this app rewriting my headers, and is
 * my tenant/permission context what I think it is" - which is otherwise
 * guesswork behind Nginx.
 *
 * Credential-bearing headers are removed, not masked, before this is built.
 */
@Schema(description = "The calling request as the server received it, with credentials stripped")
public record RequestEchoResponse(

        @Schema(example = "GET") String method,
        @Schema(example = "/api/v1/dev/inspection/request-echo") String path,
        @Schema(description = "Correlation id also present in the logs and in every error body",
                example = "a1b2c3d4") String requestId,
        @Schema(description = "First X-Forwarded-For hop, or the socket address", example = "203.0.113.7")
        String clientIp,
        @Schema(description = "Signed-in user id") Long userId,
        @Schema(description = "Tenant the JWT resolved to, never a request parameter") Long tenantId,

        @Schema(description = "Request headers with Authorization, Cookie, Set-Cookie and X-API-Key removed")
        Map<String, String> headers
) {
}
