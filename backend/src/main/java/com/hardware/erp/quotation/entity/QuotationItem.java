package com.hardware.erp.quotation.entity;

import com.hardware.erp.common.util.LineDiscount;
import com.hardware.erp.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * unitPricePaise/gstRatePercent are snapshots at quote time (mirrors
 * InvoiceItem, PROJECT_SKILLS #17) - but unlike an invoice line, these are
 * only ever shown, never charged. Convert-to-Invoice re-reads the current
 * product price rather than trusting this snapshot (CR-022) - a quotation
 * is not a price lock.
 */
@Entity
@Table(name = "quotation_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quotation_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

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


    /**
     * CR-047. discountAmountPaise is authoritative for BOTH types - the
     * entered amount when AMOUNT, the computed amount when PERCENTAGE - so
     * totals are never re-derived from a percentage at read time and a stored
     * document cannot re-price itself. discountPercent records the owner's
     * intent so a "10%" line still prints as 10% and converts as 10%.
     */
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

    @Column(name = "line_subtotal_paise", nullable = false)
    private Long lineSubtotalPaise;

    @Column(name = "line_gst_paise", nullable = false)
    private Long lineGstPaise;

    @Column(name = "line_total_paise", nullable = false)
    private Long lineTotalPaise;
}
