package com.hardware.erp.purchase.extraction;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One raw line item as read from the file, before any existing/new
 * matching happens - errors accumulated here are pure data-shape
 * problems (missing name, unparseable number), never a matching
 * decision (that happens one layer up, in PurchaseImportServiceImpl).
 */
@Data
public class ExtractedRow {
    private final int rowNumber;
    private String productName;
    private String brandName;
    private String categoryName;
    private String sku;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPriceRupees;
    private BigDecimal gstRatePercent;
    private final List<String> errors = new ArrayList<>();
}
