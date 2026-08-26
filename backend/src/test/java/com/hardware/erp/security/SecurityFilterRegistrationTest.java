package com.hardware.erp.security;

import com.hardware.erp.security.ratelimit.RateLimitFilter;
import com.hardware.erp.support.AbstractIntegrationTest;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for BUG-AUTH-008.
 *
 * Both filters were annotated @Component, which makes Spring Boot register them
 * in the servlet filter chain in addition to the Security chain. Each then ran
 * twice per request: the rate limiter consumed two tokens per call, silently
 * halving every configured limit, and the JWT filter doubled the per-request
 * database read.
 */
class SecurityFilterRegistrationTest extends AbstractIntegrationTest {

    @Autowired private FilterChainProxy filterChainProxy;
    @Autowired private org.springframework.context.ApplicationContext context;

    private List<Filter> securityChainFilters() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");
        request.setServletPath("/v1/auth/login");
        return filterChainProxy.getFilters(request.getServletPath());
    }

    @Test
    @DisplayName("JwtAuthenticationFilter appears exactly once in the security chain")
    void jwtFilterRegisteredOnce() {
        long count = securityChainFilters().stream()
                .filter(JwtAuthenticationFilter.class::isInstance)
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("RateLimitFilter appears exactly once in the security chain")
    void rateLimitFilterRegisteredOnce() {
        long count = securityChainFilters().stream()
                .filter(RateLimitFilter.class::isInstance)
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("rate limiting runs before authentication, so BCrypt is never the DoS lever")
    void rateLimitRunsBeforeJwt() {
        List<Filter> filters = securityChainFilters();
        int rateLimitIndex = indexOf(filters, RateLimitFilter.class);
        int jwtIndex = indexOf(filters, JwtAuthenticationFilter.class);

        assertThat(rateLimitIndex).isGreaterThanOrEqualTo(0);
        assertThat(jwtIndex).isGreaterThan(rateLimitIndex);
    }

    @Test
    @DisplayName("neither filter is auto-registered as a servlet filter bean")
    void filtersAreNotComponentScanned() {
        // If either were @Component, Boot would also register it globally.
        // FilterRegistrationBean entries would exist for them.
        assertThat(context.getBeanNamesForType(
                org.springframework.boot.web.servlet.FilterRegistrationBean.class))
                .noneMatch(name -> name.toLowerCase().contains("jwtauthenticationfilter")
                                || name.toLowerCase().contains("ratelimitfilter"));
    }

    @Test
    @DisplayName("RequestCorrelationFilter is global on purpose, covering swagger and actuator")
    void correlationFilterIsGlobal() {
        assertThat(context.getBeansOfType(
                com.hardware.erp.common.web.RequestCorrelationFilter.class))
                .hasSize(1);
    }

    private int indexOf(List<Filter> filters, Class<?> type) {
        for (int i = 0; i < filters.size(); i++) {
            if (type.isInstance(filters.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
