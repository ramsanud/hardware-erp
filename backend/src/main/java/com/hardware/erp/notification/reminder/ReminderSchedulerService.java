package com.hardware.erp.notification.reminder;

import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.service.impl.SmsWhatsAppNotificationProvider;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CR-053 backlog item 5. Two of the five reminder types from the original
 * request - payment-due digest and low-stock alert. SMS-on-transaction,
 * a daily sales-summary digest, and WhatsApp-specific alerts are deferred,
 * same "one bounded piece at a time" reasoning as every other item in this
 * backlog.
 *
 * Sends straight through {@link SmsWhatsAppNotificationProvider} (the
 * SMS/WhatsApp logging stub - no real provider account exists in this
 * environment, exactly like every other SMS/WhatsApp touchpoint in this
 * codebase) to the tenant's own contact number, deliberately bypassing
 * NotificationService's
 * customer-facing facade: a payment-due or low-stock digest is a message
 * TO the shop about ITS OWN situation, not a business document being sent
 * to a customer, so notification_log's "one row per customer-facing
 * attempt" shape does not fit it. A future phase that wants these digests
 * audited the same way customer notifications are should extend that
 * schema deliberately, not have this job reuse it as a loose fit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderSchedulerService {

    private final TenantRepository tenantRepository;
    private final InvoiceRepository invoiceRepository;
    private final StockRepository stockRepository;
    private final SmsWhatsAppNotificationProvider notificationProvider;

    @Scheduled(cron = "${app.reminders.cron:0 0 8 * * *}", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void sendDailyReminders() {
        List<Tenant> tenants = tenantRepository.findActiveWithAnyReminderEnabled();
        for (Tenant tenant : tenants) {
            if (tenant.getPhone() == null || tenant.getPhone().isBlank()) {
                // Nothing to send to - a shop that never filled in a contact
                // number cannot receive this, silently skipped rather than
                // logged as a per-tenant error on every run.
                continue;
            }
            if (tenant.isPaymentDueReminderEnabled()) {
                sendPaymentDueReminder(tenant);
            }
            if (tenant.isLowStockAlertEnabled()) {
                sendLowStockAlert(tenant);
            }
        }
    }

    private void sendPaymentDueReminder(Tenant tenant) {
        List<Object[]> summary = invoiceRepository.outstandingSummary(tenant.getId());
        long count = summary.isEmpty() ? 0 : ((Number) summary.get(0)[0]).longValue();
        long balancePaise = summary.isEmpty() ? 0 : ((Number) summary.get(0)[1]).longValue();
        if (count == 0) {
            return;
        }
        String message = "You have %d invoice(s) with a total of Rs.%.2f still outstanding."
                .formatted(count, balancePaise / 100.0);
        notificationProvider.send(NotificationChannel.SMS, tenant.getPhone(), null, message);
    }

    private void sendLowStockAlert(Tenant tenant) {
        long lowStockCount = stockRepository.countLowStock(tenant.getId());
        if (lowStockCount == 0) {
            return;
        }
        String message = "%d product(s) are at or below their reorder level.".formatted(lowStockCount);
        notificationProvider.send(NotificationChannel.SMS, tenant.getPhone(), null, message);
    }
}
