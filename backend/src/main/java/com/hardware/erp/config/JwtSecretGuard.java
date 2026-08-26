package com.hardware.erp.config;

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

    private final JwtProperties jwtProperties;
    private final Environment environment;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");

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
