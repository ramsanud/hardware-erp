package com.hardware.erp.product.dto;

import java.util.List;

public record ProductImportPreviewResponse(
        boolean extractionAvailable,
        String message,
        List<ProductImportRowPreview> rows,
        List<String> warnings,
        int totalRows,
        int rowsWithErrors
) {}
