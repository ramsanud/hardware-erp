package com.hardware.erp.notification.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.notification.dto.SmsDiagnosticResponse;
import com.hardware.erp.notification.service.SmsDiagnosticService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/settings/sms")
@RequiredArgsConstructor
@Validated
@Tag(name = "Settings")
public class SmsDiagnosticController {

    private final SmsDiagnosticService smsDiagnosticService;

    @PostMapping("/test")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    @Operation(
            summary = "Send one test SMS and report what happened (CR-085)",
            description = """
                    Returns SENT, LOGGED_ONLY (SMS switched off or Twilio not
                    configured) or FAILED, with Twilio's own error text when it
                    fails. The counterpart of POST /v1/settings/mail/test.""")
    public ApiResponse<SmsDiagnosticResponse> sendTest(
            @RequestParam @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number")
            String toMobileNo) {
        return ApiResponse.ok(smsDiagnosticService.sendTestSms(toMobileNo));
    }
}
