package com.hardware.erp.developer;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.developer.dto.DeveloperInspectionStatusResponse;
import com.hardware.erp.developer.dto.RequestEchoResponse;
import com.hardware.erp.developer.dto.RuntimeDiagnosticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Developer diagnostics. Not part of the ERP - nothing here reads or writes
 * shop data.
 *
 * Three independent things must all hold before any endpoint below returns
 * data, and every one of them is server-side:
 *
 *   1. SecurityConfig denies the whole /v1/dev/** tree unless the
 *      environment permits inspection;
 *   2. @PreAuthorize requires the DEVELOPER_INSPECT permission, which no
 *      default role holds - not OWNER either;
 *   3. DeveloperInspectionService re-checks the environment and answers 404
 *      if it has changed.
 *
 * Hiding the React menu entry is not on that list. It is convenience, not
 * security, and the front end is written on that assumption.
 */
@RestController
@RequestMapping("/v1/dev/inspection")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "99. Developer inspection",
     description = "Non-production diagnostics. Requires the DEVELOPER_INSPECT permission "
                 + "and an environment that permits inspection. Absent from production entirely.")
public class DeveloperInspectionController {

    private final DeveloperInspectionService developerInspectionService;

    /**
     * Deliberately NOT guarded by DEVELOPER_INSPECT, so that a developer who
     * reaches the page and sees nothing can tell WHICH gate closed. "Wrong
     * environment" and "permission not granted" are different problems with
     * different fixes, and a bare 403 conflates them.
     *
     * Safe because it discloses only the caller's own answer plus the active
     * profile names. It is still behind authentication, and still behind
     * SecurityConfig's environment check on the /v1/dev tree, so an anonymous
     * visitor and a production user both learn nothing.
     *
     * The React rail does not call this - it decides whether to show the
     * Developer entry from the DEVELOPER_INSPECT permission already present
     * in the sign-in payload. Only the Developer page itself calls it.
     */
    @GetMapping("/status")
    @Operation(summary = "Whether developer inspection is available to the caller",
               description = "Both halves of the gate, reported separately so a developer "
                           + "can tell 'wrong environment' from 'permission not granted'.")
    public ResponseEntity<ApiResponse<DeveloperInspectionStatusResponse>> status() {
        return ResponseEntity.ok(ApiResponse.ok(developerInspectionService.status()));
    }

    @GetMapping("/runtime")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).DEVELOPER_INSPECT)")
    @Operation(summary = "Named runtime facts about this instance",
               description = "Build version, active profiles, JVM and heap, uptime and server clock. "
                           + "A fixed list of named fields - never a system-property or environment dump.")
    public ResponseEntity<ApiResponse<RuntimeDiagnosticsResponse>> runtime() {
        return ResponseEntity.ok(ApiResponse.ok(developerInspectionService.runtimeDiagnostics()));
    }

    @GetMapping("/request-echo")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).DEVELOPER_INSPECT)")
    @Operation(summary = "The calling request as the server received it",
               description = "For diagnosing a reverse proxy that rewrites headers, and for "
                           + "confirming which tenant the JWT resolved to. Credential-bearing "
                           + "headers are removed before the response is built.")
    public ResponseEntity<ApiResponse<RequestEchoResponse>> requestEcho(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(developerInspectionService.requestEcho(request)));
    }
}
