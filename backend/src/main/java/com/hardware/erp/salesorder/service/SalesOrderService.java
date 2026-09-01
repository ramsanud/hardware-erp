package com.hardware.erp.salesorder.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.salesorder.dto.SalesOrderRequest;
import com.hardware.erp.salesorder.dto.SalesOrderResponse;
import com.hardware.erp.salesorder.dto.SalesOrderSummaryResponse;
import com.hardware.erp.salesorder.entity.SalesOrderStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface SalesOrderService {

    /**
     * idempotencyKey is optional (from the Idempotency-Key request header) -
     * null or blank runs the create normally. When present, CR-051's
     * IdempotencyService guarantees the order is created exactly once even
     * across a retried or double-clicked request.
     */
    SalesOrderResponse create(SalesOrderRequest request, String idempotencyKey);

    SalesOrderResponse update(Long id, SalesOrderRequest request);

    SalesOrderResponse get(Long id);

    PageResponse<SalesOrderSummaryResponse> search(String search, SalesOrderStatus status,
                                                     LocalDate fromDate, LocalDate toDate, Pageable pageable);

    SalesOrderResponse updateStatus(Long id, SalesOrderStatus target);

    /** Bills the order directly, skipping a Delivery Challan. See SalesOrderServiceImpl for the conversion rules. */
    SalesOrderResponse convertToInvoice(Long id, String idempotencyKey);

    /** Dispatches the order without billing it yet. See SalesOrderServiceImpl for the conversion rules. */
    SalesOrderResponse convertToDeliveryChallan(Long id, String idempotencyKey);
}
