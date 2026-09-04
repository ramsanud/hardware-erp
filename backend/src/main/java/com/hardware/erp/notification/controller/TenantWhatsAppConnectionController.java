package com.hardware.erp.notification.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.notification.dto.WhatsAppConnectionRequest;
import com.hardware.erp.notification.dto.WhatsAppConnectionResponse;
import com.hardware.erp.notification.dto.WhatsAppTestSendRequest;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.NotificationService;
import com.hardware.erp.notification.service.TenantWhatsAppConnectionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * CR-056. Same permission pair as every other Settings sub-resource
 * (bank accounts, brand, etc.) - SETTINGS_VIEW to see the connection
 * status, SETTINGS_MANAGE to connect/disconnect. Deliberately no tenantId
 * anywhere on this API - every method resolves it server-side (spec §6).
 */
@RestController
@RequestMapping("/v1/settings/whatsapp")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Business")
public class TenantWhatsAppConnectionController {

    private final TenantWhatsAppConnectionService connectionService;
    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_VIEW)")
    public ApiResponse<WhatsAppConnectionResponse> getStatus() {
        return ApiResponse.ok(connectionService.getStatus());
    }

    @PostMapping("/connect")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<WhatsAppConnectionResponse> connect(@Valid @RequestBody WhatsAppConnectionRequest request) {
        return ApiResponse.ok(connectionService.connect(request));
    }

    @PostMapping("/disconnect")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<WhatsAppConnectionResponse> disconnect() {
        return ApiResponse.ok(connectionService.disconnect());
    }

    @PostMapping("/test-send")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<NotificationStatus> testSend(@Valid @RequestBody WhatsAppTestSendRequest request) {
        return ApiResponse.ok(notificationService.sendTestWhatsApp(request.toMobileNo()));
    }
}
