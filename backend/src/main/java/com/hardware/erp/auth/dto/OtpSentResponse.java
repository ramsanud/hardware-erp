package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * CR-078. Acknowledges a code sent by email - for a sign-in resend, a
 * step-up, or a registration - saying where it went, masked, and when the
 * next one may be asked for.
 */
@Schema(name = "OtpSentResponse")
public record OtpSentResponse(
        @Schema(example = "o***r@sarahardware.in") String emailHint,
        @Schema(description = "Seconds before another code may be requested", example = "60")
        int resendAfterSeconds
) {}
