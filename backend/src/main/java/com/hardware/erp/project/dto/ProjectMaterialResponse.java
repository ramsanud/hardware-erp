package com.hardware.erp.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProjectMaterialResponse(
        Long id,
        Long productId,
        String productName,
        String productCode,
        Long supplierId,
        String supplierName,
        BigDecimal quantityRequired,
        BigDecimal quantityEstimated,
        BigDecimal quantityActual,
        BigDecimal quantityWastage,
        String unit,
        String unitPriceDisplay,
        String totalCostDisplay,
        String notes,
        LocalDateTime createdAt
) {}
