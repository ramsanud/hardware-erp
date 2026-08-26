package com.hardware.erp.project.entity;

import com.hardware.erp.auth.entity.User;
import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A job of work for a customer (Modular Kitchen, Iron Gate, Rooftop Sheet,
 * ...). See V18 migration for the status/outcome design.
 *
 * Depends On:
 *   Customer Module - every project belongs to a customer
 *   Auth Module - manager_user_id is an optional app_user reference
 */
@Entity
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "project_number", nullable = false, length = 20)
    private String projectNumber;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_type_id", nullable = false)
    private WorkType workType;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "site_address", length = 500)
    private String siteAddress;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "expected_completion_date")
    private LocalDate expectedCompletionDate;

    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;

    @Column(name = "customer_deadline")
    private LocalDate customerDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.UPCOMING;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20)
    private ProjectOutcome outcome;

    /**
     * The agreed contract value - profit is calculated against this, never
     * against payments received to date, since revenue is earned as the
     * work completes, not as cash arrives (see ProjectServiceImpl).
     */
    @Column(name = "project_value_paise", nullable = false)
    @Builder.Default
    private Long projectValuePaise = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_user_id")
    private User managerUser;

    @Column(name = "notes", length = 2000)
    private String notes;

    public boolean isOverdue() {
        return (status == ProjectStatus.UPCOMING || status == ProjectStatus.IN_PROGRESS)
                && customerDeadline != null && customerDeadline.isBefore(LocalDate.now());
    }
}
