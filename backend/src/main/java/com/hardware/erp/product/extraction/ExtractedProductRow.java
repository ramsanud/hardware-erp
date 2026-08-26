package com.hardware.erp.product.extraction;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** One raw product row as read from an uploaded CSV/Excel file, before any category/brand matching happens (CR-036). */
@Data
public class ExtractedProductRow {
    private final int rowNumber;
    private String productName;
    private String productCode;
    private String categoryName;
    private String brandName;
    private String unit;
    private String hsnCode;
    private BigDecimal gstRatePercent;
    private BigDecimal purchasePriceRupees;
    private BigDecimal sellingPriceRupees;
    private BigDecimal mrpRupees;
    private BigDecimal minimumStock;
    private BigDecimal reorderLevel;
    private final List<String> errors = new ArrayList<>();
}
