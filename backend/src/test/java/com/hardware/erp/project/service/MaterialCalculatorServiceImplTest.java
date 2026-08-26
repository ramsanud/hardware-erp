package com.hardware.erp.project.service;

import com.hardware.erp.project.dto.RooftopCalculatorRequest;
import com.hardware.erp.project.dto.RooftopCalculatorResponse;
import com.hardware.erp.project.service.impl.MaterialCalculatorServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialCalculatorServiceImplTest {

    private final MaterialCalculatorServiceImpl service = new MaterialCalculatorServiceImpl();

    @Test
    @DisplayName("required area + overlap% + wastage%, divided by one sheet's area, rounded up")
    void appliesOverlapAndWastageThenRoundsUp() {
        // 3m x 4m roof = 12 sq m; 1m x 2m sheet = 2 sq m; 10% overlap, 5% wastage
        // -> 12 * 1.10 * 1.05 = 13.86 sq m needed -> 13.86 / 2 = 6.93 -> rounds up to 7 sheets
        RooftopCalculatorRequest request = new RooftopCalculatorRequest(
                BigDecimal.valueOf(3), BigDecimal.valueOf(4),
                BigDecimal.valueOf(1), BigDecimal.valueOf(2),
                BigDecimal.valueOf(10), BigDecimal.valueOf(5));

        RooftopCalculatorResponse result = service.rooftopSheets(request);

        assertThat(result.requiredAreaSqMeters()).isEqualByComparingTo("12");
        assertThat(result.areaAfterOverlapAndWastageSqMeters()).isEqualByComparingTo("13.8600");
        assertThat(result.calculatedSheetQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("no overlap or wastage given (null) is treated as zero, not an error")
    void nullOverlapAndWastageTreatedAsZero() {
        RooftopCalculatorRequest request = new RooftopCalculatorRequest(
                BigDecimal.valueOf(2), BigDecimal.valueOf(2),
                BigDecimal.valueOf(2), BigDecimal.valueOf(1),
                null, null);

        RooftopCalculatorResponse result = service.rooftopSheets(request);

        assertThat(result.calculatedSheetQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("a non-exact division always rounds up - a shop can't buy 2.5 sheets")
    void fractionalResultRoundsUpToWholeSheet() {
        RooftopCalculatorRequest request = new RooftopCalculatorRequest(
                BigDecimal.valueOf(1), BigDecimal.valueOf(1),
                BigDecimal.valueOf(1), BigDecimal.valueOf(0.4),
                null, null);

        RooftopCalculatorResponse result = service.rooftopSheets(request);

        assertThat(result.calculatedSheetQuantity()).isEqualTo(3);
    }
}
