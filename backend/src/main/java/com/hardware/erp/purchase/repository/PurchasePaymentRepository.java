package com.hardware.erp.purchase.repository;

import com.hardware.erp.purchase.entity.PurchasePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchasePaymentRepository extends JpaRepository<PurchasePayment, Long> {

    List<PurchasePayment> findByPurchaseIdOrderByPaymentDateDesc(Long purchaseId);
}
