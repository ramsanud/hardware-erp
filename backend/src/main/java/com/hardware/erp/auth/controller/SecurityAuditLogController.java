package com.hardware.erp.auth.controller;

import com.hardware.erp.auth.dto.SecurityAuditLogResponse;
import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.auth.service.SecurityAuditQueryService;
import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Read-only view of security events.
 *
 * Added under CR-013: the AUDIT_VIEW permission and
 * SecurityAuditLogRepository.search() both existed but nothing exposed them,
 * so the permission could be granted and never used.
 */
@RestController
@RequestMapping("/v1/security-audit-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "5. Security Audit Log", description = "Security events only. Business history lives in each module.")
public class SecurityAuditLogController {

    private static final int MAX_PAGE_SIZE = 100;

    /** Sort whitelist: a raw parameter in ORDER BY is an injection surface. */
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "action", "action",
            "userId", "userId",
            "success", "success");

    private final SecurityAuditQueryService securityAuditQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).AUDIT_VIEW)")
    @Operation(
            summary = "Search the security audit log",
            description = """
                    Records are never modifiable through the API. Deletion happens
                    only through the scheduled cleanup, and only when
                    `app.cleanup.audit-retention-days` has been set deliberately -
                    the default of 0 means never delete.

                    The log never contains passwords, password hashes, raw refresh
                    tokens, raw reset tokens, or JWT material.""")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Matching events, newest first",
            content = @Content(examples = @ExampleObject(value = """
            {
              "success": true,
              "data": {
                "content": [
                  {
                    "id": 1042, "action": "LOGIN_FAILURE", "entityType": "USER",
                    "entityId": 5, "userId": 5, "fullName": "Karthik Raja",
                    "success": false, "failureReason": "Wrong password, attempt 2",
                    "ipAddress": "192.168.1.22",
                    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                    "requestId": "8f2a1c3d-1111-4a2b-9c3d-000000000003",
                    "createdAt": "2026-08-13T09:14:22.331"
                  }
                ],
                "page": 0, "size": 20, "totalElements": 12, "totalPages": 1,
                "first": true, "last": true
              },
              "timestamp": "2026-08-13T09:14:22.331+05:30"
            }"""))))
    public ResponseEntity<ApiResponse<PageResponse<SecurityAuditLogResponse>>> search(
            @Parameter(example = "5") @RequestParam(required = false) Long userId,
            @Parameter(example = "LOGIN_FAILURE") @RequestParam(required = false) AuditAction action,
            @Parameter(example = "2026-08-01T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(example = "2026-08-31T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String safeSort = SORTABLE.getOrDefault(sortBy, "createdAt");
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(direction, safeSort));
        return ResponseEntity.ok(ApiResponse.ok(
                securityAuditQueryService.search(userId, action, from, to, pageable)));
    }
}
