package com.hardware.erp.security;

import com.hardware.erp.common.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private static final int MAX_USER_AGENT = 255;

    private SecurityUtils() {
    }

    public static Optional<AppUserDetails> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AppUserDetails details) {
            return Optional.of(details);
        }
        return Optional.empty();
    }

    /**
     * The only sanctioned way to answer "who is calling". Endpoints that act on
     * the current user - PUT /auth/me, change-password - must use this and
     * never a user id taken from the request body or path.
     */
    public static AppUserDetails requireCurrentUser() {
        return currentUser().orElseThrow(
                () -> new AuthException("Not authenticated", "UNAUTHENTICATED"));
    }

    public static Optional<Long> currentUserId() {
        return currentUser().map(AppUserDetails::getId);
    }

    public static Optional<Long> currentTenantId() {
        return currentUser().map(AppUserDetails::getTenantId);
    }

    /**
     * The one sanctioned way to scope a tenant-owned query. Every repository
     * call that reads or writes app_user, role or supplier rows must use
     * this, never a tenant id taken from a request parameter or path
     * variable (CR-016) - a client-supplied tenant id is exactly the kind of
     * input that would let one shop reach another shop's data.
     */
    public static Long requireCurrentTenantId() {
        return currentTenantId().orElseThrow(
                () -> new AuthException("Not authenticated", "UNAUTHENTICATED"));
    }

    /**
     * The path a security rule should match on: the request URI with the
     * servlet context path removed.
     *
     * Deliberately not {@code getServletPath()}. Under a real container with
     * {@code server.servlet.context-path: /api} that returns the right thing,
     * but MockMvc leaves it empty, so a filter keyed on it silently matches
     * nothing in every integration test. That is how RateLimitIT could assert
     * 429 and receive 401 while production throttled correctly - the security
     * control worked and its test could never reach it (BUG-SEC-003).
     *
     * Subtracting the context path from the URI gives the same answer in both,
     * and does not depend on how the DispatcherServlet happens to be mapped.
     */
    public static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /**
     * X-Forwarded-For is honoured because the app sits behind Nginx. Only the
     * first hop is taken; the remainder is client-controllable and is discarded.
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (first.length() <= 45) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    /** Truncated to the column width so an oversized header cannot fail an insert. */
    public static String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_USER_AGENT ? value : value.substring(0, MAX_USER_AGENT);
    }
}
