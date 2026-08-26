package com.hardware.erp.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/v1/auth/login",
            "/v1/auth/refresh",
            "/v1/auth/forgot-password",
            "/v1/auth/reset-password");

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        authenticate(request, header.substring(BEARER_PREFIX.length()).trim());
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        Optional<Claims> parsed = jwtService.parse(token);
        if (parsed.isEmpty()) {
            return;
        }
        Claims claims = parsed.get();

        Optional<Long> userId = jwtService.userIdFrom(claims);
        Optional<Integer> tokenVersion = jwtService.tokenVersionFrom(claims);
        if (userId.isEmpty() || tokenVersion.isEmpty()) {
            log.debug("JWT missing subject or token version");
            return;
        }

        // One indexed primary-key read per request. It buys immediate
        // revocation: a deactivated user or a changed permission takes effect
        // now, not when the access token expires.
        Optional<AppUserDetails> maybeUser = userDetailsService.loadById(userId.get());
        if (maybeUser.isEmpty()) {
            return;
        }
        AppUserDetails principal = maybeUser.get();

        // The whole point of the tv claim. A field that is issued but never
        // checked is decoration, not security.
        if (!tokenVersion.get().equals(principal.getTokenVersion())) {
            log.debug("Token version mismatch for user {}", principal.getId());
            return;
        }

        if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = SecurityUtils.requestPath(request);
        return PUBLIC_PATHS.contains(path)
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/actuator/health");
    }
}
