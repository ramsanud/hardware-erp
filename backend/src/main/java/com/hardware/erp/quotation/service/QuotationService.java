package com.hardware.erp.quotation.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.quotation.dto.QuotationRequest;
import com.hardware.erp.quotation.dto.QuotationResponse;
import com.hardware.erp.quotation.dto.QuotationSummaryResponse;
import com.hardware.erp.quotation.entity.QuotationStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface QuotationService {

    QuotationResponse create(QuotationRequest request);

    /**
     * Replaces a quotation's customer details, validity, remarks and line items
     * in place, keeping its number and original date. Only DRAFT and SENT
     * quotations qualify - see the impl for why the later states do not.
     */
    QuotationResponse update(Long id, QuotationRequest request);

    QuotationResponse get(Long id);

    PageResponse<QuotationSummaryResponse> search(String search, QuotationStatus status,
                                                    LocalDate fromDate, LocalDate toDate, Pageable pageable);

    QuotationResponse updateStatus(Long id, QuotationStatus status);

    QuotationResponse convert(Long id);

    byte[] generatePdf(Long id);
}
