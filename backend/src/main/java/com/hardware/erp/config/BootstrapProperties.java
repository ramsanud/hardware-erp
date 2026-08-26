package com.hardware.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(

        /**
         * Defaults to false. Production must never silently create an account,
         * so the flag has to be set deliberately for the first run.
         */
        boolean enabled,

        String mobileNo,

        String email,

        String password,

        String fullName
) {}
