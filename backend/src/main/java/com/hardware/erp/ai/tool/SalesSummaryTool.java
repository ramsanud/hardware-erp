package com.hardware.erp.ai.tool;

import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.dashboard.dto.SalesSummaryResponse;
import com.hardware.erp.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SalesSummaryTool implements AiTool {

    private final DashboardService dashboardService;

    @Override
    public String name() {
        return "get_sales_summary";
    }

    @Override
    public String description() {
        return "Returns total sales, today's sales, and total outstanding customer balance across the whole shop.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of();
    }

    @Override
    public String requiredPermission() {
        return PermissionCode.INVOICE_VIEW;
    }

    @Override
    public String execute(Map<String, String> args) {
        SalesSummaryResponse summary = dashboardService.salesSummary();
        return "Total sales: Rs. " + summary.totalSalesDisplay() + ". Today's sales: Rs. "
                + summary.todaySalesDisplay() + ". Total outstanding customer balance: Rs. "
                + summary.outstandingCustomerBalanceDisplay() + ".";
    }
}
