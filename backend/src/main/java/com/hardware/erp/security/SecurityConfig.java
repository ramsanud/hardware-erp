package com.hardware.erp.security;

import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.developer.DeveloperInspectionService;
import com.hardware.erp.security.ratelimit.RateLimitFilter;
import com.hardware.erp.security.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final AppUserDetailsService appUserDetailsService;
    private final RateLimitService rateLimitService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final JwtAuthEntryPoint authEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final SecurityProperties securityProperties;
    private final DeveloperInspectionService developerInspectionService;

    /**
     * Strength 12: roughly 250 ms per hash on modest hardware. Deliberately
     * slow - that cost is what makes an offline dictionary attack impractical.
     * It is also why the rate limit filter runs before authentication.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Declared as plain beans, NOT @Component.
     *
     * Spring Boot auto-registers any OncePerRequestFilter bean that is a
     * @Component into the servlet filter chain for every request. It would then
     * run once there and again inside the Security chain - twice per request,
     * including on paths Security excludes. See BUG-AUTH-008.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService, appUserDetailsService);
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(rateLimitService, objectMapper);
    }

    /**
     * @Order(1): PlatformAdminSecurityConfig's chain is @Order(0) and
     * securityMatcher-scoped to /v1/platform-admin/**, so it is evaluated
     * first for that prefix. This chain's anyRequest().authenticated() would
     * otherwise also match a platform-admin path and authenticate it with
     * the tenant JwtService - wrong signing key, wrong principal type.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless bearer API: no cookie-backed session for a forged form
            // post to ride on. The one cookie in play, the refresh token, is
            // SameSite=Strict and path-scoped to /api/v1/auth.
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
            .authorizeHttpRequests(auth -> {
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(
                            "/v1/auth/login",
                            // The sign-in page must learn whether to render a
                            // challenge before anyone has signed in.
                            "/v1/auth/captcha-config",
                            "/v1/auth/refresh",
                            "/v1/auth/forgot-password",
                            "/v1/auth/reset-password",
                            "/v1/tenants/register",
                            "/v1/tenants/register/slug-available",
                            // Meta calls this with no JWT of ours - authenticity is
                            // enforced inside WhatsAppWebhookController itself (the
                            // GET handshake's hub.verify_token, the POST's
                            // X-Hub-Signature-256 HMAC), not by Spring Security.
                            "/v1/webhooks/whatsapp").permitAll()
                    // /actuator/health is the hosting platform's liveness probe
                    // and must answer before anyone signs in. The API browser is
                    // public only where it is served at all - application-prod.yml
                    // disables springdoc outright, so in production these two
                    // patterns match nothing.
                    .requestMatchers(
                            "/swagger-ui/**", "/swagger-ui.html",
                            "/v3/api-docs/**", "/actuator/health").permitAll();

                // Developer diagnostics: the ENVIRONMENT half of the CR-045
                // gate, decided here rather than inside each endpoint so that
                // a future /v1/dev or /v1/debug controller added by someone
                // who has not read CR-045 is still covered by default.
                //
                // Evaluated once at startup, which is correct: both inputs -
                // the active profiles and app.developer-inspection.enabled -
                // are fixed for the life of the process.
                if (developerInspectionService.environmentAllows()) {
                    // Permission is the other half, enforced per endpoint by
                    // @PreAuthorize. Authentication is all that is required
                    // here so that /v1/dev/inspection/status can report WHICH
                    // gate closed.
                    auth.requestMatchers("/v1/dev/**", "/v1/debug/**").authenticated();
                    // Actuator beyond health is developer tooling too. env,
                    // configprops and beans in particular would disclose the
                    // datasource password and the JWT secret.
                    auth.requestMatchers("/actuator/**")
                            .hasAuthority(PermissionCode.DEVELOPER_INSPECT);
                } else {
                    // Production, and any environment that has not opted in.
                    // No permission grant and no annotation mistake can open
                    // these, because nothing reaches a controller at all.
                    auth.requestMatchers("/v1/dev/**", "/v1/debug/**").denyAll();
                    auth.requestMatchers("/actuator/**").denyAll();
                }

                auth.anyRequest().authenticated();
            })
            // Rate limit first: stop credential stuffing before it reaches
            // BCrypt, where 250ms per attempt becomes a DoS lever.
            .addFilterBefore(rateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtAuthenticationFilter(), RateLimitFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(securityProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Requested-With", "X-Request-ID"));
        config.setExposedHeaders(List.of("Content-Disposition", "X-Request-ID", "Retry-After"));
        // Required for the refresh cookie to travel cross-origin in development.
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
