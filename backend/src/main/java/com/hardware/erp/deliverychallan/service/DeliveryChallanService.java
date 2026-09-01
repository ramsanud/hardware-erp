package com.hardware.erp.deliverychallan.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanRequest;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanResponse;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanSummaryResponse;
import com.hardware.erp.deliverychallan.entity.DeliveryChallanStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface DeliveryChallanService {

    /** idempotencyKey optional - see SalesOrderService.create() for the same convention (CR-051). */
    DeliveryChallanResponse create(DeliveryChallanRequest request, String idempotencyKey);

    /**
     * Same as {@link #create}, but for a challan raised by converting a Sales
     * Order - stamps sourceSalesOrderId and skips the tenant-scoped
     * idempotency wrapper because SalesOrderServiceImpl.convertToInvoice()'s
     * own outer key (when present) already covers this call.
     */
    DeliveryChallanResponse createFromSalesOrder(DeliveryChallanRequest request, Long sourceSalesOrderId, Long tenantId);

    DeliveryChallanResponse get(Long id);

    PageResponse<DeliveryChallanSummaryResponse> search(String search, DeliveryChallanStatus status,
                                                          LocalDate fromDate, LocalDate toDate, Pageable pageable);

    DeliveryChallanResponse cancel(Long id);

    /** Bills a delivered challan. See DeliveryChallanServiceImpl for how the stock ledger stays honest. */
    DeliveryChallanResponse convertToInvoice(Long id, String idempotencyKey);
}
