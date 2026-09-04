package com.hardware.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * CR-059 - refuses to start on a deployment configuration that cannot be
 * correct, and prints what mode this process actually came up in.
 *
 * Same purpose as {@link JwtSecretGuard}: a configuration mistake that only
 * shows itself as wrong data hours later is far worse than a refusal at boot.
 * These are all silent in production otherwise:
 *
 * <ul>
 *   <li>A self-hosted install still pointed at the hosted SaaS database. The
 *       client's shop data would be written into the multi-tenant production
 *       database, and nothing in the UI would look wrong.</li>
 *   <li>The hosted SaaS running with cookie-secure=false. The refresh cookie -
 *       a 7-day credential - would then be sent over plain HTTP.</li>
 *   <li>The hosted SaaS pointed at a database inside its own container. It
 *       would start, serve, and lose every write on the next redeploy, because
 *       the platform hosts this app on ephemeral storage (see render.yaml).</li>
 * </ul>
 *
 * <h2>Why this is a BeanFactoryPostProcessor and not an ApplicationListener</h2>
 *
 * {@code JwtSecretGuard} checks on {@code ApplicationReadyEvent}, which is the
 * right place for it - a weak signing key is dangerous once requests are being
 * served, and not a moment before. This guard cannot afford that timing. By
 * {@code ApplicationReadyEvent}, Flyway has already run and Hibernate has
 * already connected: in the very case that matters most - a self-hosted
 * install pointed at the multi-tenant SaaS database - migrations would have
 * been applied to that database before anything objected.
 *
 * A {@code BeanFactoryPostProcessor} runs after component scanning but before
 * any singleton is instantiated, so the refusal happens before the datasource,
 * Flyway or the EntityManagerFactory exist. Properties are read straight from
 * the {@link Environment} for the same reason - the
 * {@code @ConfigurationProperties} beans have not been created yet at this
 * point, so this deliberately does not inject them.
 *
 * Every check is scoped to the 'prod' profile except the mutually-exclusive
 * profile check, which is a mistake in any environment.
 */
@Slf4j
@Component
public class DeploymentModeGuard implements BeanFactoryPostProcessor {

    /**
     * Host fragments that mean "somebody else's managed database". Matched as
     * substrings of the JDBC URL, so a Supabase direct connection
     * (db.xxxx.supabase.co) and its session pooler (xxxx.pooler.supabase.com)
     * both hit. Extend this list rather than adding a second check when a
     * different managed provider is adopted.
     */
    private static final List<String> MANAGED_DB_FRAGMENTS = List.of(
            "supabase.co", "supabase.com", "neon.tech", "rds.amazonaws.com", "azure.com");

    /** Kept in step with scripts/db-target.sh's own local-host list. */
    private static final List<String> LOCAL_DB_FRAGMENTS = List.of(
            "localhost", "127.0.0.1", "//postgres:", "//db:", "host.docker.internal");

