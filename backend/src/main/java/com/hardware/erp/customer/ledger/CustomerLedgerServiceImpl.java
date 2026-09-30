package com.hardware.erp.customer.ledger;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.ledger.LedgerDtos.AgeingBucket;
import com.hardware.erp.customer.ledger.LedgerDtos.AgeingInvoice;
import com.hardware.erp.customer.ledger.LedgerDtos.AgeingResponse;
import com.hardware.erp.customer.ledger.LedgerDtos.LedgerAdjustmentRequest;
import com.hardware.erp.customer.ledger.LedgerDtos.LedgerBalanceResponse;
import com.hardware.erp.customer.ledger.LedgerDtos.LedgerEntryResponse;
import com.hardware.erp.customer.ledger.LedgerDtos.StatementResponse;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CR-091 Phase 4. The post*() methods are called by other services inside
 * THEIR transactions and carry the tenant explicitly (the invoice's own,
 * already verified) - they never re-read the security context, so a
 * scheduled job or an import that posts on a tenant's behalf works too.
 * The read methods are for the API and resolve the tenant from the JWT.
 */
@Service
@RequiredArgsConstructor
public class CustomerLedgerServiceImpl implements CustomerLedgerService {

    private static final String MODULE = "CUSTOMER";
    private static final String ENTITY = "CUSTOMER_LEDGER";

    private final CustomerLedgerEntryRepository repository;
    private final CustomerRepository customerRepository;
    private final ActivityLogService activityLog;

    @Override
    @Transactional
    public void postInvoice(Long tenantId, Long customerId, Long invoiceId, String invoiceNumber,
                            long totalPaise, LocalDateTime when) {
        post(tenantId, customerId, LedgerEntryType.INVOICE, when, totalPaise, 0L,
                "INVOICE", invoiceId, invoiceNumber, null);
    }

    @Override
    @Transactional
    public void postPayment(Long tenantId, Long customerId, Long paymentId, String invoiceNumber,
                            long amountPaise, LocalDateTime when) {
        post(tenantId, customerId, LedgerEntryType.PAYMENT, when, 0L, amountPaise,
                "PAYMENT", paymentId, invoiceNumber, null);
    }

    @Override
    @Transactional
    public void postSalesReturn(Long tenantId, Long customerId, Long creditNoteId, String creditNoteNumber,
                                long totalPaise, LocalDateTime when) {
        post(tenantId, customerId, LedgerEntryType.SALES_RETURN, when, 0L, totalPaise,
                "CREDIT_NOTE", creditNoteId, creditNoteNumber, null);
    }

    @Override
    @Transactional
    public void postInvoiceCancellation(Long tenantId, Long customerId, Long invoiceId, String invoiceNumber,
                                        long totalPaise, LocalDateTime when, String reason) {
        post(tenantId, customerId, LedgerEntryType.INVOICE_CANCELLATION, when, 0L, totalPaise,
                "INVOICE", invoiceId, invoiceNumber, reason);
    }

