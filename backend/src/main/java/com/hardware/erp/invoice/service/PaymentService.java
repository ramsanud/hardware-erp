package com.hardware.erp.invoice.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.invoice.dto.PaymentSummaryResponse;
import com.hardware.erp.invoice.entity.PaymentMethod;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface PaymentService {

    PageResponse<PaymentSummaryResponse> search(String search, PaymentMethod paymentMethod,
                                                 LocalDate fromDate, LocalDate toDate, Pageable pageable);
}
