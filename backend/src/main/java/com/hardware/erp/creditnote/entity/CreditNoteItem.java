package com.hardware.erp.creditnote.entity;

import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * invoiceItem, not productId, is what this line returns against - see
 * V37's header comment for why (BUG-FE-021 was exactly the class of
 * defect a product-keyed line invites).
 *
 * unitPricePaise/lineSubtotalPaise are the EFFECTIVE per-unit rate/value
 * the customer was actually charged on that original line (its net after
 * discount, divided across its quantity) - never the product's gross
 * price - so a credit note can never refund more than was collected.
 */
@Entity
@Table(name = "credit_note_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditNoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credit_note_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false)
    private CreditNote creditNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_item_id", nullable = false)
    private InvoiceItem invoiceItem;

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

    @Column(name = "line_subtotal_paise", nullable = false)
    private Long lineSubtotalPaise;

    @Column(name = "line_gst_paise", nullable = false)
    private Long lineGstPaise;

    @Column(name = "line_total_paise", nullable = false)
    private Long lineTotalPaise;
}
