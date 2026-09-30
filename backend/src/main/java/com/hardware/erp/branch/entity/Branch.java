package com.hardware.erp.branch.entity;

import com.hardware.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CR-092. One MAIN branch exists for every tenant from V63 onward (the one
 * every pre-existing document was backfilled onto); further branches are a
 * PREMIUM feature. Held by tenant id rather than a Tenant relation so the
 * hot paths that stamp a branch on a document (StockServiceImpl,
 * InvoiceServiceImpl) can carry a plain id.
 */
@Entity
@Table(name = "branch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_code", nullable = false, length = 20)
    private String branchCode;

    @Column(name = "branch_name", nullable = false, length = 150)
    private String branchName;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "pincode", length = 6)
    private String pincode;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BranchStatus status = BranchStatus.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;
}
