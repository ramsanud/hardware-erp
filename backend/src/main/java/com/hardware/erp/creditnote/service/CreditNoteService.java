package com.hardware.erp.creditnote.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.creditnote.dto.CreditNoteRequest;
import com.hardware.erp.creditnote.dto.CreditNoteResponse;
import com.hardware.erp.creditnote.dto.CreditNoteSummaryResponse;
import com.hardware.erp.creditnote.entity.CreditNoteStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface CreditNoteService {

    /** idempotencyKey optional - see SalesOrderService.create() for the same convention (CR-051). */
    CreditNoteResponse create(CreditNoteRequest request, String idempotencyKey);

    CreditNoteResponse get(Long id);

    PageResponse<CreditNoteSummaryResponse> search(String search, CreditNoteStatus status,
                                                     LocalDate fromDate, LocalDate toDate, Pageable pageable);

    /** Reverses the stock movement. Never un-does an invoice's own figures - see CreditNoteServiceImpl. */
    CreditNoteResponse cancel(Long id);
}
