package com.hardware.erp.config;

import com.hardware.erp.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /**
     * Empty for system operations - bootstrap seeding, scheduled cleanup - which
     * leaves created_by NULL rather than inventing an attribution.
     */
    @Bean
    public AuditorAware<Long> auditorAware() {
        return SecurityUtils::currentUserId;
    }
}
