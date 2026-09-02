package com.hardware.erp.notification.reminder;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CR-056 §11 - manual trigger for ReminderSchedulerService's own low-stock digest, see its javadoc. */
@RestController
@RequestMapping("/v1/inventory/low-stock")
@RequiredArgsConstructor
@Tag(name = "Inventory")
public class LowStockAlertController {

    private final ReminderSchedulerService reminderSchedulerService;
    private final TenantRepository tenantRepository;

    @PostMapping("/send-alert")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVENTORY_VIEW)")
    @Transactional(readOnly = true)
    public ApiResponse<Long> sendAlertNow() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Tenant tenant = tenantRepository.getReferenceById(tenantId);
        return ApiResponse.ok(reminderSchedulerService.sendLowStockAlertNow(tenant));
    }
}
