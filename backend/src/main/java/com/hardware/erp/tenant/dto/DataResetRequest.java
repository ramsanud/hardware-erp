package com.hardware.erp.tenant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * CR-067. Both fields are the confirmation; neither replaces the other.
 *
 * @param confirmationPhrase the shop's own name, typed back. This is the half
 *                           that cannot be switched off by configuration -
 *                           CAPTCHA is inactive whenever its keys are absent
 *                           (CaptchaProperties.active), and a wipe must not
 *                           quietly lose its guard on such an install.
 * @param captchaToken       Turnstile token. Optional in the DTO for the same
 *                           reason LoginRequest's is: whether it is required is
 *                           a runtime decision, not a compile-time one. The
 *                           server rejects a missing token whenever a challenge
 *                           is actually active.
 */
public record DataResetRequest(
        @NotBlank(message = "Type the shop name to confirm")
        String confirmationPhrase,

        String captchaToken
) {}
