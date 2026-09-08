package com.hardware.erp.platformadmin.security;

import com.hardware.erp.security.JwtService;
import com.hardware.erp.security.SecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * CR-065. The Secure flag cannot be asserted in PlatformAdminRefreshCookieIT:
 * that container serves plain http and the test profile sets
 * cookie-secure=false, so an assertion there would pin the DEV value and say
 * nothing about production. Here the property is set explicitly both ways.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformAdminRefreshTokenCookieServiceTest {

    @Mock private JwtService jwtService;

    private PlatformAdminRefreshTokenCookieService serviceWith(boolean secure,
                                                               SecurityProperties.RefreshTokenTransport transport) {
        when(jwtService.refreshTokenDays()).thenReturn(7L);
        SecurityProperties properties = new SecurityProperties(
                transport, "erp_refresh_token", secure, Boolean.TRUE, List.of());
        return new PlatformAdminRefreshTokenCookieService(properties, jwtService);
    }

    @Test
    @DisplayName("the production configuration marks the cookie Secure")
    void secureInProduction() {
        var response = new MockHttpServletResponse();
        serviceWith(true, SecurityProperties.RefreshTokenTransport.COOKIE)
                .write(response, "raw-token");

        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("Secure");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("SameSite=Strict");
        assertThat(header).contains("Path=/api/v1/platform-admin/auth");
        assertThat(header).startsWith("erp_pa_refresh_token=raw-token");
    }

    @Test
    @DisplayName("only local http development drops Secure, and nothing else about the cookie changes")
    void insecureOnlyForLocalHttp() {
        var response = new MockHttpServletResponse();
        serviceWith(false, SecurityProperties.RefreshTokenTransport.COOKIE)
                .write(response, "raw-token");

        String header = response.getHeader("Set-Cookie");
        assertThat(header).doesNotContain("Secure");
        // The protections that do not depend on the transport being https
        // stay on even in development.
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("SameSite=Strict");
    }

    @Test
    @DisplayName("JSON transport writes no cookie and leaves the token in the body")
    void jsonTransportIsUnaffected() {
        var service = serviceWith(true, SecurityProperties.RefreshTokenTransport.JSON);
        var response = new MockHttpServletResponse();

        service.write(response, "raw-token");

        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(service.bodyValue("raw-token")).isEqualTo("raw-token");
    }

    @Test
    @DisplayName("cookie mode strips the token from the body")
    void cookieModeStripsTheBodyValue() {
        assertThat(serviceWith(true, SecurityProperties.RefreshTokenTransport.COOKIE)
                .bodyValue("raw-token")).isNull();
    }

    @Test
    @DisplayName("clear() matches write() on name, path and flags, or the browser keeps the original")
    void clearMatchesWrite() {
        var service = serviceWith(true, SecurityProperties.RefreshTokenTransport.COOKIE);

        var written = new MockHttpServletResponse();
        service.write(written, "raw-token");
        var cleared = new MockHttpServletResponse();
        service.clear(cleared);

        Cookie a = written.getCookie("erp_pa_refresh_token");
        Cookie b = cleared.getCookie("erp_pa_refresh_token");
        assertThat(b).isNotNull();
        assertThat(b.getPath()).isEqualTo(a.getPath());
        assertThat(b.isHttpOnly()).isEqualTo(a.isHttpOnly());
        assertThat(b.getSecure()).isEqualTo(a.getSecure());
        assertThat(b.getMaxAge()).isZero();
    }

    @Test
    @DisplayName("the cookie is preferred over a body token, and the body still works when there is no cookie")
    void readPrefersTheCookie() {
        var service = serviceWith(true, SecurityProperties.RefreshTokenTransport.COOKIE);

        var withCookie = new MockHttpServletRequest();
        withCookie.setCookies(new Cookie("erp_pa_refresh_token", "from-cookie"));
        assertThat(service.read(withCookie, "from-body")).contains("from-cookie");

        assertThat(service.read(new MockHttpServletRequest(), "from-body")).contains("from-body");
        assertThat(service.read(new MockHttpServletRequest(), null)).isEmpty();
        assertThat(service.read(new MockHttpServletRequest(), "  ")).isEmpty();
    }
}
