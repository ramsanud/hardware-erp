package com.hardware.erp.invoice.mapper;

import com.hardware.erp.common.util.LineDiscount;
import com.hardware.erp.invoice.dto.InvoiceItemResponse;
import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.product.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for BUG-FE-017 - line gross must be computed from the
 * snapshotted quantity and unit price, never reconstructed from a subtotal
 * that a coupon or document-level discount has already reduced.
 */
class InvoiceMapperTest {

    private final InvoiceMapper mapper = new InvoiceMapper();

    @Test
    @DisplayName("BUG-FE-017: line gross stays at quantity x unit price after a coupon has absorbed the line")
    void lineGrossSurvivesCouponAllocation() {
        // The documented reproduction: 3 x Rs 320 = Rs 960 gross, a 10% line
        // discount (Rs 96), and a coupon that then absorbs the Rs 864 net line.
        InvoiceItem item = InvoiceItem.builder()
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
                // applyCoupon allocates the coupon by REDUCING this figure, so it
                // is no longer "gross minus the line discount" once one is used.
                .lineSubtotalPaise(0L)
                .lineGstPaise(0L)
                .lineTotalPaise(0L)
                .build();

        InvoiceItemResponse response = mapper.toResponse(item);

        // The defect reconstructed gross as lineSubtotalPaise + discountAmountPaise,
        // which here is 0 + 9_600 = Rs 96 - the discount alone, exactly the
        // symptom BUG-FE-017 records. Gross is a property of what was ordered and
        // must not move when a coupon changes what is owed.
        assertThat(response.lineGrossDisplay()).isEqualTo("960.00");
        assertThat(response.discountDisplay()).isEqualTo("96.00");
    }
}
