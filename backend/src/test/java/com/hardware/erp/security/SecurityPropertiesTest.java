package com.hardware.erp.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding tests for app.security.
 *
 * These exist because of a real production failure on 2026-09-05: a Render
 * deployment had an environment variable present but blank, Spring's
 * ${VAR:default} therefore resolved to the empty string rather than the
 * default, and the application died at startup with
 *
 *   Could not bind properties to 'SecurityProperties' : prefix=app.security
 *
 * naming neither the key nor the reason. Every field is now a wrapper type
 * with a default, so a blank value is treated as unset.
 */
class SecurityPropertiesTest {

    private SecurityProperties bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("app.security", SecurityProperties.class)
                .orElseGet(() -> new SecurityProperties(null, null, null, null, null));
    }

    /** What an env file line like "MFA_REQUIRED=" or a blank dashboard box produces. */
    private Map<String, Object> allBlank() {
        Map<String, Object> map = new HashMap<>();
        map.put("app.security.refresh-token-transport", "");
        map.put("app.security.cookie-name", "");
        map.put("app.security.cookie-secure", "");
        map.put("app.security.mfa-required", "");
        map.put("app.security.allowed-origins", "");
        return map;
    }

    @Test
    @DisplayName("a blank value for every key binds instead of killing the application")
    void blankValuesBind() {
        SecurityProperties properties = bind(allBlank());

        assertThat(properties).isNotNull();
        assertThat(properties.refreshTokenTransport())
                .isEqualTo(SecurityProperties.RefreshTokenTransport.COOKIE);
        assertThat(properties.cookieName()).isEqualTo("erp_refresh_token");
        assertThat(properties.allowedOrigins()).isEmpty();
    }

    @Test
    @DisplayName("a blank MFA_REQUIRED means MFA stays ON - a blank value must never weaken security")
    void blankMfaRequiredDefaultsToOn() {
        // The failure this guards against is subtle: had the default gone the
        // other way, a typo in a dashboard would silently disable the second
        // factor on a live deployment and nothing would say so.
        assertThat(bind(allBlank()).mfaRequired()).isTrue();
    }

    @Test
    @DisplayName("a blank COOKIE_SECURE means the refresh cookie stays Secure")
    void blankCookieSecureDefaultsToSecure() {
        assertThat(bind(allBlank()).cookieSecure()).isTrue();
    }

    @Test
    @DisplayName("an absent app.security block still yields usable defaults")
    void absentBlockDefaults() {
        SecurityProperties properties = bind(new HashMap<>());

        assertThat(properties.cookieSecure()).isTrue();
        assertThat(properties.mfaRequired()).isTrue();
        assertThat(properties.refreshTokenTransport())
                .isEqualTo(SecurityProperties.RefreshTokenTransport.COOKIE);
    }

    @Test
    @DisplayName("real values still win - the defaults only fill genuine gaps")
    void explicitValuesAreHonoured() {
        Map<String, Object> map = new HashMap<>();
        map.put("app.security.refresh-token-transport", "JSON");
        map.put("app.security.cookie-name", "custom_token");
        map.put("app.security.cookie-secure", "false");
        map.put("app.security.mfa-required", "false");
        map.put("app.security.allowed-origins", "https://a.example,https://b.example");

        SecurityProperties properties = bind(map);

        assertThat(properties.refreshTokenTransport())
                .isEqualTo(SecurityProperties.RefreshTokenTransport.JSON);
        assertThat(properties.cookieName()).isEqualTo("custom_token");
        assertThat(properties.cookieSecure()).isFalse();
        assertThat(properties.mfaRequired()).isFalse();
        assertThat(properties.allowedOrigins()).containsExactly("https://a.example", "https://b.example");
    }
}
