package com.hardware.erp.invoice.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.dto.InvoiceSummaryResponse;
import com.hardware.erp.invoice.dto.PaymentRequest;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface InvoiceService {

    InvoiceResponse create(InvoiceRequest request);

    /**
     * Amends an unpaid invoice in place, keeping its number and date. Refused
     * once any payment exists - see the impl for why.
     */
    InvoiceResponse update(Long id, InvoiceRequest request);

    InvoiceResponse get(Long id);

    PageResponse<InvoiceSummaryResponse> search(String search, InvoiceStatus status,
                                                 LocalDate fromDate, LocalDate toDate, Pageable pageable);

    InvoiceResponse addPayment(Long invoiceId, PaymentRequest request);

    InvoiceResponse cancel(Long id);

    byte[] generatePdf(Long id);
}
