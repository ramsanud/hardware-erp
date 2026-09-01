package com.hardware.erp.creditnote.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface CreditNoteItemRepository extends JpaRepository<com.hardware.erp.creditnote.entity.CreditNoteItem, Long> {

    /**
     * Quantity already credited against one original invoice line, summed
     * across every non-cancelled credit note - the guard that stops the
     * same line being over-returned across several separate credit notes.
     * See V37's header comment.
     */
    @Query("""
           select coalesce(sum(ci.quantity), 0) from CreditNoteItem ci
           where ci.invoiceItem.id = :invoiceItemId
             and ci.creditNote.status <> com.hardware.erp.creditnote.entity.CreditNoteStatus.CANCELLED
           """)
    BigDecimal sumCreditedQuantity(@Param("invoiceItemId") Long invoiceItemId);
}