    /**
     * The Environment is pulled from the bean factory rather than injected.
     * A BeanFactoryPostProcessor is instantiated before autowiring is
     * available - a constructor argument here fails outright with "No default
     * constructor found" - but the Environment is registered as a plain
     * singleton during {@code prepareBeanFactory}, long before this runs.
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        check(beanFactory.getBean(ConfigurableApplicationContext.ENVIRONMENT_BEAN_NAME, Environment.class));
    }

    /** Package-private so the test can drive it against a MockEnvironment. */
    void check(Environment environment) {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        boolean production = profiles.contains("prod");

        if (profiles.contains("cloud") && profiles.contains("selfhosted")) {
            throw new IllegalStateException(refusal("""
                     Both the 'cloud' and 'selfhosted' profiles are active.
                     They set opposite defaults for the database, the refresh
                     cookie and billing, and the winner depends on ordering.

                     Pick one:
                         SPRING_PROFILES_ACTIVE=prod,cloud
                         SPRING_PROFILES_ACTIVE=prod,selfhosted"""));
        }

        DeploymentProperties deployment = Binder.get(environment)
                .bind("app.deployment", DeploymentProperties.class)
                .orElseGet(() -> new DeploymentProperties(null, null, null));

        // Only this one property is needed, so it is read directly rather than
        // binding the whole SecurityProperties record - which would also pull
        // in allowed-origins and the transport enum for no reason. 'true' is
        // the default in application.yml and the safe assumption if unset.
        boolean cookieSecure = environment.getProperty("app.security.cookie-secure", Boolean.class, true);
        boolean mfaRequired = environment.getProperty("app.security.mfa-required", Boolean.class, true);

        String jdbcUrl = environment.getProperty("spring.datasource.url", "").toLowerCase(Locale.ROOT);
        boolean managedDb = MANAGED_DB_FRAGMENTS.stream().anyMatch(jdbcUrl::contains);
        boolean localDb = LOCAL_DB_FRAGMENTS.stream().anyMatch(jdbcUrl::contains);

        if (production && deployment.selfHosted() && managedDb) {
            throw new IllegalStateException(refusal("""
                     APP_DEPLOYMENT_MODE is SELF_HOSTED but DB_HOST points at a
                     hosted database. A self-hosted install must never write
                     into the multi-tenant SaaS database - that is one shop's
                     data landing in another company's system.

                     Point DB_HOST at this machine's own PostgreSQL container
                     (docker-compose.selfhosted.yml calls it 'postgres'), or
                     switch to SPRING_PROFILES_ACTIVE=prod,cloud if this really
                     is the hosted deployment."""));
        }

        if (production && deployment.mode().isCloud() && localDb) {
            throw new IllegalStateException(refusal("""
                     APP_DEPLOYMENT_MODE is CLOUD but DB_HOST resolves to this
                     container. Hosted instances run on ephemeral storage, so
                     this would appear to work and then lose every record on
                     the next redeploy.

                     Set DB_HOST/DB_USER/DB_PASSWORD to the managed database
                     (Supabase session pooler on port 5432 - never the
                     transaction pooler on 6543, see render.yaml)."""));
        }

        if (production && deployment.mode().isCloud() && !cookieSecure) {
            throw new IllegalStateException(refusal("""
                     The hosted deployment is running with COOKIE_SECURE=false.
                     The refresh token is a 7-day credential and would be sent
                     over plain HTTP.

                     Remove the override; 'true' is the only correct value
                     here. COOKIE_SECURE=false exists for the self-hosted LAN
                     install, which has no certificate to present."""));
        }

        // Not fatal, but the single most likely self-hosted support call: a LAN
        // install over http with a Secure cookie logs the user straight back
        // out, because the browser accepts the Set-Cookie and then never sends
        // it back.
        if (deployment.selfHosted() && cookieSecure) {
            log.warn("Self-hosted mode with cookie-secure=true. If this install is reached over plain "
                    + "http (a LAN address such as http://192.168.1.10), sign-in will appear to succeed "
                    + "and then bounce straight back to the login page - a browser will not return a "
                    + "Secure cookie over http. Set COOKIE_SECURE=false, or put the install behind HTTPS.");
        }

        log.info("""

                ---------------------------------------------------------
                 Hardware ERP - {} deployment{}
                   profiles     : {}
                   database     : {}
                   cookie-secure: {}
                   mfa          : {}
                   billing      : {}
                ---------------------------------------------------------""",
                deployment.mode(),
                deployment.installationName().isBlank() ? "" : " (" + deployment.installationName() + ")",
                profiles.isEmpty() ? "[none]" : String.join(",", profiles),
                describeDatabase(jdbcUrl, managedDb, localDb),
                cookieSecure,
                mfaRequired ? "required" : "*** DISABLED - password is the only factor (CR-060) ***",
                deployment.billingApplies() ? "enabled" : "disabled (not applicable to this deployment)");

        // CR-060. Not a refusal: switching MFA off is a legitimate, deliberate
        // choice while the second factor is being built. But it is a real
        // reduction in account security - a leaked or guessed password is then
        // sufficient on its own - so it is stated separately and loudly rather
        // than left as one quiet word in a banner nobody reads twice.
        if (!mfaRequired) {
            log.warn("MFA IS DISABLED (MFA_REQUIRED=false). A password alone signs in - no second "
                    + "factor is requested or checked. The CR-058 enrollment, TOTP and backup-code "
                    + "implementation is intact and returns as soon as this is true again. Do not "
                    + "leave this off in a production installation holding real shop data.");
        }
    }

    /**
     * Host, port and database name only. A pooled connection string carries the
     * username as part of the URL on some providers, so it is never logged whole.
     */
    private String describeDatabase(String jdbcUrl, boolean managedDb, boolean localDb) {
        String kind = managedDb ? "managed" : localDb ? "local/container" : "unknown";
        int start = jdbcUrl.indexOf("//");
        if (start < 0) {
            return "<unparsed> (" + kind + ")";
        }
        int end = jdbcUrl.indexOf('?');
        String hostAndDb = jdbcUrl.substring(start + 2, end > start ? end : jdbcUrl.length());
        return hostAndDb + " (" + kind + ")";
    }

    private String refusal(String body) {
        return "\n\n=====================================================\n REFUSING TO START\n"
                + body + "\n=====================================================";
    }
}
