package com.hardware.erp.platformadmin.service;

import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.invoice.repository.PaymentRepository;
import com.hardware.erp.platformadmin.dto.PlatformDashboardResponse;
import com.hardware.erp.purchase.repository.PurchaseRepository;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Admin Overview. Every number here is a real aggregate query run against
 * the live database at request time - see the class javadoc on
 * PlatformDashboardResponse for what this phase deliberately does not
 * fake (system health, error rate, MRR).
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminDashboardService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;
    private final PurchaseRepository purchaseRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public PlatformDashboardResponse overview() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);

        LocalDate startOfThisMonth = today.withDayOfMonth(1);
        LocalDate startOfLastMonth = startOfThisMonth.minusMonths(1);

        long totalTenants = tenantRepository.count();
        long activeTenants = tenantRepository.countByStatus(TenantStatus.ACTIVE);
        long suspendedTenants = tenantRepository.countByStatus(TenantStatus.SUSPENDED);
        long newThisMonth = tenantRepository.countByCreatedAtBetween(
                startOfThisMonth.atStartOfDay(), startOfTomorrow);
        long newLastMonth = tenantRepository.countByCreatedAtBetween(
                startOfLastMonth.atStartOfDay(), startOfThisMonth.atStartOfDay());
        Double growthPercent = newLastMonth == 0 ? null
                : ((newThisMonth - newLastMonth) * 100.0) / newLastMonth;

        var tenants = new PlatformDashboardResponse.Tenants(
                totalTenants, activeTenants, suspendedTenants, newThisMonth, growthPercent);

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long newUsersToday = userRepository.countByCreatedAtBetween(startOfToday, startOfTomorrow);
        var users = new PlatformDashboardResponse.Users(totalUsers, activeUsers, newUsersToday);

        var activityToday = new PlatformDashboardResponse.BusinessActivityToday(
                invoiceRepository.countByInvoiceDate(today),
                paymentRepository.countByPaymentDateBetween(startOfToday, startOfTomorrow),
                purchaseRepository.countByPurchaseDate(today));

        var subscriptions = new PlatformDashboardResponse.Subscriptions(
                tenantRepository.countBySubscriptionTier(SubscriptionTier.FREE),
                tenantRepository.countBySubscriptionTier(SubscriptionTier.PRO),
                tenantRepository.countBySubscriptionTier(SubscriptionTier.MAX));

        var health = new PlatformDashboardResponse.PlatformHealth(true);

        return new PlatformDashboardResponse(
                tenants, users, activityToday, subscriptions, health, LocalDateTime.now());
    }
}
