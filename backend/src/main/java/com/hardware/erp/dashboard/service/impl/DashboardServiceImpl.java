package com.hardware.erp.dashboard.service.impl;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.dashboard.dto.SalesSummaryResponse;
import com.hardware.erp.dashboard.service.DashboardService;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional(readOnly = true)
    public SalesSummaryResponse salesSummary() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        List<Object[]> rows = invoiceRepository.tenantSalesSummary(tenantId);
        // The query is an unconditional aggregate (COALESCE, no GROUP BY) - always exactly one row.
        Object[] totals = rows.get(0);
        long totalSales = ((Number) totals[0]).longValue();
        long outstanding = ((Number) totals[1]).longValue();
        long todaySales = invoiceRepository.todaySales(tenantId, LocalDate.now());
        long yesterdaySales = invoiceRepository.todaySales(tenantId, LocalDate.now().minusDays(1));

        return new SalesSummaryResponse(
                IndianCurrencyFormat.rupees(totalSales),
                IndianCurrencyFormat.rupees(todaySales),
                IndianCurrencyFormat.rupees(outstanding),
                todaySales, yesterdaySales);
    }
}
