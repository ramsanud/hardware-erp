package com.hardware.erp.platformadmin.security;

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

/**
 * Mirrors security.JwtAuthenticationFilter. Only ever wired into
 * PlatformAdminSecurityConfig's chain, which is securityMatcher-scoped to
 * /v1/platform-admin/**, so it never even sees a tenant-facing request.
 *
 * A token bearing an MFA-challenge purpose claim is rejected here (see
 * PlatformAdminJwtService.purposeFrom) - it proves a password check passed,
 * not that a session may be created, and the /mfa/* endpoints validate it
 * themselves from the request body instead of the Authorization header.
 */
@Slf4j
@RequiredArgsConstructor
public class PlatformAdminAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/v1/platform-admin/auth/login",
            "/v1/platform-admin/auth/mfa/verify",
            "/v1/platform-admin/auth/mfa/enroll",
            "/v1/platform-admin/auth/mfa/enroll/confirm",
            "/v1/platform-admin/auth/refresh");

    private final PlatformAdminJwtService jwtService;
    private final PlatformAdminUserDetailsService userDetailsService;

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

        if (jwtService.purposeFrom(claims).isPresent()) {
            log.debug("Rejected an MFA-challenge token presented as a session token");
            return;
        }

        Optional<Long> adminId = jwtService.adminIdFrom(claims);
        Optional<Integer> tokenVersion = jwtService.tokenVersionFrom(claims);
        if (adminId.isEmpty() || tokenVersion.isEmpty()) {
            return;
        }

        Optional<PlatformAdminPrincipal> maybeAdmin = userDetailsService.loadById(adminId.get());
        if (maybeAdmin.isEmpty()) {
            return;
        }
        PlatformAdminPrincipal principal = maybeAdmin.get();

        if (!tokenVersion.get().equals(principal.getTokenVersion())) {
            log.debug("Token version mismatch for platform admin {}", principal.getId());
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
        String path = com.hardware.erp.security.SecurityUtils.requestPath(request);
        return !path.startsWith("/v1/platform-admin/") || PUBLIC_PATHS.contains(path);
    }
}
