package com.hardware.erp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CR-096. The task only ever fetches a URL this record built from a
 * configured https origin plus the fixed health path. These pin that rule:
 * anything that is not exactly that shape means "do not ping", never "ping
 * something close".
 */
class KeepAlivePropertiesTest {

    private static KeepAliveProperties with(String baseUrl) {
        return new KeepAliveProperties(null, baseUrl, null);
    }

    @Test
    @DisplayName("Render's RENDER_EXTERNAL_URL origin becomes the health URL")
    void renderOriginBecomesHealthUrl() {
        assertThat(with("https://hardware-erp-9j9f.onrender.com").healthUrl())
                .contains(URI.create("https://hardware-erp-9j9f.onrender.com/api/actuator/health"));
    }

    @Test
    @DisplayName("a trailing slash and surrounding whitespace are tolerated")
    void trailingSlashAndWhitespaceTolerated() {
        assertThat(with("  https://hardware-erp-9j9f.onrender.com/  ").healthUrl())
                .contains(URI.create("https://hardware-erp-9j9f.onrender.com/api/actuator/health"));
    }

    @Test
    @DisplayName("defaults: enabled, blank base URL, ten-minute interval - so nothing runs off Render")
    void defaultsAreOffEverywhereButRender() {
        KeepAliveProperties defaults = new KeepAliveProperties(null, null, null);

        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.baseUrl()).isEmpty();
        assertThat(defaults.intervalMs()).isEqualTo(600_000L);
        assertThat(defaults.healthUrl()).isEmpty();
    }

    @Test
    @DisplayName("KEEP_ALIVE_ENABLED=false wins over a configured origin")
    void disabledWinsOverOrigin() {
        assertThat(new KeepAliveProperties(false, "https://hardware-erp-9j9f.onrender.com", null).healthUrl())
                .isEmpty();
    }

    @ParameterizedTest(name = "refused: {0}")
    @ValueSource(strings = {
            "http://hardware-erp-9j9f.onrender.com",               // plain http - never
            "https://hardware-erp-9j9f.onrender.com/api/actuator/health", // the full URL pasted - a path is refused, not guessed around
            "https://hardware-erp-9j9f.onrender.com/api",
            "https://user:pw@hardware-erp-9j9f.onrender.com",      // credentials in the origin
            "https://hardware-erp-9j9f.onrender.com?x=1",
            "https://hardware-erp-9j9f.onrender.com#frag",
            "hardware-erp-9j9f.onrender.com",                      // no scheme
            "file:///etc/passwd",
            "https://",
            "not a url at all",
            "   "
    })
    @DisplayName("anything that is not a bare https origin is treated as not configured")
    void onlyABareHttpsOriginIsAccepted(String baseUrl) {
        assertThat(with(baseUrl).healthUrl()).isEmpty();
    }
}
