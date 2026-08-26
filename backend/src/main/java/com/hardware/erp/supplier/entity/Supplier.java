package com.hardware.erp.supplier.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A business the shop buys from.
 *
 * Depends On:
 *   Module 1 - created_by / updated_by attribute to app_user
 *
 * Never hard-deleted. From Module 8 onwards every purchase order, goods
 * receipt and supplier payment references supplier_id, and those references
 * must still resolve to a name years later.
 */
@Entity
@Table(name = "supplier")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    private Long id;

    /** LAZY: unlike User/Role this is not resolved on every security check (CR-016). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Unique per tenant, not globally - each shop starts its own SUP-0001 (CR-016). */
    @Column(name = "supplier_code", nullable = false, length = 30)
    private String supplierCode;

    @Column(name = "supplier_name", nullable = false, length = 255)
    private String supplierName;

    @Column(name = "contact_person", length = 200)
    private String contactPerson;

    @Column(name = "mobile_no", nullable = false, length = 15)
    private String mobileNo;

    @Column(name = "alternate_mobile_no", length = 15)
    private String alternateMobileNo;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "gst_no", length = 15)
    private String gstNo;

    @Column(name = "pan_no", length = 10)
    private String panNo;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    /** GST state code, e.g. 33 for Tamil Nadu. Drives CGST/SGST vs IGST later. */
    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "pincode", length = 6)
    private String pincode;

    @Column(name = "payment_terms_days", nullable = false)
    @Builder.Default
    private Integer paymentTermsDays = 0;

    /**
     * Integer paise, never a floating point rupee value. A double accumulates
     * error across a year of purchases and the balance stops tying out.
     */
    @Column(name = "credit_limit_paise", nullable = false)
    @Builder.Default
    private Long creditLimitPaise = 0L;

    @Column(name = "bank_account_name", length = 200)
    private String bankAccountName;

    /**
     * Encrypted at rest (CR-018) - {@link BankAccountNumberConverter} makes
     * this transparent everywhere else in the entity/service layer, which
     * always sees plaintext. Column is 255-wide to hold AES-GCM ciphertext,
     * not the raw account number.
     */
    @Column(name = "bank_account_no", length = 255)
    @Convert(converter = BankAccountNumberConverter.class)
    private String bankAccountNo;

    @Column(name = "bank_ifsc", length = 11)
    private String bankIfsc;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<SupplierContact> contacts = new ArrayList<>();

    public boolean isActive() {
        return status == SupplierStatus.ACTIVE && deletedAt == null;
    }

    /** True when purchase documents may be raised against this supplier. */
    public boolean isPurchasable() {
        return isActive();
    }

    public void softDelete(Long actingUserId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = actingUserId;
        this.status = SupplierStatus.INACTIVE;
    }

    public void addContact(SupplierContact contact) {
        contacts.add(contact);
        contact.setSupplier(this);
    }
}
