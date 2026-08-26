package com.hardware.erp.project.dto;

import java.math.BigDecimal;

public record RooftopCalculatorResponse(
        BigDecimal requiredAreaSqMeters,
        BigDecimal areaAfterOverlapAndWastageSqMeters,
        BigDecimal sheetAreaSqMeters,
        int calculatedSheetQuantity
) {}
