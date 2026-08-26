package com.hardware.erp.invoice.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.coupon.entity.Coupon;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantBankAccount;
import com.hardware.erp.tenant.entity.TenantBankAccountQr;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * paidPaise/balancePaise/status are always derived from the linked Payment
 * rows by InvoiceServiceImpl, never set directly from a request body - the
 * same "never trust client-supplied authoritative state" principle CR-016
 * applies to tenant_id.
 */
@Entity
@Table(name = "invoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "invoice_number", nullable = false, length = 30)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

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
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Column(name = "remarks", length = 500)
    private String remarks;

    /** All optional - most retail sales are handed over in-store, no transporter involved. */
    @Column(name = "transport_mode", length = 50)
    private String transportMode;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    /** Nullable - most invoices carry no coupon (CR-028). subtotalPaise/gstAmountPaise already reflect the discount; this is only for "what was applied and why". */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @Column(name = "discount_paise", nullable = false)
    @Builder.Default
    private Long discountPaise = 0L;

    /**
     * Both nullable and live-resolved (CR-036) - never snapshotted. When
     * null, InvoicePdfService falls back to the tenant's single default
     * bank fields, exactly the pre-CR-036 behaviour. ON DELETE SET NULL at
     * the schema level means deleting an account/QR never breaks an
     * already-issued invoice.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private TenantBankAccount bankAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_qr_id")
    private TenantBankAccountQr bankAccountQr;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    public void recalculate() {
        this.balancePaise = totalPaise - paidPaise;
        if (paidPaise <= 0) {
            this.status = InvoiceStatus.UNPAID;
        } else if (paidPaise >= totalPaise) {
            this.status = InvoiceStatus.PAID;
        } else {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        }
    }
}
