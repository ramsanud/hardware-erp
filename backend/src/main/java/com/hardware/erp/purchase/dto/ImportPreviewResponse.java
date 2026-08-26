package com.hardware.erp.purchase.dto;

import java.util.List;

public record ImportPreviewResponse(
        boolean extractionAvailable,
        String message,
        List<ImportRowPreview> rows,
        List<String> warnings,
        int totalRows,
        int rowsWithErrors,
        int newProductCount,
        int existingProductCount
) {}
