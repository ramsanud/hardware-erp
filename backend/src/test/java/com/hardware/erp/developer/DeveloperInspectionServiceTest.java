package com.hardware.erp.developer;

import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.developer.dto.RequestEchoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The environment half of the CR-045 gate. No Spring context: the whole point
 * of these assertions is that the decision is made from two plain inputs and
 * cannot be talked out of by configuration.
 */
class DeveloperInspectionServiceTest {

    private DeveloperInspectionService serviceFor(boolean enabled, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new DeveloperInspectionService(
                new DeveloperInspectionProperties(enabled), environment);
    }

    @Test
    @DisplayName("a non-production environment that opted in permits inspection")
    void enabledOutsideProduction() {
        assertThat(serviceFor(true, "local").environmentAllows()).isTrue();
        assertThat(serviceFor(true, "dev").environmentAllows()).isTrue();
        assertThat(serviceFor(true, "test").environmentAllows()).isTrue();
    }

    @Test
    @DisplayName("an environment that did not opt in refuses, whatever the profile")
    void disabledWhenNotOptedIn() {
        assertThat(serviceFor(false, "local").environmentAllows()).isFalse();
        assertThat(serviceFor(false, "dev").environmentAllows()).isFalse();
    }

    /**
     * The assertion this class exists for. application-prod.yml already sets a
     * hard false; this proves the code refuses even when something has handed
     * it a true - a mis-ordered property source, an env var, or a future edit
     * to that file.
     */
    @Test
    @DisplayName("production refuses inspection even when the property says enabled")
    void productionOverridesTheProperty() {
        assertThat(serviceFor(true, "prod").environmentAllows()).isFalse();
    }

    @Test
    @DisplayName("production still refuses when it is one profile among several")
    void productionWinsAmongMultipleProfiles() {
        assertThat(serviceFor(true, "prod", "metrics").environmentAllows()).isFalse();
    }

    @Test
    @DisplayName("diagnostics answer 404, not 403, where inspection is off")
    void diagnosticsAreAbsentNotForbidden() {
        DeveloperInspectionService service = serviceFor(true, "prod");

        assertThatThrownBy(service::runtimeDiagnostics)
                .isInstanceOf(BusinessException.class)
                .as("403 would confirm the route exists and is worth attacking")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("runtime diagnostics report named fields only, never a configuration dump")
    void runtimeDiagnosticsAreNamedFields() {
        var diagnostics = serviceFor(true, "local").runtimeDiagnostics();

        assertThat(diagnostics.activeProfiles()).containsExactly("local");
        assertThat(diagnostics.javaVersion()).isNotBlank();
        assertThat(diagnostics.heapMaxMb()).isPositive();
        assertThat(diagnostics.uptimeSeconds()).isNotNegative();
    }

    /**
     * Removed, not masked: a masked value still confirms the header was
     * present and how long it was, and no diagnostic question needs either.
     */
    @Test
    @DisplayName("request echo drops every credential-bearing header")
    void requestEchoStripsCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/dev/inspection/request-echo");
        request.addHeader("Authorization", "Bearer a.real.token");
        request.addHeader("Cookie", "erp_refresh_token=a-real-refresh-token");
        request.addHeader("X-Api-Key", "a-real-api-key");
        request.addHeader("X-Auth-Token", "another-real-token");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("X-Request-ID", "req-1234");

        RequestEchoResponse echo = serviceFor(true, "local").requestEcho(request);

        assertThat(echo.headers()).containsKeys("User-Agent", "X-Request-ID");
        assertThat(echo.headers().keySet())
                .extracting(String::toLowerCase)
                .doesNotContain("authorization", "cookie", "x-api-key", "x-auth-token");
        assertThat(echo.headers().values())
                .noneMatch(value -> value.contains("real"));
    }

    @Test
    @DisplayName("an unauthenticated caller holds no developer permission")
    void anonymousHoldsNoPermission() {
        assertThat(serviceFor(true, "local").callerHoldsPermission()).isFalse();
    }
}
