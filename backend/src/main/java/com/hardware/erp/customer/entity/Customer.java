package com.hardware.erp.customer.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

/**
 * Minimal by design (CR-021): backs the Invoice flow only. Created or
 * matched by mobile number from InvoiceServiceImpl - there is no
 * CustomerController and no management UI yet. Shaped to be
 * forward-compatible with the real Module 5 (same tenant-scoping and
 * code-generation pattern as Supplier/Product), not a throwaway stub.
 */
@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "customer_code", nullable = false, length = 30)
    private String customerCode;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "mobile_no", nullable = false, length = 15)
    private String mobileNo;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "gst_no", length = 15)
    private String gstNo;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    /** Compared against the tenant's own state_code to split GST into CGST+SGST vs IGST on the invoice PDF (CR-022). */
    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "pincode", length = 6)
    private String pincode;

    @Column(name = "credit_limit_paise", nullable = false)
    @Builder.Default
    private Long creditLimitPaise = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    public boolean isActive() {
        return status == CustomerStatus.ACTIVE;
    }
}
