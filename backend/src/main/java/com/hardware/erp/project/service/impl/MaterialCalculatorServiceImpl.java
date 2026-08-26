package com.hardware.erp.project.service.impl;

import com.hardware.erp.project.dto.RooftopCalculatorRequest;
import com.hardware.erp.project.dto.RooftopCalculatorResponse;
import com.hardware.erp.project.service.MaterialCalculatorService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one worked formula the request spelled out explicitly (§24-25):
 * required area, inflated by overlap% and wastage%, divided by one sheet's
 * area, rounded up. Deliberately not a claim of one universal engineering
 * formula - the result is always shown next to a user-editable quantity so
 * the shop can override it (damage, an odd corner, whatever the formula
 * can't see). Stateless, no persistence - a project material line is only
 * created when the user actually adds one via ProjectMaterialService.
 */
@Service
public class MaterialCalculatorServiceImpl implements MaterialCalculatorService {

    @Override
    public RooftopCalculatorResponse rooftopSheets(RooftopCalculatorRequest request) {
        BigDecimal requiredArea = request.widthMeters().multiply(request.lengthMeters());
        BigDecimal sheetArea = request.sheetWidthMeters().multiply(request.sheetLengthMeters());

        BigDecimal overlapFactor = BigDecimal.ONE.add(percentToFraction(request.overlapPercent()));
        BigDecimal wastageFactor = BigDecimal.ONE.add(percentToFraction(request.wastagePercent()));
        BigDecimal areaAfterOverlapAndWastage = requiredArea.multiply(overlapFactor).multiply(wastageFactor);

        int sheetQuantity = areaAfterOverlapAndWastage
                .divide(sheetArea, 4, RoundingMode.UP)
                .setScale(0, RoundingMode.UP)
                .intValueExact();

        return new RooftopCalculatorResponse(
                requiredArea.setScale(4, RoundingMode.HALF_UP),
                areaAfterOverlapAndWastage.setScale(4, RoundingMode.HALF_UP),
                sheetArea.setScale(4, RoundingMode.HALF_UP),
                sheetQuantity);
    }

    private BigDecimal percentToFraction(BigDecimal percent) {
        return percent == null ? BigDecimal.ZERO : percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }
}
