package com.hardware.erp.salesorder.entity;

import com.hardware.erp.common.util.LineDiscount;
import com.hardware.erp.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * unitPricePaise/gstRatePercent are snapshots at order time, exactly like
 * QuotationItem - but Convert-to-Delivery-Challan/Invoice re-reads the
 * current product price rather than trusting this snapshot (mirrors
 * Quotation's CR-022 rule), so this is shown, never charged, until then.
 */
@Entity
@Table(name = "sales_order_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sales_order_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false, length = 255)
    private String productNameSnapshot;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price_paise", nullable = false)
    private Long unitPricePaise;

    @Column(name = "gst_rate_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRatePercent = BigDecimal.ZERO;

    /** CR-052, mirrors QuotationItem's CR-047/CR-050 shape exactly. */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    @Builder.Default
    private LineDiscount.Type discountType = LineDiscount.Type.NONE;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "discount_amount_paise", nullable = false)
    @Builder.Default
    private Long discountAmountPaise = 0L;

    /** Internal labour margin (CR-050) - folded into the rate, never shown to the customer as its own line. */
    @Column(name = "labour_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal labourPercent = BigDecimal.ZERO;

    @Column(name = "labour_amount_paise", nullable = false)
    @Builder.Default
    private Long labourAmountPaise = 0L;

    @Column(name = "line_subtotal_paise", nullable = false)
    private Long lineSubtotalPaise;

    @Column(name = "line_gst_paise", nullable = false)
    private Long lineGstPaise;

    @Column(name = "line_total_paise", nullable = false)
    private Long lineTotalPaise;
}
