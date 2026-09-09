package com.hardware.erp.platformadmin.security;

import com.hardware.erp.security.JwtService;
import com.hardware.erp.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * CR-065 / BUG-SEC-006. Refresh token transport for the Platform Admin
 * console.
 *
 * Deliberately a sibling of {@link com.hardware.erp.security.RefreshTokenCookieService}
 * rather than a reuse of it, and deliberately NOT a generalisation of it. The
 * two differ in exactly the two values that MUST differ, and hard-coding each
 * here is what makes it impossible to configure them into collision:
 *
 *   - cookie NAME. A platform-admin session and a tenant session can be open
 *     in the same browser at the same time (the frontend keeps two entirely
 *     separate clients precisely so they never share state). One name for both
 *     would mean whichever signed in last silently destroyed the other's
 *     session.
 *
 *   - cookie PATH. Scoped to the platform-admin auth endpoints, so the browser
 *     never attaches a staff credential to a tenant request. A shared /api
 *     scope would ship it to every ERP call any shop makes.
 *
 * Everything else is the tenant service's configuration, unchanged and for the
 * same reasons: HttpOnly so an XSS bug on this origin steals at most a
 * 15-minute access token instead of a 7-day credential, Secure by default, and
 * SameSite=Strict.
 *
 * SameSite=Strict plus the path scope is also what keeps SecurityConfig's
 * existing csrf.disable() honest: the cookie is never attached to a
 * cross-site request, so there is no cross-site form to forge. CSRF was not
 * weakened to make this cookie work - the cookie is configured so the existing
 * posture stays true of it.
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminRefreshTokenCookieService {

    /**
     * Distinct from the tenant cookie's name (erp_refresh_token) - see the
     * class comment. Not configurable, on purpose.
     */
    private static final String COOKIE_NAME = "erp_pa_refresh_token";

    /**
     * The context path is /api, so this is the full browser-visible path of
     * PlatformAdminAuthController's own endpoints and nothing else.
     */
    private static final String COOKIE_PATH = "/api/v1/platform-admin/auth";

    private final SecurityProperties properties;
    private final JwtService jwtService;

    /**
     * Honours the same app.security.refresh-token-transport switch the tenant
     * side does, so a non-browser client can still be served the token in the
     * body. COOKIE is the default.
     */
    public boolean isCookieMode() {
        return properties.refreshTokenTransport()
                == SecurityProperties.RefreshTokenTransport.COOKIE;
    }

    public void write(HttpServletResponse response, String rawRefreshToken) {
        if (!isCookieMode() || rawRefreshToken == null) {
            return;
        }
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofDays(jwtService.refreshTokenDays()))
                .build()
                .toString());
    }

    /**
     * Written with the same name, path and flags as {@link #write} - a cookie
     * is only replaced when all three match, so a clear that differed in any
     * of them would leave the original sitting in the browser.
     */
    public void clear(HttpServletResponse response) {
        if (!isCookieMode()) {
            return;
        }
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build()
                .toString());
    }

    /** Cookie first, then the request body, so both transports work per config. */
    public Optional<String> read(HttpServletRequest request, String bodyToken) {
        if (isCookieMode()) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                Optional<String> fromCookie = Arrays.stream(cookies)
                        .filter(c -> COOKIE_NAME.equals(c.getName()))
                        .map(Cookie::getValue)
                        .filter(v -> v != null && !v.isBlank())
                        .findFirst();
                if (fromCookie.isPresent()) {
                    return fromCookie;
                }
            }
        }
        return Optional.ofNullable(bodyToken).filter(v -> !v.isBlank());
    }

    /** Null in cookie mode, so the token never reaches the response body. */
    public String bodyValue(String rawRefreshToken) {
        return isCookieMode() ? null : rawRefreshToken;
    }
}
