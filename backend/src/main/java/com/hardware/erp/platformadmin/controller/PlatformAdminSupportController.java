package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.platformadmin.dto.PlatformSupportDashboardResponse;
import com.hardware.erp.platformadmin.security.PlatformAdminPrincipal;
import com.hardware.erp.platformadmin.service.PlatformAdminSupportService;
import com.hardware.erp.supportticket.dto.SupportTicketDetailResponse;
import com.hardware.erp.supportticket.dto.SupportTicketSummaryResponse;
import com.hardware.erp.supportticket.dto.TicketMessageRequest;
import com.hardware.erp.supportticket.entity.TicketCategory;
import com.hardware.erp.supportticket.entity.TicketPriority;
import com.hardware.erp.supportticket.entity.TicketStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/platform-admin/support")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Support Center")
public class PlatformAdminSupportController {

    private final PlatformAdminSupportService supportService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SUPPORT_VIEW')")
    public ResponseEntity<ApiResponse<PlatformSupportDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(supportService.dashboard(currentAdminId())));
    }

    @GetMapping("/tickets")
    @PreAuthorize("hasAuthority('SUPPORT_VIEW')")
    public ResponseEntity<ApiResponse<PageResponse<SupportTicketSummaryResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) Long assignedAdminId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                supportService.search(search, status, priority, category, assignedAdminId, pageable)));
    }

    @GetMapping("/tickets/{id}")
    @PreAuthorize("hasAuthority('SUPPORT_VIEW')")
    public ResponseEntity<ApiResponse<SupportTicketDetailResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(supportService.get(id)));
    }

    @PostMapping("/tickets/{id}/messages")
    @PreAuthorize("hasAuthority('SUPPORT_MANAGE')")
    public ResponseEntity<ApiResponse<SupportTicketDetailResponse>> reply(
            @PathVariable Long id, @Valid @RequestBody TicketMessageRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(supportService.reply(id, request, currentAdminId(), httpRequest)));
    }

    @PostMapping("/tickets/{id}/assign/{assigneeAdminId}")
    @PreAuthorize("hasAuthority('SUPPORT_MANAGE')")
    public ResponseEntity<ApiResponse<SupportTicketSummaryResponse>> assign(
            @PathVariable Long id, @PathVariable Long assigneeAdminId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supportService.assign(id, assigneeAdminId, currentAdminId(), request)));
    }

    @PostMapping("/tickets/{id}/priority/{priority}")
    @PreAuthorize("hasAuthority('SUPPORT_MANAGE')")
    public ResponseEntity<ApiResponse<SupportTicketSummaryResponse>> changePriority(
            @PathVariable Long id, @PathVariable TicketPriority priority, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supportService.changePriority(id, priority, currentAdminId(), request)));
    }

    @PostMapping("/tickets/{id}/status/{status}")
    @PreAuthorize("hasAuthority('SUPPORT_MANAGE')")
    public ResponseEntity<ApiResponse<SupportTicketSummaryResponse>> changeStatus(
            @PathVariable Long id, @PathVariable TicketStatus status, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supportService.changeStatus(id, status, currentAdminId(), request)));
    }

    private Long currentAdminId() {
        var principal = (PlatformAdminPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getId();
    }
}
