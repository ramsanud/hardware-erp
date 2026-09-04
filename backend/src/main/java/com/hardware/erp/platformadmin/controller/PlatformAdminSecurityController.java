package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.PlatformAdminActiveSessionResponse;
import com.hardware.erp.platformadmin.dto.PlatformSecurityDashboardResponse;
import com.hardware.erp.platformadmin.security.PlatformAdminPrincipal;
import com.hardware.erp.platformadmin.service.PlatformAdminSecurityService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/platform-admin/security")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Security Center")
public class PlatformAdminSecurityController {

    private final PlatformAdminSecurityService securityService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SECURITY_VIEW')")
    public ResponseEntity<ApiResponse<PlatformSecurityDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(securityService.dashboard()));
    }

    /** No permission gate beyond authentication - every admin manages their own sessions, same as /auth/logout-all. */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<PlatformAdminActiveSessionResponse>>> mySessions() {
        return ResponseEntity.ok(ApiResponse.ok(securityService.mySessions(currentAdminId())));
    }

    @PostMapping("/sessions/{id}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeSession(@PathVariable Long id, HttpServletRequest request) {
        securityService.revokeSession(id, currentAdminId(), request);
        return ResponseEntity.ok(ApiResponse.message("Session revoked"));
    }

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<ApiResponse<Integer>> revokeOtherSessions(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(securityService.revokeAllOtherSessions(currentAdminId(), request)));
    }

    private Long currentAdminId() {
        var principal = (PlatformAdminPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getId();
    }
}
