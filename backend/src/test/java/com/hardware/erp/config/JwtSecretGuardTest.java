package com.hardware.erp.config;

import com.hardware.erp.platformadmin.security.PlatformAdminJwtProperties;
import com.hardware.erp.security.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The signing-key half of the startup guards.
 *
 * This test class did not exist before 2026-09-04: the guard had covered
 * app.jwt.secret since CR-008 with no test, and covered the Platform Admin
 * Console's own key not at all. The second gap was real and reachable -
 * render.yaml declared no PLATFORM_ADMIN_JWT_SECRET, so a deploy from that
 * blueprint signed platform-admin sessions with the placeholder committed in
 * application.yml.
 */
class JwtSecretGuardTest {

    /** Must match application.yml's own defaults, which is the whole point. */
    private static final String PLACEHOLDER_APP = "Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tMzJieXRlcy1taW5pbXVtIQ==";
    private static final String PLACEHOLDER_PLATFORM_ADMIN = "UGxhdGZvcm0tQWRtaW4tZGV2LW9ubHktMzJieXRlcy1taW4h";

    private static final String REAL_APP = "cmVhbC1hcHAtc2VjcmV0LTMyYnl0ZXMtbWluaW11bS1oZXJlIQ==";
    private static final String REAL_PLATFORM_ADMIN = "cmVhbC1wbGF0Zm9ybS1hZG1pbi1zZWNyZXQtMzJieXRlcyE=";

    private void run(String appSecret, String platformAdminSecret, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);

        JwtProperties app = new JwtProperties(appSecret, "hardware-erp", 15, 7, 10);
        PlatformAdminJwtProperties platformAdmin =
                new PlatformAdminJwtProperties(platformAdminSecret, "hardware-erp-platform-admin", 15, 7, 10);

        new JwtSecretGuard(app, platformAdmin, environment)
                .onApplicationEvent(mock(ApplicationReadyEvent.class));
    }

    @Test
    @DisplayName("refuses production on the placeholder app JWT secret - a total authentication bypass")
    void refusesPlaceholderAppSecretInProduction() {
        assertThatThrownBy(() -> run(PLACEHOLDER_APP, REAL_PLATFORM_ADMIN, "prod", "cloud"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFUSING TO START");
    }

    @Test
    @DisplayName("refuses production on the placeholder PLATFORM ADMIN secret - forgeable console session for every tenant")
    void refusesPlaceholderPlatformAdminSecretInProduction() {
        assertThatThrownBy(() -> run(REAL_APP, PLACEHOLDER_PLATFORM_ADMIN, "prod", "cloud"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM ADMIN");
    }

    @Test
    @DisplayName("refuses production when both secrets are the same value, even though neither is a placeholder")
    void refusesSharedSecretInProduction() {
        // The case a naive "is it the placeholder?" check misses entirely: two
        // genuinely random keys are still wrong if they are the same key,
        // because it collapses two trust boundaries into one (CR-054).
        assertThatThrownBy(() -> run(REAL_APP, REAL_APP, "prod", "cloud"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same");
    }

    @Test
    @DisplayName("accepts production with two distinct real secrets")
    void acceptsDistinctRealSecrets() {
        assertThatCode(() -> run(REAL_APP, REAL_PLATFORM_ADMIN, "prod", "cloud"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("warns but does not block development - a fresh clone must still run")
    void allowsPlaceholdersOutsideProduction() {
        // Both placeholders at once, which is exactly what a clone with no
        // environment set has. Failing here would make the project unrunnable
        // out of the box, which is the reason the placeholders exist.
        assertThatCode(() -> run(PLACEHOLDER_APP, PLACEHOLDER_PLATFORM_ADMIN, "dev"))
                .doesNotThrowAnyException();
    }
}
