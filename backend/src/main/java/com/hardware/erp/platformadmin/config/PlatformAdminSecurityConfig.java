package com.hardware.erp.platformadmin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.platformadmin.security.PlatformAdminAuthenticationFilter;
import com.hardware.erp.platformadmin.security.PlatformAdminJwtService;
import com.hardware.erp.platformadmin.security.PlatformAdminRateLimitFilter;
import com.hardware.erp.platformadmin.security.PlatformAdminUserDetailsService;
import com.hardware.erp.security.JwtAuthEntryPoint;
import com.hardware.erp.security.RestAccessDeniedHandler;
import com.hardware.erp.security.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * A completely separate filter chain from SecurityConfig, matched only on
 * /v1/platform-admin/** via securityMatcher. Ordered ahead of the tenant
 * chain (@Order(0) vs @Order(1) there) so Spring Security picks this one for
 * a platform-admin request instead of falling through to the tenant chain's
 * anyRequest().authenticated(), which would otherwise also match and would
 * authenticate with the wrong JwtService entirely.
 *
 * Deliberately duplicates SecurityConfig's header/CORS/session hardening
 * rather than sharing a HttpSecurity customizer with it - the two chains
 * must be able to diverge in the future (e.g. platform admin someday
 * requiring a stricter CSP or a different CORS origin list for a separate
 * admin subdomain) without one edit silently changing the other's behavior.
 */
@Configuration
@RequiredArgsConstructor
public class PlatformAdminSecurityConfig {

    private final PlatformAdminJwtService platformAdminJwtService;
    private final PlatformAdminUserDetailsService platformAdminUserDetailsService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final JwtAuthEntryPoint authEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public PlatformAdminAuthenticationFilter platformAdminAuthenticationFilter() {
        return new PlatformAdminAuthenticationFilter(platformAdminJwtService, platformAdminUserDetailsService);
    }

    @Bean
    public PlatformAdminRateLimitFilter platformAdminRateLimitFilter() {
        return new PlatformAdminRateLimitFilter(rateLimitService, objectMapper);
    }

    @Bean
    @Order(0)
    public SecurityFilterChain platformAdminFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/v1/platform-admin/**")
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh
                    .authenticationEntryPoint(authEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            .headers(headers -> headers
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(Customizer.withDefaults())
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31_536_000))
                    .referrerPolicy(referrer -> referrer.policy(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; frame-ancestors 'none'; base-uri 'self'; "
                            + "form-action 'self'; object-src 'none'"))
                    .permissionsPolicyHeader(pp -> pp.policy(
                            "camera=(), microphone=(), geolocation=(), payment=()")))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(
                            "/v1/platform-admin/auth/login",
                            "/v1/platform-admin/auth/mfa/verify",
                            "/v1/platform-admin/auth/mfa/enroll",
                            "/v1/platform-admin/auth/mfa/enroll/confirm",
                            "/v1/platform-admin/auth/refresh").permitAll()
                    .anyRequest().authenticated())
            .addFilterBefore(platformAdminRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(platformAdminAuthenticationFilter(), PlatformAdminRateLimitFilter.class);

        return http.build();
    }
}
