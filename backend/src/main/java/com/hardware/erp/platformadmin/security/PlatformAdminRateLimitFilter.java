package com.hardware.erp.platformadmin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.dto.ErrorResponse;
import com.hardware.erp.common.web.RequestCorrelationFilter;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.security.ratelimit.RateLimitRule;
import com.hardware.erp.security.ratelimit.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Dedicated, not a change to the tenant-facing RateLimitFilter (see that
 * class's javadoc: it is deliberately hardcoded to tenant paths). Per-IP
 * only, unlike the tenant login limiter's extra per-identifier bucket - the
 * platform admin roster is small and every account already has its own
 * lockout (PlatformAdmin.registerFailedLogin), so a per-IP ceiling is enough
 * to stop credential stuffing without the request-body-buffering complexity
 * a per-identifier bucket would need here too.
 */
@Slf4j
@RequiredArgsConstructor
public class PlatformAdminRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN = "/v1/platform-admin/auth/login";

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String path = SecurityUtils.requestPath(request);
        if (!LOGIN.equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitService.Decision decision = rateLimitService.check(
                RateLimitRule.PLATFORM_ADMIN_LOGIN_PER_IP, SecurityUtils.clientIp(request));

        if (!decision.allowed()) {
            log.warn("Platform admin login rate limit exceeded from {}", SecurityUtils.clientIp(request));
            reject(request, response, decision.retryAfterSeconds());
            return;
        }

        chain.doFilter(request, response);
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
