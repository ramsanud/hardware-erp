package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body is optional. With the default cookie transport the refresh token arrives
 * in an HttpOnly cookie and this field is null.
 */
@Schema(name = "RefreshRequest")
public record RefreshRequest(

        @Schema(description = "Only used when app.security.refresh-token-transport=json",
                example = "null")
        String refreshToken
) {}
