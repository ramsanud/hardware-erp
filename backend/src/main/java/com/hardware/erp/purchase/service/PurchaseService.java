package com.hardware.erp.purchase.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.purchase.dto.PurchaseRequest;
import com.hardware.erp.purchase.dto.PurchaseResponse;
import com.hardware.erp.purchase.dto.PurchaseSummaryResponse;
import com.hardware.erp.purchase.dto.RecordPurchasePaymentRequest;
import com.hardware.erp.purchase.entity.PurchaseDocument;
import com.hardware.erp.purchase.entity.PurchaseStatus;
import org.springframework.data.domain.Pageable;

public interface PurchaseService {

    PurchaseResponse create(PurchaseRequest request);

    /**
     * CR-102. Same as the one-argument form, run exactly once per
     * {@code Idempotency-Key}: a retry after a lost response returns the
     * stored result instead of a second purchase. A null or blank key means
     * "no key" and the call simply runs. See IdempotencyService.
     */
    PurchaseResponse create(PurchaseRequest request, String idempotencyKey);

    /** The original uploaded bill file, tenant-scoped - never by document id alone. */
    PurchaseDocument getDocument(Long purchaseId);

    PurchaseResponse get(Long id);

    PageResponse<PurchaseSummaryResponse> search(String search, PurchaseStatus status, Pageable pageable);

    PurchaseResponse addPayment(Long purchaseId, RecordPurchasePaymentRequest request);

    /**
     * CR-102. Same as the one-argument form, run exactly once per
     * {@code Idempotency-Key}: a retry after a lost response returns the
     * stored result instead of a second payment. A null or blank key means
     * "no key" and the call simply runs. See IdempotencyService.
     */
    PurchaseResponse addPayment(Long purchaseId, RecordPurchasePaymentRequest request, String idempotencyKey);

    PurchaseResponse cancel(Long id);
}
