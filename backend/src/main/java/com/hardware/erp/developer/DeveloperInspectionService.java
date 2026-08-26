package com.hardware.erp.developer;

import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.web.RequestCorrelationFilter;
import com.hardware.erp.developer.dto.DeveloperInspectionStatusResponse;
import com.hardware.erp.developer.dto.RequestEchoResponse;
import com.hardware.erp.developer.dto.RuntimeDiagnosticsResponse;
import com.hardware.erp.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Developer inspection, gated on the environment AND on the person (CR-045).
 *
 * The two gates are independent on purpose:
 *
 *   environment  - app.developer-inspection.enabled, plus the prod override
 *                  below. Says "this box is a place where debugging happens".
 *   person       - the DEVELOPER_INSPECT permission, checked by @PreAuthorize
 *                  on the controller. Says "this human is a developer".
 *
 * Neither is sufficient alone. A developer signing into production gets
 * nothing; a shop owner on a dev box gets nothing, because OWNER does not
 * hold DEVELOPER_INSPECT - see V30 and TenantRegistrationServiceImpl.
 */
@Service
@RequiredArgsConstructor
public class DeveloperInspectionService {

    /**
     * Headers that carry a credential. Removed entirely rather than masked -
     * a masked value still confirms the header's presence and length, and
     * there is no diagnostic question that needs either.
     */
    private static final Set<String> CREDENTIAL_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "proxy-authorization",
            "x-api-key", "x-auth-token");

    private static final long MB = 1024L * 1024L;

    private final DeveloperInspectionProperties properties;
    private final Environment environment;

    /**
     * Production always answers false, whatever the configuration says.
     *
     * application-prod.yml already sets a hard false, so this is the second
     * lock on the same door: it means a future edit to that file, or a
     * misordered property source, cannot quietly re-enable diagnostics on a
     * box serving real shops. Mirrors JwtSecretGuard's stance on the
     * placeholder signing key.
     */
    public boolean environmentAllows() {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return false;
        }
        return properties.enabled();
    }

    public boolean callerHoldsPermission() {
        return SecurityUtils.currentUser()
                .map(user -> user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(PermissionCode.DEVELOPER_INSPECT::equals))
                .orElse(false);
    }

    /**
     * Status is readable by any signed-in user - it is what the React shell
     * asks before deciding whether to render the Developer entry in the rail.
     * It discloses the profile NAMES only, never a configured value.
     */
    public DeveloperInspectionStatusResponse status() {
        boolean env = environmentAllows();
        boolean permission = callerHoldsPermission();
        return new DeveloperInspectionStatusResponse(
                env && permission, env, permission,
                List.of(environment.getActiveProfiles()));
    }

    /**
     * 404, not 403. In an environment where inspection is off, the honest
     * answer is that this endpoint does not exist there - a 403 would confirm
     * the route is real and worth attacking. SecurityConfig denies the whole
     * /v1/dev/** tree before a request reaches here anyway; this is the
     * in-code half of the same rule, so the guarantee does not depend on one
     * line of the filter chain staying correct.
     */
    private void requireEnabled() {
        if (!environmentAllows()) {
            throw new BusinessException(
                    "Endpoint not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }

    public RuntimeDiagnosticsResponse runtimeDiagnostics() {
        requireEnabled();

        Runtime runtime = Runtime.getRuntime();
        String version = getClass().getPackage().getImplementationVersion();

        return new RuntimeDiagnosticsResponse(
                environment.getProperty("spring.application.name", "hardware-erp"),
                // Null when running from exploded classes rather than the jar,
                // which is every IDE run - not worth failing the call over.
                version != null ? version : "development-build",
                List.of(environment.getActiveProfiles()),
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                runtime.availableProcessors(),
                (runtime.totalMemory() - runtime.freeMemory()) / MB,
                runtime.maxMemory() / MB,
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                OffsetDateTime.now(),
                ZoneId.systemDefault().getId());
    }

    public RequestEchoResponse requestEcho(HttpServletRequest request) {
        requireEnabled();

        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (!CREDENTIAL_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                headers.put(name, request.getHeader(name));
            }
        }

        return new RequestEchoResponse(
                request.getMethod(),
                request.getRequestURI(),
                RequestCorrelationFilter.currentRequestId(request),
                SecurityUtils.clientIp(request),
                SecurityUtils.currentUserId().orElse(null),
                SecurityUtils.currentTenantId().orElse(null),
                Collections.unmodifiableMap(headers));
    }
}
