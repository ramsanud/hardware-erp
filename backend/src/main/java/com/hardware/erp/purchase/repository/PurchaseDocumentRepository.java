package com.hardware.erp.purchase.repository;

import com.hardware.erp.purchase.entity.PurchaseDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseDocumentRepository extends JpaRepository<PurchaseDocument, Long> {

    Optional<PurchaseDocument> findByPurchaseIdAndTenantId(Long purchaseId, Long tenantId);
}
