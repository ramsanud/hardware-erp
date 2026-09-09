package com.hardware.erp.security.ratelimit;

import java.time.Duration;

public enum RateLimitRule {

    LOGIN_PER_IP(Duration.ofMinutes(1)),
    LOGIN_PER_IDENTIFIER(Duration.ofMinutes(1)),
    FORGOT_PASSWORD_PER_IP(Duration.ofHours(1)),
    FORGOT_PASSWORD_PER_IDENTIFIER(Duration.ofHours(1)),
    RESET_PASSWORD_PER_IP(Duration.ofHours(1)),
    REFRESH_PER_IP(Duration.ofMinutes(1)),
    REGISTER_PER_IP(Duration.ofHours(1)),

    /**
     * CR-062. The signup wizard's shop-name and identifier availability
     * lookups. Per minute, not per hour: a legitimate user types a shop name
     * and triggers several debounced checks in a single sitting, so an hourly
     * budget the size of REGISTER_PER_IP's would lock them out mid-form.
     */
    REGISTRATION_AVAILABILITY_PER_IP(Duration.ofMinutes(1)),

    /** Platform Admin Console login - a far smaller, far higher-value account pool than tenant logins. */
    PLATFORM_ADMIN_LOGIN_PER_IP(Duration.ofMinutes(1));

    private final Duration window;

    RateLimitRule(Duration window) {
        this.window = window;
    }

    public Duration window() {
        return window;
    }
}
