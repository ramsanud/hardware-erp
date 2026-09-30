package com.hardware.erp.customer.ledger;

import com.hardware.erp.customer.ledger.LedgerDtos.AgeingResponse;
import com.hardware.erp.customer.ledger.LedgerDtos.LedgerAdjustmentRequest;
import com.hardware.erp.customer.ledger.LedgerDtos.LedgerBalanceResponse;
import com.hardware.erp.customer.ledger.LedgerDtos.StatementResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CR-091 Phase 4. The one writer of customer_ledger_entry. Invoice,
 * payment, credit-note and cancellation services call post*() inside
 * their own transaction, so an invoice and its ledger debit commit or
 * roll back together - exactly the discipline CR-021 established for
 * stock movements.
 *
 * Idempotent by construction: posting the same event twice is a no-op
 * (the unique key exists for the constraint, exists() is checked first so
 * the caller sees no exception either).
 */
public interface CustomerLedgerService {

    void postInvoice(Long tenantId, Long customerId, Long invoiceId, String invoiceNumber,
                     long totalPaise, LocalDateTime when);

    void postPayment(Long tenantId, Long customerId, Long paymentId, String invoiceNumber,
                     long amountPaise, LocalDateTime when);

    void postSalesReturn(Long tenantId, Long customerId, Long creditNoteId, String creditNoteNumber,
                         long totalPaise, LocalDateTime when);

    void postInvoiceCancellation(Long tenantId, Long customerId, Long invoiceId, String invoiceNumber,
                                 long totalPaise, LocalDateTime when, String reason);

    /**
     * The one permitted change to a posted row: an UNPAID invoice amended in
     * place (InvoiceServiceImpl.update, which already refuses once any payment
     * exists) is still the same unsettled document, so its INVOICE debit
     * follows the new total rather than a second row pretending to be a
     * correction. Customer may change too (the amendment can re-point it).
     */
    void amendInvoice(Long tenantId, Long customerId, Long invoiceId, long newTotalPaise);

    LedgerBalanceResponse balance(Long customerId);

    StatementResponse statement(Long customerId, LocalDate from, LocalDate to);

    AgeingResponse ageing(Long customerId);

    /** Manual correction with a mandatory reason - PAYMENT_MANAGE. Written to activity_log as well. */
    LedgerBalanceResponse adjust(Long customerId, LedgerAdjustmentRequest request);
}