    @Override
    @Transactional
    public void amendInvoice(Long tenantId, Long customerId, Long invoiceId, long newTotalPaise) {
        repository.findByTenantIdAndEntryTypeAndReferenceTypeAndReferenceId(
                        tenantId, LedgerEntryType.INVOICE, "INVOICE", invoiceId)
                .ifPresent(entry -> {
                    entry.setCustomerId(customerId);
                    entry.setDebitPaise(newTotalPaise);
                    repository.save(entry);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerBalanceResponse balance(Long customerId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Customer customer = requireCustomer(customerId, tenantId);
        List<CustomerLedgerEntry> entries = repository
                .findByTenantIdAndCustomerIdOrderByEntryDateAscIdAsc(tenantId, customerId);
        long debit = entries.stream().mapToLong(CustomerLedgerEntry::getDebitPaise).sum();
        long credit = entries.stream().mapToLong(CustomerLedgerEntry::getCreditPaise).sum();
        return new LedgerBalanceResponse(customerId, customer.getCustomerName(),
                debit - credit, IndianCurrencyFormat.rupees(debit - credit),
                debit, IndianCurrencyFormat.rupees(debit), credit, IndianCurrencyFormat.rupees(credit));
    }

    @Override
    @Transactional(readOnly = true)
    public StatementResponse statement(Long customerId, LocalDate from, LocalDate to) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Customer customer = requireCustomer(customerId, tenantId);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        long running = repository.balanceBefore(tenantId, customerId, start);
        long opening = running;
        List<LedgerEntryResponse> rows = new ArrayList<>();
        for (CustomerLedgerEntry entry : repository
                .findByTenantIdAndCustomerIdAndEntryDateBetweenOrderByEntryDateAscIdAsc(
                        tenantId, customerId, start, end.minusNanos(1))) {
            running += entry.getDebitPaise() - entry.getCreditPaise();
            rows.add(new LedgerEntryResponse(entry.getId(), entry.getEntryType(), entry.getEntryDate(),
                    entry.getDebitPaise(), IndianCurrencyFormat.rupees(entry.getDebitPaise()),
                    entry.getCreditPaise(), IndianCurrencyFormat.rupees(entry.getCreditPaise()),
                    running, IndianCurrencyFormat.rupees(running),
                    entry.getReferenceType(), entry.getReferenceId(), entry.getReferenceNumber(), entry.getNotes()));
        }
        return new StatementResponse(customerId, customer.getCustomerName(), from, to,
                opening, IndianCurrencyFormat.rupees(opening), rows,
                running, IndianCurrencyFormat.rupees(running));
    }

    /**
     * Buckets are the ones Indian shop accounting uses: current (0-30),
     * 31-60, 61-90, over 90. Age from the invoice date - invoices carry no
     * separate due date, and the response says so.
     */
    @Override
    @Transactional(readOnly = true)
    public AgeingResponse ageing(Long customerId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Customer customer = requireCustomer(customerId, tenantId);
        LocalDate today = LocalDate.now();

        long[] bucketPaise = new long[4];
        int[] bucketCount = new int[4];
        List<AgeingInvoice> invoices = new ArrayList<>();
        long total = 0;
        for (Object[] row : repository.openInvoiceBalances(tenantId, customerId)) {
            long outstanding = ((Number) row[4]).longValue();
            if (outstanding <= 0) {
                continue;
            }
            LocalDate invoiceDate = toLocalDate(row[2]);
            int age = (int) ChronoUnit.DAYS.between(invoiceDate, today);
            int bucket = age <= 30 ? 0 : age <= 60 ? 1 : age <= 90 ? 2 : 3;
            bucketPaise[bucket] += outstanding;
            bucketCount[bucket]++;
            total += outstanding;
            invoices.add(new AgeingInvoice(((Number) row[0]).longValue(), (String) row[1], invoiceDate, age,
                    ((Number) row[3]).longValue(), outstanding, IndianCurrencyFormat.rupees(outstanding)));
        }

        List<AgeingBucket> buckets = List.of(
                new AgeingBucket("0-30 days", 0, 30, bucketPaise[0], IndianCurrencyFormat.rupees(bucketPaise[0]), bucketCount[0]),
                new AgeingBucket("31-60 days", 31, 60, bucketPaise[1], IndianCurrencyFormat.rupees(bucketPaise[1]), bucketCount[1]),
                new AgeingBucket("61-90 days", 61, 90, bucketPaise[2], IndianCurrencyFormat.rupees(bucketPaise[2]), bucketCount[2]),
                new AgeingBucket("Over 90 days", 91, null, bucketPaise[3], IndianCurrencyFormat.rupees(bucketPaise[3]), bucketCount[3]));
        return new AgeingResponse(customerId, customer.getCustomerName(), today, buckets, invoices,
                total, IndianCurrencyFormat.rupees(total));
    }

    @Override
    @Transactional
    public LedgerBalanceResponse adjust(Long customerId, LedgerAdjustmentRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        requireCustomer(customerId, tenantId);
        CustomerLedgerEntry saved = repository.save(CustomerLedgerEntry.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .entryType(LedgerEntryType.ADJUSTMENT)
                .entryDate(LocalDateTime.now())
                .debitPaise(request.debit() ? request.amountPaise() : 0L)
                .creditPaise(request.debit() ? 0L : request.amountPaise())
                .referenceType("ADJUSTMENT")
                // An adjustment references itself: there is no other document.
                // The unique key still holds because the id is fresh.
                .referenceId(System.nanoTime())
                .notes(request.reason().trim())
                .createdAt(LocalDateTime.now())
                .createdBy(SecurityUtils.currentUserId().orElse(null))
                .build());
        saved.setReferenceId(saved.getId());
        repository.save(saved);
        activityLog.created(MODULE, ENTITY, saved.getId(), "Ledger adjustment",
                Map.of("customerId", customerId, "debit", request.debit(),
                        "amountPaise", request.amountPaise(), "reason", request.reason()));
        return balance(customerId);
    }

    // ---------------------------------------------------------------

    private void post(Long tenantId, Long customerId, LedgerEntryType type, LocalDateTime when,
                      long debit, long credit, String referenceType, Long referenceId,
                      String referenceNumber, String notes) {
        if (repository.existsByTenantIdAndEntryTypeAndReferenceTypeAndReferenceId(
                tenantId, type, referenceType, referenceId)) {
            return;
        }
        repository.save(CustomerLedgerEntry.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .entryType(type)
                .entryDate(when == null ? LocalDateTime.now() : when)
                .debitPaise(debit)
                .creditPaise(credit)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .referenceNumber(referenceNumber)
                .notes(notes)
                .createdAt(LocalDateTime.now())
                .createdBy(SecurityUtils.currentUserId().orElse(null))
                .build());
    }

    private Customer requireCustomer(Long customerId, Long tenantId) {
        return customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    }

    /** Guard against the JDBC driver handing back a Timestamp for a DATE column on some drivers. */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return ((Timestamp) value).toLocalDateTime().toLocalDate();
    }
}
