package com.hardware.erp.security.ratelimit;

import java.time.Duration;

public enum RateLimitRule {

    LOGIN_PER_IP(Duration.ofMinutes(1)),
    LOGIN_PER_IDENTIFIER(Duration.ofMinutes(1)),
    FORGOT_PASSWORD_PER_IP(Duration.ofHours(1)),
    FORGOT_PASSWORD_PER_IDENTIFIER(Duration.ofHours(1)),
    RESET_PASSWORD_PER_IP(Duration.ofHours(1)),
    REFRESH_PER_IP(Duration.ofMinutes(1)),
    REGISTER_PER_IP(Duration.ofHours(1));

    private final Duration window;

    RateLimitRule(Duration window) {
        this.window = window;
    }

    public Duration window() {
        return window;
    }
}
