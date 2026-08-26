package com.hardware.erp.supplier.entity;

import com.hardware.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * One named person at a supplier.
 *
 * A supplier typically has several: the owner, the sales contact, the delivery
 * coordinator. A single contact_person field on the supplier row forces staff
 * to overwrite one with another and lose the rest.
 */
@Entity
@Table(name = "supplier_contact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierContact extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_contact_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "contact_name", nullable = false, length = 200)
    private String contactName;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "mobile_no", nullable = false, length = 15)
    private String mobileNo;

    @Column(name = "email", length = 255)
    private String email;

    /**
     * At most one per supplier. Enforced by a partial unique index in V2, not
     * only by the service layer.
     */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;
}
