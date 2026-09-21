package com.hardware.erp.customer.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface CustomerLedgerEntryRepository extends JpaRepository<CustomerLedgerEntry, Long> {

    List<CustomerLedgerEntry> findByTenantIdAndCustomerIdOrderByEntryDateAscIdAsc(Long tenantId, Long customerId);

    @Query("""
            SELECT COALESCE(SUM(e.debitPaise), 0) - COALESCE(SUM(e.creditPaise), 0)
            FROM CustomerLedgerEntry e
            WHERE e.tenantId = :tenantId AND e.customerId = :customerId
            """)
    long balancePaise(Long tenantId, Long customerId);

    /** Balance as it stood at the end of a given moment - the opening line of a statement. */
    @Query("""
            SELECT COALESCE(SUM(e.debitPaise), 0) - COALESCE(SUM(e.creditPaise), 0)
            FROM CustomerLedgerEntry e
            WHERE e.tenantId = :tenantId AND e.customerId = :customerId AND e.entryDate < :before
            """)
    long balanceBefore(Long tenantId, Long customerId, LocalDateTime before);

    List<CustomerLedgerEntry> findByTenantIdAndCustomerIdAndEntryDateBetweenOrderByEntryDateAscIdAsc(
            Long tenantId, Long customerId, LocalDateTime from, LocalDateTime to);

    boolean existsByTenantIdAndEntryTypeAndReferenceTypeAndReferenceId(
            Long tenantId, LedgerEntryType entryType, String referenceType, Long referenceId);

    java.util.Optional<CustomerLedgerEntry> findByTenantIdAndEntryTypeAndReferenceTypeAndReferenceId(
            Long tenantId, LedgerEntryType entryType, String referenceType, Long referenceId);

    /**
     * Open invoices with the amount still owed on each, for ageing. An
     * invoice's own outstanding is its ledger debit minus the credits that
     * reference the same invoice number (payments and cancellation) - a
     * credit note is a separate document credited against the customer,
     * not against a specific invoice, and appears in the account balance
     * but not in per-invoice ageing.
     */
    @Query(value = """
            SELECT i.invoice_id, i.invoice_number, i.invoice_date, i.total_paise,
                   i.total_paise - COALESCE((
                       SELECT SUM(c.credit_paise) FROM customer_ledger_entry c
                        WHERE c.tenant_id = i.tenant_id AND c.reference_number = i.invoice_number
                          AND c.entry_type IN ('PAYMENT', 'INVOICE_CANCELLATION')), 0) AS outstanding_paise
              FROM invoice i
             WHERE i.tenant_id = :tenantId AND i.customer_id = :customerId AND i.status <> 'CANCELLED'
             ORDER BY i.invoice_date, i.invoice_id
            """, nativeQuery = true)
    List<Object[]> openInvoiceBalances(Long tenantId, Long customerId);
}
