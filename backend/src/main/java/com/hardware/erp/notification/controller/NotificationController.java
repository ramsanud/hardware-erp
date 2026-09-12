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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
        notificationService.contactAdmin(request.subject(), request.message(), null);
        return ApiResponse.ok(null);
    }

    /**
     * CR-073: the same report, with a screenshot attached.
     *
     * A second mapping on the same path rather than a replacement, separated
     * by {@code consumes}, because the JSON form is a published contract with
     * its own Postman entry and integration test. A reporter with nothing to
     * attach should not be made to send a multipart body, and the existing
     * callers should not have to change to keep working.
     *
     * {@code @ModelAttribute} keeps the Bean Validation on
     * {@link ContactAdminRequest} in force for the form-encoded fields, so
     * subject and message obey exactly the same limits down both paths -
     * validating one and not the other is how the two would drift apart.
     */
    @PostMapping(value = "/contact-admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> contactAdminWithScreenshot(
            @Valid @ModelAttribute ContactAdminRequest request,
            @RequestParam(value = "screenshot", required = false) MultipartFile screenshot) {
        notificationService.contactAdmin(request.subject(), request.message(), screenshot);
        return ApiResponse.ok(null);
    }
}
