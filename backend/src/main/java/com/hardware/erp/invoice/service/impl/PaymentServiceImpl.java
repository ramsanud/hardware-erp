package com.hardware.erp.invoice.service.impl;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.invoice.dto.PaymentSummaryResponse;
import com.hardware.erp.invoice.entity.PaymentMethod;
import com.hardware.erp.invoice.mapper.InvoiceMapper;
import com.hardware.erp.invoice.repository.PaymentRepository;
import com.hardware.erp.invoice.service.PaymentService;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Read-only view across every payment ever recorded, regardless of which
 * invoice it was taken against. Creation stays on InvoiceServiceImpl.addPayment
 * - this service never writes a Payment row.
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceMapper invoiceMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentSummaryResponse> search(String search, PaymentMethod paymentMethod,
                                                         LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        // payment_date is a timestamp; a LocalDate filter must widen to the
        // full calendar day on both ends to stay inclusive.
        LocalDateTime fromDateTime = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate == null ? null : LocalDateTime.of(toDate, LocalTime.MAX);
        return PageResponse.from(
                paymentRepository.search(tenantId, search, paymentMethod, fromDateTime, toDateTime, pageable),
                invoiceMapper::toSummary);
    }
}
