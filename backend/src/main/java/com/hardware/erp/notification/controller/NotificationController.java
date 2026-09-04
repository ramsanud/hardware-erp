package com.hardware.erp.notification.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.notification.dto.ContactAdminRequest;
import com.hardware.erp.notification.dto.NotificationLogResponse;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only log view, gated by SETTINGS_VIEW rather than a new permission
 * code - administrative visibility into outbound messaging, not a business
 * module of its own. contact-admin is separate: any authenticated user in
 * any role may report a problem with the application itself, not just a
 * settings manager.
 */
@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/log")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_VIEW)")
    public ApiResponse<PageResponse<NotificationLogResponse>> log(
            @RequestParam(required = false) NotificationChannel channel,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(notificationService.search(channel, pageable));
    }

    @PostMapping("/contact-admin")
    public ApiResponse<Void> contactAdmin(@Valid @RequestBody ContactAdminRequest request) {
        notificationService.contactAdmin(request.subject(), request.message());
        return ApiResponse.ok(null);
    }
}
