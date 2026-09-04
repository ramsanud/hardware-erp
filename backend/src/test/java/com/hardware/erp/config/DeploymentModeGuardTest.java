package com.hardware.erp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CR-059 - the deployment switch's fail-fast half.
 *
 * Every case here is a configuration that would otherwise start successfully
 * and be wrong in a way nobody notices until data is in the wrong place or a
 * credential has crossed plain HTTP.
 *
 * Properties are set on a MockEnvironment rather than through bound beans,
 * because that is exactly how the guard reads them at runtime - it runs as a
 * BeanFactoryPostProcessor, before any @ConfigurationProperties bean exists.
 * See the class javadoc for why it has to run that early.
 */
class DeploymentModeGuardTest {

    private static final String SUPABASE_URL =
            "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require";
    private static final String CONTAINER_URL =
            "jdbc:postgresql://postgres:5432/hardware_erp?sslmode=disable";

    private void run(DeploymentMode mode, boolean cookieSecure, String jdbcUrl, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        environment.setProperty("spring.datasource.url", jdbcUrl);
        environment.setProperty("app.deployment.mode", mode.name());
        environment.setProperty("app.security.cookie-secure", String.valueOf(cookieSecure));

        new DeploymentModeGuard().check(environment);
    }

    @Test
    @DisplayName("refuses when both deployment profiles are active - the winner would depend on ordering")
    void refusesBothProfiles() {
        assertThatThrownBy(() -> run(DeploymentMode.CLOUD, true, SUPABASE_URL, "prod", "cloud", "selfhosted"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFUSING TO START");
    }

    @Test
    @DisplayName("refuses a self-hosted install pointed at the hosted database - one shop's data in another company's system")
    void refusesSelfHostedAgainstManagedDatabase() {
        assertThatThrownBy(() -> run(DeploymentMode.SELF_HOSTED, false, SUPABASE_URL, "prod", "selfhosted"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SELF_HOSTED");
    }

    @Test
    @DisplayName("refuses the hosted deployment pointed at its own container - it would lose every write on redeploy")
    void refusesCloudAgainstContainerDatabase() {
        assertThatThrownBy(() -> run(DeploymentMode.CLOUD, true, CONTAINER_URL, "prod", "cloud"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ephemeral");
    }

    @Test
    @DisplayName("refuses the hosted deployment with an insecure refresh cookie")
    void refusesCloudWithInsecureCookie() {
        assertThatThrownBy(() -> run(DeploymentMode.CLOUD, false, SUPABASE_URL, "prod", "cloud"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COOKIE_SECURE");
    }

    @Test
    @DisplayName("accepts the hosted deployment configured correctly")
    void acceptsValidCloud() {
        assertThatCode(() -> run(DeploymentMode.CLOUD, true, SUPABASE_URL, "prod", "cloud"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("accepts a self-hosted install on http against its own container - the whole point of the mode")
    void acceptsValidSelfHosted() {
        assertThatCode(() -> run(DeploymentMode.SELF_HOSTED, false, CONTAINER_URL, "prod", "selfhosted"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("leaves non-production alone - a developer runs the hosted default against a local container all day")
    void doesNotBlockDevelopment() {
        // The pre-CR-059 default is CLOUD, and every developer machine points
        // at docker-compose.yml. If this guard fired outside 'prod' it would
        // break every existing local setup the moment it was merged.
        assertThatCode(() -> run(DeploymentMode.CLOUD, false, CONTAINER_URL, "dev"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("falls back to the CLOUD defaults when app.deployment is absent entirely")
    void defaultsWhenUnconfigured() {
        // An older deployment's environment predates this property block. It
        // must behave exactly as it did before CR-059, not fail to boot.
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("spring.datasource.url", CONTAINER_URL);

        assertThatCode(() -> new DeploymentModeGuard().check(environment)).doesNotThrowAnyException();
    }
}
