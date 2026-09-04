package com.hardware.erp.billing.service.impl;

import com.hardware.erp.billing.config.RazorpayProperties;
import com.hardware.erp.billing.dto.PlatformBillingOverviewResponse;
import com.hardware.erp.billing.dto.SubscriptionPaymentResponse;
import com.hardware.erp.billing.dto.TenantBillingHistoryResponse;
import com.hardware.erp.billing.entity.PlatformSubscriptionPayment;
import com.hardware.erp.billing.entity.SubscriptionPaymentStatus;
import com.hardware.erp.billing.repository.PlatformSubscriptionPaymentRepository;
import com.hardware.erp.billing.service.PlatformBillingQueryService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Platform Admin dashboard's Revenue chart (spec §3/§27) - aggregated
 * server-side, one query per month rather than shipping raw payment rows
 * to the browser (spec §26). 12 small COUNT/SUM queries a request is a
 * deliberate simplicity-over-cleverness choice - this endpoint is not on
 * a hot path and the table has no reason to be large.
 */
@Service
@RequiredArgsConstructor
public class PlatformBillingQueryServiceImpl implements PlatformBillingQueryService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");

    private final RazorpayProperties properties;
    private final PlatformSubscriptionPaymentRepository paymentRepository;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public PlatformBillingOverviewResponse overview() {
        YearMonth currentMonth = YearMonth.now();
        List<PlatformBillingOverviewResponse.MonthlyRevenuePoint> monthly = new ArrayList<>();
        long totalRevenue = 0;
        long totalSuccess = 0;
        long totalFailed = 0;

        for (int i = 11; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            LocalDateTime from = month.atDay(1).atStartOfDay();
            LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();

            long revenue = paymentRepository.sumCapturedAmountPaiseBetween(from, to);
            long success = paymentRepository.findByStatusAndCreatedAtBetween(
                    SubscriptionPaymentStatus.CAPTURED, from, to).size();
            long failed = paymentRepository.findByStatusAndCreatedAtBetween(
                    SubscriptionPaymentStatus.FAILED, from, to).size();

            monthly.add(new PlatformBillingOverviewResponse.MonthlyRevenuePoint(
                    month.format(MONTH_LABEL), revenue, success, failed));
            totalRevenue += revenue;
            totalSuccess += success;
            totalFailed += failed;
        }

        return new PlatformBillingOverviewResponse(
                properties.active(), totalRevenue, totalSuccess, totalFailed, monthly);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantBillingHistoryResponse tenantHistory(Long tenantId) {
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant not found.", HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        List<SubscriptionPaymentResponse> payments = paymentRepository.findByOrder_Tenant_IdOrderByCreatedAtDesc(tenantId)
                .stream().map(this::toResponse).toList();

        return new TenantBillingHistoryResponse(tenant.getSubscriptionTier(), payments);
    }

    private SubscriptionPaymentResponse toResponse(PlatformSubscriptionPayment payment) {
        return new SubscriptionPaymentResponse(
                payment.getId(), payment.getOrder().getId(), payment.getOrder().getRequestedTier(),
                payment.getAmountPaise(), payment.getOrder().getCurrency(),
                payment.getStatus(), payment.getSource(), payment.getCapturedAt());
    }
}
