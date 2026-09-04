package com.hardware.erp.platformadmin.config;

import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRole;
import com.hardware.erp.platformadmin.entity.PlatformAdminStatus;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.service.PlatformAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Creates the very first SUPER_ADMIN account. Mirrors BootstrapOwnerInitializer
 * exactly (same gate shape, same "fail startup rather than create a weak
 * account" posture) with one deliberate difference: mfaEnabled is always left
 * false here. A TOTP secret cannot be bootstrapped from an environment
 * variable the way a password can - there is no phone to scan a QR code
 * during application startup - so this account, like any other, completes
 * MFA enrollment interactively on its first login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminBootstrapInitializer implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final Pattern STRONG = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{" + MIN_PASSWORD_LENGTH + ",}$");

    private final PlatformAdminBootstrapProperties properties;
    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService auditService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            if (platformAdminRepository.count() == 0) {
                log.warn("No platform admin exists and app.platform-admin.bootstrap.enabled is false. "
                         + "Set PLATFORM_ADMIN_BOOTSTRAP_ENABLED=true with "
                         + "PLATFORM_ADMIN_BOOTSTRAP_EMAIL and PLATFORM_ADMIN_BOOTSTRAP_PASSWORD "
                         + "to create the first SUPER_ADMIN account.");
            }
            return;
        }

        if (platformAdminRepository.count() > 0) {
            log.info("Platform admin bootstrap is enabled but accounts already exist; nothing to do. "
                     + "Set PLATFORM_ADMIN_BOOTSTRAP_ENABLED=false.");
            return;
        }

        validate();

        PlatformAdmin admin = PlatformAdmin.builder()
                .fullName(properties.fullName() == null || properties.fullName().isBlank()
                        ? "Platform Super Admin" : properties.fullName().trim())
                .email(properties.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(properties.password()))
                .role(PlatformAdminRole.SUPER_ADMIN)
                .status(PlatformAdminStatus.ACTIVE)
                .mfaEnabled(false)
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .build();

        PlatformAdmin saved = platformAdminRepository.save(admin);

        log.warn("Bootstrap platform SUPER_ADMIN created for email {}. It must complete MFA "
                 + "enrollment on first login. Set PLATFORM_ADMIN_BOOTSTRAP_ENABLED=false now.",
                saved.getEmail());

        auditService.success(PlatformAuditAction.BOOTSTRAP_SUPER_ADMIN_CREATED, saved, null);
    }

    private void validate() {
        if (properties.email() == null || !properties.email().contains("@")) {
            throw new IllegalStateException(
                    "app.platform-admin.bootstrap.email must be a valid email address "
                    + "(env PLATFORM_ADMIN_BOOTSTRAP_EMAIL)");
        }
        if (properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException(
                    "app.platform-admin.bootstrap.password must be set "
                    + "(env PLATFORM_ADMIN_BOOTSTRAP_PASSWORD)");
        }
        if (!STRONG.matcher(properties.password()).matches()) {
            throw new IllegalStateException(
                    "app.platform-admin.bootstrap.password must be at least " + MIN_PASSWORD_LENGTH
                    + " characters and contain a lowercase letter, an uppercase letter and a digit");
        }
    }
}
