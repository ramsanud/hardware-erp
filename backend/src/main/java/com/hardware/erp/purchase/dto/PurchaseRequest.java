package com.hardware.erp.purchase.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PurchaseRequest(
        @NotNull Long supplierId,
        @Size(max = 60) String supplierBillNumber,
        @NotNull LocalDate purchaseDate,
        @NotEmpty @Valid List<PurchaseItemRequest> items,
        /** Whether a line's unitPricePaise should overwrite the product's own purchasePricePaise (spec §12 - never silent). Defaults to true for a manually-entered purchase; the Import flow decides this per row explicitly. */
        boolean updateProductCost,
        @PositiveOrZero Long initialPaymentPaise,
        PaymentMethod paymentMethod,
        @Size(max = 500) String remarks
) {}
