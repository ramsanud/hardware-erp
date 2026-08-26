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

    /** The original uploaded bill file, tenant-scoped - never by document id alone. */
    PurchaseDocument getDocument(Long purchaseId);

    PurchaseResponse get(Long id);

    PageResponse<PurchaseSummaryResponse> search(String search, PurchaseStatus status, Pageable pageable);

    PurchaseResponse addPayment(Long purchaseId, RecordPurchasePaymentRequest request);

    PurchaseResponse cancel(Long id);
}
