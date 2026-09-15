package com.hardware.erp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CR-096. Drives the task through the package-private HttpClient seam, the
 * same way ResendEmailProviderTest does, so no network is involved.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RenderKeepAliveTaskTest {

    private static final String ORIGIN = "https://hardware-erp-9j9f.onrender.com";

    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<Void> response;

    private RenderKeepAliveTask taskFor(String baseUrl) {
        return new RenderKeepAliveTask(new KeepAliveProperties(true, baseUrl, null), httpClient);
    }

    @Test
    @DisplayName("pings exactly the configured origin's health path, as a bounded GET")
    void pingsTheHealthPathWithATimeout() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(httpClient.<Void>send(any(), any())).thenReturn(response);

        taskFor(ORIGIN).keepAlive();

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(sent.capture(), any());
        HttpRequest request = sent.getValue();
        assertThat(request.uri()).isEqualTo(URI.create(ORIGIN + "/api/actuator/health"));
        assertThat(request.method()).isEqualTo("GET");
        // A hung ping would hold the shared scheduler thread; the request
        // must carry its own deadline, not rely on the socket giving up.
        assertThat(request.timeout()).contains(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("a non-200 answer is logged and swallowed - the next tick is the retry")
    void nonOkIsSwallowed() throws Exception {
        when(response.statusCode()).thenReturn(503);
        when(httpClient.<Void>send(any(), any())).thenReturn(response);

        assertThatCode(() -> taskFor(ORIGIN).keepAlive()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a network failure is logged and swallowed, never propagated to the scheduler")
    void ioFailureIsSwallowed() throws Exception {
        when(httpClient.<Void>send(any(), any())).thenThrow(new IOException("connection refused"));

        assertThatCode(() -> taskFor(ORIGIN).keepAlive()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an interrupted ping restores the interrupt flag")
    void interruptIsRestored() throws Exception {
        when(httpClient.<Void>send(any(), any())).thenThrow(new InterruptedException());

        taskFor(ORIGIN).keepAlive();

        assertThat(Thread.interrupted()).as("interrupt flag was set again").isTrue();
    }

    @Test
    @DisplayName("an http origin is refused at construction: no request is ever made")
    void plainHttpOriginNeverPings() throws Exception {
        taskFor("http://hardware-erp-9j9f.onrender.com").keepAlive();

        verify(httpClient, never()).send(any(), any());
    }
}
