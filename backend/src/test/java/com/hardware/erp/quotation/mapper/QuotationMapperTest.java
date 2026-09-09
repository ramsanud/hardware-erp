package com.hardware.erp.quotation.mapper;

import com.hardware.erp.common.util.LineDiscount;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.quotation.dto.QuotationItemResponse;
import com.hardware.erp.quotation.entity.QuotationItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for BUG-FE-017 in the second mapper that carried the
 * identical reconstruction. CR-049's quotation-level discount reduces
 * lineSubtotalPaise the same way applyCoupon does on an invoice.
 */
class QuotationMapperTest {

    private final QuotationMapper mapper = new QuotationMapper();

    @Test
    @DisplayName("BUG-FE-017: line gross stays at quantity x unit price after a quotation-level discount")
    void lineGrossSurvivesCouponAllocation() {
        // Same reproduction as the invoice side: 3 x Rs 320 = Rs 960 gross, a
        // 10% line discount (Rs 96), and a document-level discount that then
        // absorbs the remaining Rs 864.
        QuotationItem item = QuotationItem.builder()
                .id(1L)
                .product(Product.builder().id(2L).build())
                .productNameSnapshot("Cement 50kg")
                .quantity(new BigDecimal("3"))
                .unit("BAG")
                .unitPricePaise(32_000L)
                .gstRatePercent(new BigDecimal("18.00"))
                .discountType(LineDiscount.Type.PERCENTAGE)
                .discountPercent(new BigDecimal("10.00"))
                .discountAmountPaise(9_600L)
                // CR-049 allocates the quotation-level discount by REDUCING this.
                .lineSubtotalPaise(0L)
                .lineGstPaise(0L)
                .lineTotalPaise(0L)
                .build();

        QuotationItemResponse response = mapper.toResponse(item);

        // The defect reconstructed gross as lineSubtotalPaise + discountAmountPaise,
        // which here is 0 + 9_600 = Rs 96 - the discount alone.
        assertThat(response.lineGrossDisplay()).isEqualTo("960.00");
        assertThat(response.discountDisplay()).isEqualTo("96.00");
    }
}
