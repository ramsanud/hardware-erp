package com.hardware.erp.summary;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/** CR-092. Read today's summary text, or send it now rather than waiting for 20:30. */
@RestController
@RequestMapping("/v1/daily-summary")
@RequiredArgsConstructor
@Tag(name = "Daily Summary")
public class DailySummaryController {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    private final DailyBusinessSummaryService summaryService;

    @GetMapping("/today")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)")
    public ApiResponse<Map<String, String>> today() {
        LocalDate today = LocalDate.now(SHOP_ZONE);
        return ApiResponse.ok(Map.of("day", today.toString(),
                "body", summaryService.preview(SecurityUtils.requireCurrentTenantId(), today)));
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<NotificationStatus> sendNow() {
        LocalDate today = LocalDate.now(SHOP_ZONE);
        return ApiResponse.ok(summaryService.summarise(SecurityUtils.requireCurrentTenantId(), today));
    }
}
