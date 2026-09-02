package com.hardware.erp.platformadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.platform-admin.bootstrap")
public record PlatformAdminBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String fullName
) {}
