package com.hardware.erp.purchase.entity;

import com.hardware.erp.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * unitPricePaise and gstRatePercent are snapshots at purchase time
 * (mirrors InvoiceItem/PROJECT_SKILLS #17) - a later change to the
 * product master must never rewrite an already-recorded purchase line.
 * Never updated once written.
 */
@Entity
@Table(name = "purchase_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false, length = 255)
    private String productNameSnapshot;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    /** The price actually paid to the supplier for this line - becomes the product's new purchasePricePaise when the caller opts in (see PurchaseServiceImpl). */
    @Column(name = "unit_price_paise", nullable = false)
    private Long unitPricePaise;

    @Column(name = "gst_rate_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRatePercent = BigDecimal.ZERO;

    @Column(name = "line_subtotal_paise", nullable = false)
    private Long lineSubtotalPaise;

    @Column(name = "line_gst_paise", nullable = false)
    private Long lineGstPaise;

    @Column(name = "line_total_paise", nullable = false)
    private Long lineTotalPaise;
}
