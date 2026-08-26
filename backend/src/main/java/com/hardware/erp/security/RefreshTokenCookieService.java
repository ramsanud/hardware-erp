package com.hardware.erp.security;

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
 * Refresh token transport.
 *
 * Default is an HttpOnly, Secure, SameSite=Strict cookie scoped to
 * /api/v1/auth. JavaScript cannot read it, so an XSS bug steals at most a
 * 15-minute access token instead of a 7-day credential. SameSite=Strict plus
 * the path scope removes the CSRF exposure that normally comes with cookie
 * auth, which is why CSRF tokens are not required for the JSON API.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenCookieService {

    private static final String COOKIE_PATH = "/api/v1/auth";

    private final SecurityProperties properties;
    private final JwtService jwtService;

    public boolean isCookieMode() {
        return properties.refreshTokenTransport()
                == SecurityProperties.RefreshTokenTransport.COOKIE;
    }

    public void write(HttpServletResponse response, String rawRefreshToken) {
        if (!isCookieMode()) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(properties.cookieName(), rawRefreshToken)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofDays(jwtService.refreshTokenDays()))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        if (!isCookieMode()) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(properties.cookieName(), "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /** Cookie first, then the request body, so both transports work per config. */
    public Optional<String> read(HttpServletRequest request, String bodyToken) {
        if (isCookieMode()) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                Optional<String> fromCookie = Arrays.stream(cookies)
                        .filter(c -> properties.cookieName().equals(c.getName()))
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

    /** Null in cookie mode so the token never reaches the response body. */
    public String bodyValue(String rawRefreshToken) {
        return isCookieMode() ? null : rawRefreshToken;
    }
}
