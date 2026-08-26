package com.hardware.erp.notification.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.notification.dto.MailDiagnosticResponse;
import com.hardware.erp.notification.service.MailDiagnosticService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/settings/mail")
@RequiredArgsConstructor
@Validated
@Tag(name = "Settings")
public class MailDiagnosticController {

    private final MailDiagnosticService mailDiagnosticService;

    @PostMapping("/test")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    @Operation(
            summary = "Send one test email and report what happened",
            description = """
                    Returns SENT, LOGGED_ONLY (no MAIL_USER configured) or FAILED,
                    with the mail server's own rejection text when it fails.

                    Exists so outgoing email can be proven to work before anything
                    depends on it - a login code emailed by a misconfigured server
                    locks the user out of their own account, and nothing on screen
                    would say why.""")
    public ApiResponse<MailDiagnosticResponse> sendTest(
            @RequestParam @NotBlank @Email String toEmail) {
        return ApiResponse.ok(mailDiagnosticService.sendTestEmail(toEmail));
    }
}
