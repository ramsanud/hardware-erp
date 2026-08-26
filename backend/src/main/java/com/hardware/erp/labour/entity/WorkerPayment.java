package com.hardware.erp.labour.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.invoice.entity.PaymentMethod;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** A payment made to a worker against wages earned (CR-036 phase 4). Mirrors project_payment's shape. */
@Entity
@Table(name = "worker_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerPayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "worker_payment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Soft cancel, never a hard delete - a payment is a financial record. A CANCELLED row stays in history but is excluded from paid/balance totals (CR-037). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WorkerPaymentStatus status = WorkerPaymentStatus.ACTIVE;
}
