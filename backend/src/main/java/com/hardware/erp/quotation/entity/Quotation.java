package com.hardware.erp.quotation.entity;

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
 * A price document, not a financial record (CR-022) - a quotation never
 * moves stock and posts nothing until it is converted. "Expired" is never
 * persisted automatically (no scheduled job); isExpired() is computed from
 * validUntil at read time, and Convert-to-Invoice is the one place that
 * enforces it.
 */
@Entity
@Table(name = "quotation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quotation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quotation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "quotation_number", nullable = false, length = 30)
    private String quotationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "quotation_date", nullable = false)
    private LocalDate quotationDate;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    /**
     * CR-049. Same three-field shape as QuotationItem: type and percent record
     * the owner's intent, discountPaise is always the authoritative money
     * figure. discountPaise reuses the column V16 created for coupons and
     * never populated.
     */
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

    /** NET of both the line discounts and the quotation discount - the taxable amount. */
    @Column(name = "subtotal_paise", nullable = false)
    private Long subtotalPaise;

    @Column(name = "gst_amount_paise", nullable = false)
    private Long gstAmountPaise;

    @Column(name = "total_paise", nullable = false)
    private Long totalPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private QuotationStatus status = QuotationStatus.DRAFT;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "converted_invoice_id")
    private Long convertedInvoiceId;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<QuotationItem> items = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    public boolean isExpired() {
        return validUntil.isBefore(LocalDate.now());
    }

    public boolean canConvert() {
        return (status == QuotationStatus.DRAFT || status == QuotationStatus.SENT
                || status == QuotationStatus.ACCEPTED) && !isExpired();
    }
}
