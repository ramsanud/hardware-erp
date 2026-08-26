package com.hardware.erp.security.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.dto.ErrorResponse;
import com.hardware.erp.common.web.RequestCorrelationFilter;
import com.hardware.erp.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies before authentication, because the point is to stop credential
 * stuffing before it ever reaches BCrypt - 250 ms of hashing per attempt is
 * otherwise a denial-of-service lever.
 *
 * Account lockout alone is insufficient: it does nothing against an attacker
 * spraying one password across many accounts.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN = "/v1/auth/login";
    private static final String FORGOT = "/v1/auth/forgot-password";
    private static final String RESET = "/v1/auth/reset-password";
    private static final String REFRESH = "/v1/auth/refresh";
    private static final String REGISTER = "/v1/tenants/register";

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String path = request.getServletPath();
        String ip = SecurityUtils.clientIp(request);

        // LOGIN and FORGOT key a second bucket by the identifier field in the
        // body, which means reading it here. A plain HttpServletRequest's
        // input stream can only be read once, so it is replaced with a
        // request wrapper backed by the buffered bytes - the controller reads
        // an identical, still-unconsumed stream afterwards. RESET and REFRESH
        // never inspect the body, so they pass the original request through
        // untouched.
        boolean needsBody = LOGIN.equals(path) || FORGOT.equals(path);
        HttpServletRequest forwarded = needsBody ? new CachedBodyHttpServletRequest(request) : request;

        List<RateLimitService.Decision> decisions = new ArrayList<>();
        switch (path) {
            case LOGIN -> {
                decisions.add(rateLimitService.check(RateLimitRule.LOGIN_PER_IP, ip));
                identifierOf(forwarded).ifPresent(id -> decisions.add(
                        rateLimitService.check(RateLimitRule.LOGIN_PER_IDENTIFIER, id)));
            }
            case FORGOT -> {
                decisions.add(rateLimitService.check(RateLimitRule.FORGOT_PASSWORD_PER_IP, ip));
                identifierOf(forwarded).ifPresent(id -> decisions.add(
                        rateLimitService.check(RateLimitRule.FORGOT_PASSWORD_PER_IDENTIFIER, id)));
            }
            case RESET -> decisions.add(
                    rateLimitService.check(RateLimitRule.RESET_PASSWORD_PER_IP, ip));
            case REFRESH -> decisions.add(
                    rateLimitService.check(RateLimitRule.REFRESH_PER_IP, ip));
            case REGISTER -> decisions.add(
                    rateLimitService.check(RateLimitRule.REGISTER_PER_IP, ip));
            default -> {
                chain.doFilter(request, response);
                return;
            }
        }

        long retryAfter = decisions.stream()
                .filter(d -> !d.allowed())
                .mapToLong(RateLimitService.Decision::retryAfterSeconds)
                .max()
                .orElse(0);

        if (retryAfter > 0) {
            log.warn("Rate limit exceeded on {} from {}", path, ip);
            reject(request, response, retryAfter);
            return;
        }

        chain.doFilter(forwarded, response);
    }

    /**
     * Reads only the identifier field. A malformed body is not this filter's
     * problem - it falls through to bean validation, which reports it properly.
     */
    private java.util.Optional<String> identifierOf(HttpServletRequest request) {
        try {
            byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
            if (body.length == 0) {
                return java.util.Optional.empty();
            }
            JsonNode node = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode identifier = node.get("identifier");
            if (identifier == null || !identifier.isTextual()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(identifier.asText().trim().toLowerCase());
        } catch (Exception ex) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Buffers the body once so it can be read here and again by the
     * controller. {@code ContentCachingRequestWrapper} does not fit this: it
     * caches bytes as something downstream reads them, it does not replay
     * them, so a filter that reads the body itself before {@code
     * chain.doFilter} leaves nothing for the controller to deserialize.
     */
    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = StreamUtils.copyToByteArray(request.getInputStream());
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return source.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) { }
                @Override public int read() { return source.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please wait before trying again.",
                request.getRequestURI(),
                RequestCorrelationFilter.currentRequestId(request)));
    }
}
