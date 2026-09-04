package com.hardware.erp.config;

import com.hardware.erp.platformadmin.security.PlatformAdminJwtProperties;
import com.hardware.erp.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Refuses to run in production with the development JWT secret.
 *
 * The placeholder in application.yml exists so a developer can clone and run.
 * Shipping it would mean anyone with the repository can forge a token for any
 * user, which is a total authentication bypass.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSecretGuard implements ApplicationListener<ApplicationReadyEvent> {

    /** Must match the default in application.yml. */
    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tMzJieXRlcy1taW5pbXVtIQ==",
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLTMyYnl0ZXMh");

    /**
     * CR-054's own placeholder, from application.yml's app.platform-admin.jwt
     * block. It was previously unguarded: this class only ever checked
     * app.jwt.secret, and render.yaml declared no PLATFORM_ADMIN_JWT_SECRET at
     * all - so a deploy from that blueprint came up signing Platform Admin
     * Console tokens with a key committed to this repository, and nothing
     * objected. That console is the highest-privilege surface in the product
     * (tenant management, data export, billing), so it needs the guard more
     * than the tenant app does, not less.
     */
    private static final Set<String> KNOWN_PLATFORM_ADMIN_PLACEHOLDERS = Set.of(
            "UGxhdGZvcm0tQWRtaW4tZGV2LW9ubHktMzJieXRlcy1taW4h");

    private final JwtProperties jwtProperties;
    private final PlatformAdminJwtProperties platformAdminJwtProperties;
    private final Environment environment;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (KNOWN_PLATFORM_ADMIN_PLACEHOLDERS.contains(platformAdminJwtProperties.secret())) {
            if (production) {
                throw new IllegalStateException("""

                        =====================================================
                         REFUSING TO START
                         The placeholder PLATFORM ADMIN JWT secret is in use
                         with the 'prod' profile. Anyone with the source can
                         forge a Platform Admin Console session - tenant
                         management, data export and billing for every shop.

                         Generate one and set it as PLATFORM_ADMIN_JWT_SECRET:
                             openssl rand -base64 32

                         It must be a DIFFERENT value from JWT_SECRET: the two
                         are separate trust boundaries (CR-054), and sharing
                         one key lets a token from either side be replayed at
                         the other.
                        =====================================================""");
            }
            log.warn("Using the development Platform Admin JWT secret. Set PLATFORM_ADMIN_JWT_SECRET before deploying.");
        }

        // A unique-but-shared key passes both placeholder checks above and is
        // still wrong, for the reason the refusal message gives.
        if (production && jwtProperties.secret().equals(platformAdminJwtProperties.secret())) {
            throw new IllegalStateException("""

                    =====================================================
                     REFUSING TO START
                     JWT_SECRET and PLATFORM_ADMIN_JWT_SECRET are the same
                     value. The Platform Admin Console is a separate trust
                     boundary from the shop application (CR-054); one shared
                     signing key means a token minted on either side can be
                     replayed against the other.

                     Generate a second, different value:
                         openssl rand -base64 32
                    =====================================================""");
        }

        if (KNOWN_PLACEHOLDERS.contains(jwtProperties.secret())) {
            if (production) {
                throw new IllegalStateException("""

                        =====================================================
                         REFUSING TO START
                         The placeholder JWT secret is in use with the 'prod'
                         profile. Anyone with the source can forge a token for
                         any user.

                         Generate one and set it as JWT_SECRET:
                             openssl rand -base64 32
                        =====================================================""");
            }
            log.warn("Using the development JWT secret. Set JWT_SECRET before deploying.");
        }
    }
}
