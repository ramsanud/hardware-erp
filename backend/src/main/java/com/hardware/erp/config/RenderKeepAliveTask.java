package com.hardware.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * CR-096 - pings this deployment's own public health endpoint so a Render
 * free-tier instance does not go idle. See {@link KeepAliveProperties} for
 * why this exists and why it is only half of the answer.
 *
 * <p>Constructed only when a base URL is configured (on Render that is the
 * injected RENDER_EXTERNAL_URL; nowhere else sets one), so dev, test and a
 * self-hosted install never carry a ticking task. The scheduler thread is
 * shared with {@code TokenCleanupJob}, so every call here is bounded: a
 * connect timeout, a request timeout, and nothing retried - the next tick is
 * ten minutes away and is the retry.
 *
 * <p>Failures are logged and swallowed on purpose. The first tick after a
 * deploy can run before Render's edge routes to the new instance, and a
 * failed self-ping is not a reason to alarm anyone - the external monitors
 * are what report an outage. A success is logged at DEBUG only; a line
 * every ten minutes forever is not information.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.keep-alive.base-url:}' != '' && ${app.keep-alive.enabled:true}")
public class RenderKeepAliveTask {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final URI healthUrl;

    @Autowired
    public RenderKeepAliveTask(KeepAliveProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    /** Package-private seam so the tests drive the real request handling without a network. */
    RenderKeepAliveTask(KeepAliveProperties properties, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.healthUrl = properties.healthUrl().orElse(null);
        if (healthUrl == null) {
            log.warn("Keep-alive is switched on but app.keep-alive.base-url '{}' is not a plain https origin "
                    + "(expected the form https://xxxxxx.onrender.com). No self-ping will be sent.",
                    properties.baseUrl());
        } else {
            log.info("Keep-alive self-ping active: GET {} every {}s (CR-096). This keeps an awake instance "
                    + "awake; an external monitor is still needed to wake a sleeping one.",
                    healthUrl, properties.intervalMs() / 1000);
        }
    }

    /**
     * fixedDelay, not fixedRate: the next tick is measured from the end of the
     * previous one, so a slow response can never queue overlapping pings.
     * The initial delay gives Tomcat and the health indicators time to come
     * up; on a free instance a cold start alone is 30-60s.
     */
    @Scheduled(fixedDelayString = "${app.keep-alive.interval-ms:600000}", initialDelayString = "120000")
    public void keepAlive() {
        if (healthUrl == null) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(healthUrl)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "hardware-erp-keepalive")
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                log.debug("Keep-alive ping to {} answered 200", healthUrl);
            } else {
                log.warn("Keep-alive ping to {} answered HTTP {}", healthUrl, response.statusCode());
            }
        } catch (IOException e) {
            log.warn("Keep-alive ping to {} failed: {}", healthUrl, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
