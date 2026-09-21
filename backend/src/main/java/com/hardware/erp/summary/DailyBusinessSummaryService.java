package com.hardware.erp.summary;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.discovery.entity.OwnerNotificationType;
import com.hardware.erp.discovery.service.OwnerNotificationService;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.NotificationService;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.service.FeatureAccessService;
import com.hardware.erp.summary.DailySummaryRepository.DayRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * CR-092. Builds and delivers one shop's end-of-day summary. A separate
 * bean from the job so the per-tenant REQUIRES_NEW is honoured through the
 * proxy (BUG-BE-002 / BUG-BE-006 - never a same-class call).
 *
 * Delivery is the in-app owner notification always, plus the owner's own
 * channel (WhatsApp, else email) when the plan includes
 * ADVANCED_NOTIFICATIONS - that send is metered like any other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyBusinessSummaryService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final DailySummaryRepository repository;
    private final OwnerNotificationService ownerNotificationService;
    private final NotificationService notificationService;
    private final FeatureAccessService featureAccessService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationStatus summarise(Long tenantId, LocalDate day) {
        DayRow row = repository.day(tenantId, day);
        String title = "Daily summary - " + day.format(DAY);
        String body = build(day, row);
        ownerNotificationService.notify(tenantId, OwnerNotificationType.DAILY_SUMMARY, title, body, "DAILY_SUMMARY", null);
        if (!featureAccessService.hasFeature(tenantId, FeatureKey.ADVANCED_NOTIFICATIONS)) {
            return NotificationStatus.LOGGED_ONLY;
        }
        return notificationService.sendOwnerMessage(tenantId, title, body, "DAILY_SUMMARY", null);
    }

    /** Also used by the on-demand "Send today's summary now" endpoint. */
    public String preview(Long tenantId, LocalDate day) {
        return build(day, repository.day(tenantId, day));
    }

    private static String build(LocalDate day, DayRow r) {
        long invoices = n(r.getInvoiceCount());
        StringBuilder b = new StringBuilder();
        b.append("Business summary for ").append(day.format(DAY)).append('\n');
        b.append("Sales: ").append(invoices).append(invoices == 1 ? " invoice, ₹" : " invoices, ₹")
                .append(IndianCurrencyFormat.rupees(n(r.getSalesPaise()))).append('\n');
        b.append("Payments received: ₹").append(IndianCurrencyFormat.rupees(n(r.getPaymentsPaise()))).append('\n');
        b.append("Purchases: ").append(n(r.getPurchaseCount())).append(", ₹")
                .append(IndianCurrencyFormat.rupees(n(r.getPurchasesPaise()))).append('\n');
        b.append("Total outstanding from customers: ₹").append(IndianCurrencyFormat.rupees(n(r.getOutstandingPaise()))).append('\n');
        long low = n(r.getLowStockCount());
        b.append(low == 0 ? "No products at or below minimum stock." : low + " product(s) at or below minimum stock.");
        return b.toString();
    }

    private static long n(Long v) {
        return v == null ? 0L : v;
    }
}
