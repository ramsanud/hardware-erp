package com.hardware.erp.tenant.entity;

import com.hardware.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** An owner-labelled QR image (e.g. "SBI QR", "GPay") uploaded against one bank account (CR-036). */
@Entity
@Table(name = "tenant_bank_account_qr")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantBankAccountQr extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qr_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private TenantBankAccount bankAccount;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Integer fileSize;

    @Column(name = "image_data", nullable = false)
    private byte[] imageData;
}
