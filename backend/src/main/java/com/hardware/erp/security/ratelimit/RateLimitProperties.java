package com.hardware.erp.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(

        boolean enabled,

        /** Login attempts per IP per minute. */
        int loginPerIpPerMinute,

        /** Login attempts per identifier per minute - stops slow drips at one account. */
        int loginPerIdentifierPerMinute,

        /** Forgot-password requests per IP per hour. */
        int forgotPasswordPerIpPerHour,

        /** Forgot-password requests per identifier per hour - stops mailbox flooding. */
        int forgotPasswordPerIdentifierPerHour,

        int resetPasswordPerIpPerHour,

        int refreshPerIpPerMinute,

        /** New tenant signups per IP per hour (CR-028) - registration creates a tenant + owner account, a far heavier action than a login attempt. */
        int registerPerIpPerHour
) {}
