package com.hardware.erp.notification.reminder;

import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.service.impl.SmsNotificationProvider;
import com.hardware.erp.notification.service.impl.WhatsAppBusinessProvider;
import com.hardware.erp.platformadmin.service.JobExecutionTracker;
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
 * Sends straight through {@link SmsNotificationProvider} and (since Task
 * 05) {@link WhatsAppBusinessProvider} to the tenant's own contact number,
 * deliberately bypassing NotificationService's customer-facing facade: a
 * payment-due or low-stock digest is a message TO the shop about ITS OWN
 * situation, not a business document being sent to a customer, so
 * notification_log's "one row per customer-facing attempt" shape does not
 * fit it. A future phase that wants these digests audited the same way
 * customer notifications are should extend that schema deliberately, not
 * have this job reuse it as a loose fit.
 *
 * WhatsApp send failures (thrown by {@link WhatsAppBusinessProvider} when
 * configured but the API call itself fails) are caught and logged here
 * rather than allowed to abort the whole tenant loop - one shop's bad
 * WhatsApp credential must not stop every other tenant's digest for the
 * day, and SMS remains the fallback channel regardless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderSchedulerService {

    private final TenantRepository tenantRepository;
    private final InvoiceRepository invoiceRepository;
    private final StockRepository stockRepository;
    private final SmsNotificationProvider smsProvider;
    private final WhatsAppBusinessProvider whatsAppProvider;
    private final JobExecutionTracker jobExecutionTracker;

    public static final String JOB_NAME = "reminder-scheduler";

    @Scheduled(cron = "${app.reminders.cron:0 0 8 * * *}", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void sendDailyReminders() {
        Long runId = jobExecutionTracker.start(JOB_NAME);
        try {
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
            jobExecutionTracker.success(runId, "%d tenant(s) with a reminder enabled".formatted(tenants.size()));
        } catch (Exception ex) {
            jobExecutionTracker.failure(runId, ex.getMessage());
            throw ex;
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
        sendToShop(tenant, message);
    }

    private void sendLowStockAlert(Tenant tenant) {
        long lowStockCount = stockRepository.countLowStock(tenant.getId());
        if (lowStockCount == 0) {
            return;
        }
        String message = "%d product(s) are at or below their reorder level.".formatted(lowStockCount);
        sendToShop(tenant, message);
    }

    /**
     * CR-056 §11 - the Inventory page's manual "Send WhatsApp Alert" button,
     * for a shop that wants to notify itself right now rather than waiting
     * for the daily 8am job above. Same message/recipient (the shop's own
     * contact number - spec §11 "never send low-stock info to customers"),
     * same channels, just triggered on demand instead of by
     * sendDailyReminders()'s schedule. Does not respect
     * tenant.isLowStockAlertEnabled() - a manual click is an explicit
     * request, not the automated digest that toggle governs.
     */
    @Transactional(readOnly = true)
    public long sendLowStockAlertNow(Tenant tenant) {
        if (tenant.getPhone() == null || tenant.getPhone().isBlank()) {
            throw new com.hardware.erp.common.exception.BusinessException(
                    "Add a phone number in Shop Settings first - that is where this alert is sent.");
        }
        long lowStockCount = stockRepository.countLowStock(tenant.getId());
        if (lowStockCount > 0) {
            sendToShop(tenant, "%d product(s) are at or below their reorder level.".formatted(lowStockCount));
        }
        return lowStockCount;
    }

    private void sendToShop(Tenant tenant, String message) {
        smsProvider.send(tenant.getId(), NotificationChannel.SMS, tenant.getPhone(), null, message);
        try {
            whatsAppProvider.send(tenant.getId(), NotificationChannel.WHATSAPP, tenant.getPhone(), null, message);
        } catch (Exception ex) {
            log.warn("WhatsApp digest failed for tenant {} - SMS attempt above still stands", tenant.getId(), ex);
        }
    }
}
