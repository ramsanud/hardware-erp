package com.hardware.erp.config;

import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.repository.RoleRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.auth.service.SecurityAuditService;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Creates the very first owner account.
 *
 * Replaces the previous "create an owner whenever the user table is empty" rule
 * (BUG-AUTH-004), which fired in production with a default password and logged
 * the credentials.
 *
 * Now:
 *   - disabled unless APP_BOOTSTRAP_ENABLED is explicitly true
 *   - refuses to start if the configured password is weak or missing
 *   - idempotent: does nothing once any user exists
 *   - never logs the password, not even at DEBUG
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapOwnerInitializer implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final Pattern MOBILE = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern STRONG = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{" + MIN_PASSWORD_LENGTH + ",}$");

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            if (userRepository.count() == 0) {
                log.warn("No users exist and app.bootstrap.enabled is false. "
                         + "Set APP_BOOTSTRAP_ENABLED=true with APP_BOOTSTRAP_MOBILE, "
                         + "APP_BOOTSTRAP_EMAIL and APP_BOOTSTRAP_PASSWORD to create "
                         + "the first owner account.");
            }
            return;
        }

        if (userRepository.count() > 0) {
            log.info("Bootstrap is enabled but users already exist; nothing to do. "
                     + "Set APP_BOOTSTRAP_ENABLED=false.");
            return;
        }

        validate();

        // Exactly one tenant exists at this stage: V6 seeds it, and
        // provisioning a second shop is deliberately out of scope for
        // CR-016 (see CHANGE_REQUEST_REGISTRY.md). Once that lands, this
        // bootstrap flow is replaced by real tenant provisioning.
        Tenant tenant = tenantRepository.findAll().stream().findFirst().orElseThrow(
                () -> new IllegalStateException(
                        "No tenant exists. The V6 migration has not run."));

        Role ownerRole = roleRepository.findByCodeAndTenantId("OWNER", tenant.getId()).orElseThrow(
                () -> new IllegalStateException(
                        "OWNER role is missing. The V1 migration has not run."));

        User owner = User.builder()
                .tenant(tenant)
                .role(ownerRole)
                .fullName(properties.fullName() == null || properties.fullName().isBlank()
                        ? "Shop Owner" : properties.fullName().trim())
                .mobileNo(properties.mobileNo().trim())
                .email(properties.email().trim().toLowerCase())
                .employeeCode("EMP001")
                .passwordHash(passwordEncoder.encode(properties.password()))
                .status(UserStatus.ACTIVE)
                // Always forced: the password came from an environment variable
                // that is visible to anyone who can read the deployment config.
                .mustChangePassword(true)
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .passwordChangedAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(owner);

        // Mobile number only. The password is never written to any log.
        log.warn("Bootstrap owner created for mobile {}. Change the password at first "
                 + "sign-in and set APP_BOOTSTRAP_ENABLED=false.", saved.getMobileNo());

        auditService.success(AuditAction.BOOTSTRAP_OWNER_CREATED, saved.getId(),
                saved.getFullName(), "USER", saved.getId());
    }

    /**
     * Fails startup rather than creating a weak owner account. A running
     * application with a guessable owner password is worse than one that does
     * not start.
     */
    private void validate() {
        if (properties.mobileNo() == null || !MOBILE.matcher(properties.mobileNo().trim()).matches()) {
            throw new IllegalStateException(
                    "app.bootstrap.mobile-no must be a valid 10-digit Indian mobile number "
                    + "(env APP_BOOTSTRAP_MOBILE)");
        }
        if (properties.email() == null || !properties.email().contains("@")) {
            throw new IllegalStateException(
                    "app.bootstrap.email must be a valid email address "
                    + "(env APP_BOOTSTRAP_EMAIL)");
        }
        if (properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException(
                    "app.bootstrap.password must be set (env APP_BOOTSTRAP_PASSWORD)");
        }
        if (!STRONG.matcher(properties.password()).matches()) {
            throw new IllegalStateException(
                    "app.bootstrap.password must be at least " + MIN_PASSWORD_LENGTH
                    + " characters and contain a lowercase letter, an uppercase letter "
                    + "and a digit");
        }
    }
}
