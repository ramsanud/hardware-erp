package com.hardware.erp.creditnote.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A GST-compliant record of goods returned against an already-issued
 * Invoice - see V37's header comment for the full design, including why
 * this never edits the original invoice's own figures.
 */
@Entity
@Table(name = "credit_note")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditNote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credit_note_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "credit_note_number", nullable = false, length = 30)
    private String creditNoteNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "credit_note_date", nullable = false)
    private LocalDate creditNoteDate;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "subtotal_paise", nullable = false)
    private Long subtotalPaise;

    @Column(name = "gst_amount_paise", nullable = false)
    private Long gstAmountPaise;

    @Column(name = "total_paise", nullable = false)
    private Long totalPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CreditNoteStatus status = CreditNoteStatus.ISSUED;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CreditNoteItem> items = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;
}
