package com.hardware.erp.salesorder.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.common.util.LineDiscount;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A confirmed customer commitment, sitting between Quotation (a price
 * offer, CR-022) and Delivery Challan/Invoice (goods actually moving).
 * Like Quotation, it never moves stock and posts nothing on its own -
 * see V35's header comment for the full reasoning.
 *
 * Converts exactly once, to exactly one of a Delivery Challan or an
 * Invoice - convertedDeliveryChallanId and convertedInvoiceId are
 * mutually exclusive, mirroring Quotation.convertedInvoiceId.
 */
@Entity
@Table(name = "sales_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sales_order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "sales_order_number", nullable = false, length = 30)
    private String salesOrderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    /** Informational only - nothing gates on this, unlike quotation.validUntil. */
    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    /** CR-052. Same three-field shape as Quotation's whole-document discount (CR-049). */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    @Builder.Default
    private LineDiscount.Type discountType = LineDiscount.Type.NONE;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal discountPercent = java.math.BigDecimal.ZERO;

    @Column(name = "discount_paise", nullable = false)
    @Builder.Default
    private Long discountPaise = 0L;

    /** NET of both the line discounts and the order discount - the taxable amount. */
    @Column(name = "subtotal_paise", nullable = false)
    private Long subtotalPaise;

    @Column(name = "gst_amount_paise", nullable = false)
    private Long gstAmountPaise;

    @Column(name = "total_paise", nullable = false)
    private Long totalPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SalesOrderStatus status = SalesOrderStatus.DRAFT;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "converted_delivery_challan_id")
    private Long convertedDeliveryChallanId;

    @Column(name = "converted_invoice_id")
    private Long convertedInvoiceId;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SalesOrderItem> items = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    public boolean canConvert() {
        return status == SalesOrderStatus.DRAFT || status == SalesOrderStatus.CONFIRMED;
    }
}
