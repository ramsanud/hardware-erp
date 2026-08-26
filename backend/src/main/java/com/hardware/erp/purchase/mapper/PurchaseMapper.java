package com.hardware.erp.purchase.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.purchase.dto.*;
import com.hardware.erp.purchase.entity.Purchase;
import com.hardware.erp.purchase.entity.PurchaseItem;
import com.hardware.erp.purchase.entity.PurchasePayment;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class PurchaseMapper {

    public PurchaseResponse toResponse(Purchase purchase, List<PurchasePayment> payments, boolean hasDocument) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getPurchaseNumber(),
                purchase.getSupplier().getId(),
                purchase.getSupplier().getSupplierName(),
                purchase.getSupplier().getMobileNo(),
                purchase.getSupplierBillNumber(),
                purchase.getPurchaseDate(),
                rupees(purchase.getSubtotalPaise()),
                rupees(purchase.getGstAmountPaise()),
                rupees(purchase.getTotalPaise()),
                rupees(purchase.getPaidPaise()),
                rupees(purchase.getBalancePaise()),
                purchase.getStatus(),
                purchase.getRemarks(),
                purchase.getImportedAt() != null,
                purchase.getImportedAt(),
                purchase.getItems().stream()
                        .sorted(Comparator.comparing(PurchaseItem::getId))
                        .map(this::toResponse)
                        .toList(),
                payments.stream().map(this::toResponse).toList(),
                hasDocument,
                purchase.getCreatedAt());
    }

    public PurchaseSummaryResponse toSummary(Purchase purchase) {
        return new PurchaseSummaryResponse(
                purchase.getId(),
                purchase.getPurchaseNumber(),
                purchase.getSupplier().getSupplierName(),
                purchase.getSupplierBillNumber(),
                purchase.getPurchaseDate(),
                rupees(purchase.getTotalPaise()),
                rupees(purchase.getBalancePaise()),
                purchase.getStatus(),
                purchase.getImportedAt() != null);
    }

    public PurchaseItemResponse toResponse(PurchaseItem item) {
        return new PurchaseItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getQuantity(),
                item.getUnit(),
                rupees(item.getUnitPricePaise()),
                item.getGstRatePercent().toPlainString(),
                rupees(item.getLineSubtotalPaise()),
                rupees(item.getLineGstPaise()),
                rupees(item.getLineTotalPaise()));
    }

    public PurchasePaymentResponse toResponse(PurchasePayment payment) {
        return new PurchasePaymentResponse(
                payment.getId(),
                rupees(payment.getAmountPaise()),
                payment.getPaymentMethod(),
                payment.getPaymentDate(),
                payment.getNotes());
    }

    private String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise == null ? 0L : paise);
    }
}
