package com.hardware.erp.billing.service;

import com.hardware.erp.billing.dto.PlatformBillingOverviewResponse;
import com.hardware.erp.billing.dto.TenantBillingHistoryResponse;

public interface PlatformBillingQueryService {

    /** Revenue chart data for the Platform Admin dashboard - last 12 calendar months, aggregated server-side. */
    PlatformBillingOverviewResponse overview();

    /** One tenant's plan + payment history, for the Tenant Detail page's Subscription section. */
    TenantBillingHistoryResponse tenantHistory(Long tenantId);
}
