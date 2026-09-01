package com.hardware.erp.deliverychallan.dto;

import java.math.BigDecimal;

public record DeliveryChallanItemResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal quantity,
        String unit,
        String unitPriceDisplay,
        String valueDisplay
) {}
