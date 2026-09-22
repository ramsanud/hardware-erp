package com.hardware.erp.document.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.document.service.ShareDispatcherService;
import com.hardware.erp.notification.dto.WhatsAppLinkResponse;
import com.hardware.erp.notification.entity.NotificationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CR-101 - multi-channel sharing for a finished document job and the
 * occasion-greeting template. Every mapping is a GET/POST with no file
 * attached to the response body except {@code ReportJobController.download}
 * itself - see {@link ShareDispatcherService}'s own javadoc for why
 * WhatsApp never carries the file.
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Document Sharing", description = "CR-101")
public class ShareDispatcherController {

    private final ShareDispatcherService shareDispatcherService;

    @GetMapping("/documents/jobs/{id}/share/whatsapp-link")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)")
    @Operation(summary = "wa.me link for a completed job's file; omit toMobileNo to open WhatsApp's contact chooser")
    public ApiResponse<WhatsAppLinkResponse> whatsAppLink(
            @PathVariable Long id, @RequestParam(required = false) String toMobileNo) {
        return ApiResponse.ok(shareDispatcherService.whatsAppLinkForJob(id, toMobileNo));
    }

    @PostMapping("/documents/jobs/{id}/share/email")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)")
    @Operation(summary = "Emails a completed job's file as an attachment")
    public ApiResponse<NotificationStatus> emailJob(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(shareDispatcherService.emailJob(id, body.get("toEmail")));
    }

    @GetMapping("/customers/{id}/greeting-link")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    @Operation(summary = "wa.me link for a festival/birthday/anniversary greeting to one customer")
    public ApiResponse<WhatsAppLinkResponse> occasionGreeting(
            @PathVariable Long id, @RequestParam String occasion) {
        return ApiResponse.ok(shareDispatcherService.occasionGreetingLink(id, occasion));
    }
}
