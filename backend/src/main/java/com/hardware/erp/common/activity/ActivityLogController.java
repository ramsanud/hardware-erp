package com.hardware.erp.common.activity;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CR-072. The business audit trail, readable at last.
 *
 * `activity_log` has been written by roughly ten services since CR-015 and no
 * controller has ever read it back, so the before/after values that make it
 * worth writing were visible only through a database client. This is that
 * viewer.
 *
 * READ ONLY, AND IT MUST STAY THAT WAY. There is no endpoint here to edit or
 * delete a row, and none should be added: an audit trail an operator can prune
 * is not an audit trail.
 *
 * Gated on AUDIT_VIEW, the same permission the security-log viewer uses. Both
 * answer "who changed what", and splitting them would mean granting two
 * permissions to answer one question.
 */
@RestController
@RequestMapping("/v1/activity-log")
@RequiredArgsConstructor
@Tag(name = "Activity log")
public class ActivityLogController {

    private final ActivityLogQueryService activityLogQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).AUDIT_VIEW)")
    @Operation(summary = "Business changes for this shop, newest first",
            description = """
                    Every create, update, delete and lifecycle action recorded
                    by the business modules, with the fields that changed.
                    Scoped to the caller's own shop from the JWT - no parameter
                    can widen it. Rows written with no signed-in user (a
                    scheduled job, an import) carry no tenant and are returned
                    to nobody.""")
    public ApiResponse<PageResponse<ActivityLogResponse>> search(
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(activityLogQueryService.search(
                moduleCode, entityType, entityId, userId, fromDate, toDate, pageable));
    }

    @GetMapping("/modules")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).AUDIT_VIEW)")
    @Operation(summary = "Module codes this shop actually has history for",
            description = "Populates the module filter with values that will return something.")
    public ApiResponse<List<String>> moduleCodes() {
        return ApiResponse.ok(activityLogQueryService.moduleCodes());
    }
}
