package com.hardware.erp.purchase.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * paidPaise/balancePaise/status are always derived from the linked
 * PurchasePayment rows via recalculate() - mirrors Invoice exactly
 * (PROJECT_SKILLS's "one source of truth" pattern), never computed twice
 * in two different places.
 */
@Entity
@Table(name = "purchase")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Purchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "purchase_number", nullable = false, length = 30)
    private String purchaseNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "supplier_bill_number", length = 60)
    private String supplierBillNumber;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "subtotal_paise", nullable = false)
    private Long subtotalPaise;

    @Column(name = "gst_amount_paise", nullable = false)
    private Long gstAmountPaise;

    @Column(name = "total_paise", nullable = false)
    private Long totalPaise;

    @Column(name = "paid_paise", nullable = false)
    @Builder.Default
    private Long paidPaise = 0L;

    @Column(name = "balance_paise", nullable = false)
    private Long balancePaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PurchaseStatus status = PurchaseStatus.RECEIVED;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    @Column(name = "imported_by")
    private Long importedBy;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PurchaseItem> items = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * A CANCELLED purchase is never revived by this - cancellation is a
     * one-way business action performed explicitly, not a side effect of
     * a payment. Every other status is fully derived from paid vs. total.
     */
    public void recalculate() {
        this.balancePaise = totalPaise - paidPaise;
        if (status == PurchaseStatus.CANCELLED) {
            return;
        }
        if (paidPaise <= 0) {
            this.status = PurchaseStatus.RECEIVED;
        } else if (paidPaise >= totalPaise) {
            this.status = PurchaseStatus.PAID;
        } else {
            this.status = PurchaseStatus.PARTIALLY_PAID;
        }
    }
}
