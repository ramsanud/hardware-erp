package com.hardware.erp.tenant.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.supplier.entity.BankAccountNumberConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * One of possibly several accounts a shop can receive payment into (CR-036).
 * The pre-existing single-account fields on {@link Tenant} are untouched and
 * remain the fallback whenever an invoice has no account explicitly selected
 * - see InvoicePdfService.paymentBlock() for the resolution order.
 */
@Entity
@Table(name = "tenant_bank_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantBankAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bank_account_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "bank_name", nullable = false, length = 200)
    private String bankName;

    @Column(name = "account_holder_name", nullable = false, length = 200)
    private String accountHolderName;

    /** Encrypted at rest (CR-018's converter, reused as-is) - always plaintext in Java. */
    @Column(name = "account_number", nullable = false, length = 255)
    @Convert(converter = BankAccountNumberConverter.class)
    private String accountNumber;

    @Column(name = "ifsc_code", nullable = false, length = 11)
    private String ifscCode;

    @Column(name = "upi_id", length = 100)
    private String upiId;

    @Column(name = "default_account", nullable = false)
    @Builder.Default
    private boolean defaultAccount = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TenantBankAccountStatus status = TenantBankAccountStatus.ACTIVE;

    @OneToMany(mappedBy = "bankAccount", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TenantBankAccountQr> qrCodes = new ArrayList<>();
}
