package com.hardware.erp.tenant.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.tenant.dto.DataResetPreviewResponse;
import com.hardware.erp.tenant.dto.DataResetRequest;
import com.hardware.erp.tenant.dto.DataResetResponse;
import com.hardware.erp.tenant.service.DataResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR-067 - the shop's own reset. Never a platform-admin tool: the tenant comes
 * from the caller's token and there is no path variable to point at another
 * shop.
 *
 * Both endpoints require DATA_RESET rather than SETTINGS_MANAGE. Correcting a
 * GSTIN and erasing a shop's trading history are different authorities, and a
 * role granted the first must not silently acquire the second.
 */
@RestController
@RequestMapping("/v1/settings/data-reset")
@RequiredArgsConstructor
@Tag(name = "Settings")
public class DataResetController {

    private final DataResetService dataResetService;

    @GetMapping("/preview")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).DATA_RESET)")
    @Operation(
            summary = "What a reset would delete",
            description = """
                    Counts, per record type, taken from the caller's own tenant.

                    The confirmation dialog is built from this rather than from
                    fixed wording, for the same reason a bill import previews
                    before it commits: "342 invoices, 118 payments" is a
                    decision a person can make, "your data" is not.

                    Also returns the shop name the caller must type back, and
                    whether a CAPTCHA token is required - Turnstile is inactive
                    on installs that never configured its keys.""")
    public ApiResponse<DataResetPreviewResponse> preview() {
        return ApiResponse.ok(dataResetService.preview());
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).DATA_RESET)")
    @Operation(
            summary = "Permanently delete this shop's transactional data",
            description = """
                    Deletes invoices, quotations, sales orders, delivery
                    challans, credit notes, payments, purchases, expenses,
                    projects, labour attendance and wages, and the whole stock
                    ledger - in one transaction, all or nothing.

                    **Keeps** users, roles, settings and every master record:
                    products, customers, suppliers, categories, brands, workers,
                    work types, expense categories and coupon definitions.
                    `activity_log` and `security_audit_log` are kept too, and
                    this operation writes to both.

                    Requires the shop's own name in `confirmationPhrase` and,
                    where a challenge is configured, a valid `captchaToken`.
                    Either one failing deletes nothing:

                    | Condition | Response |
                    |---|---|
                    | CAPTCHA active, token missing or rejected | 400 `CAPTCHA_FAILED` |
                    | CAPTCHA active, Cloudflare unreachable | 503 `CAPTCHA_UNAVAILABLE` |
                    | Phrase does not match the shop name | 400 `RESET_CONFIRMATION_FAILED` |

                    There is no undo. Take a backup first.""")
    public ApiResponse<DataResetResponse> reset(@Valid @RequestBody DataResetRequest request,
                                                HttpServletRequest httpRequest) {
        DataResetResponse result = dataResetService.reset(request, httpRequest);
        return ApiResponse.ok(result.totalRecords() + " records permanently deleted", result);
    }
}
