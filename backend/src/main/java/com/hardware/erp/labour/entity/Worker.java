package com.hardware.erp.labour.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

/** A day-wage worker in the shop's own labour force - not a Product/Supplier/Customer, tracked separately for attendance and payroll (CR-036 phase 4). */
@Entity
@Table(name = "worker")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "worker_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "mobile_no", length = 15)
    private String mobileNo;

    @Column(name = "role_title", length = 100)
    private String roleTitle;

    @Column(name = "daily_rate_paise", nullable = false)
    private Long dailyRatePaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WorkerStatus status = WorkerStatus.ACTIVE;
}
