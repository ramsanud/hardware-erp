package com.hardware.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Optional;

/**
 * CR-096 - the in-process keep-alive for a Render free-tier deployment.
 *
 * A free Render web service sleeps after 15 minutes with no inbound request
 * and takes a 30-60s cold start to come back, which is what makes the app
 * look broken in front of a client. Render injects {@code RENDER_EXTERNAL_URL}
 * (the service's own public https origin) into every web service it runs,
 * so on Render this configures itself with nothing to set; anywhere else
 * the base URL is blank and the task is never constructed.
 *
 * <p>Deliberately not a replacement for the external pingers in
 * docs/DEPLOYMENT.md section 4. A scheduler inside the container sleeps with
 * the container: this keeps an awake service awake, and only an outside
 * request can wake a sleeping one. Both together cost nothing.
 *
 * <p>The only accepted target is an {@code https} origin from configuration,
 * with the health path appended here rather than read from anywhere - the
 * task never fetches a URL that a request, a tenant row or a plain-http
 * setting could have shaped.
 */
@ConfigurationProperties(prefix = "app.keep-alive")
public record KeepAliveProperties(
        Boolean enabled,
        String baseUrl,
        /** Milliseconds between pings. Render's idle window is 15 minutes; 10 leaves one missed tick of slack. */
        Long intervalMs
) {

    /** Same path SecurityConfig leaves public and render.yaml probes; nothing else is ever pinged. */
    public static final String HEALTH_PATH = "/api/actuator/health";

    public KeepAliveProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (baseUrl == null) {
            baseUrl = "";
        }
        if (intervalMs == null) {
            intervalMs = 600_000L;
        }
    }

    /**
     * The health URL to ping, or empty when the task should not run: switched
     * off, no base URL (every non-Render environment), or a base URL that is
     * not a plain https origin. A malformed or http value is treated as
     * "not configured" rather than pinged, so a typo cannot turn this into a
     * request to somewhere unintended.
     */
    public Optional<URI> healthUrl() {
        if (!enabled || baseUrl.isBlank()) {
            return Optional.empty();
        }
        URI origin;
        try {
            origin = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!"https".equalsIgnoreCase(origin.getScheme()) || origin.getHost() == null
                || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null) {
            return Optional.empty();
        }
        String path = origin.getPath() == null ? "" : origin.getPath().replaceAll("/+$", "");
        if (!path.isEmpty()) {
            // RENDER_EXTERNAL_URL is a bare origin. A path means someone pasted
            // the health URL itself or something stranger; refuse rather than
            // guess where the health endpoint sits under it.
            return Optional.empty();
        }
        return Optional.of(URI.create(
                "https://" + origin.getRawAuthority() + HEALTH_PATH));
    }
}
