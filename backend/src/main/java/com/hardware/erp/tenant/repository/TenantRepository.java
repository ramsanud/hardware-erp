package com.hardware.erp.tenant.repository;

import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** CR-053 backlog item 5. Only active tenants with at least one reminder switched on - the daily job has nothing to do for anyone else. */
    @Query("select t from Tenant t where t.status = 'ACTIVE' "
            + "and (t.paymentDueReminderEnabled = true or t.lowStockAlertEnabled = true)")
    List<Tenant> findActiveWithAnyReminderEnabled();

    // ---------------------------------------------------------------
    // Platform Admin Console (CR-054 phase 2) - Tenant Management.
    // ---------------------------------------------------------------

    long countByStatus(TenantStatus status);

    long countBySubscriptionTier(SubscriptionTier tier);

    /** Overview "new tenants" trend - a genuine month-over-month figure, computed from real createdAt rows, never a placeholder. */
    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("""
           select t from Tenant t
           where (cast(:search as string) is null
                  or lower(t.name) like lower(concat('%', cast(:search as string), '%'))
                  or lower(t.slug) like lower(concat('%', cast(:search as string), '%'))
                  or lower(t.email) like lower(concat('%', cast(:search as string), '%'))
                  or t.phone like concat('%', cast(:search as string), '%'))
             and (:status is null or t.status = :status)
             and (:tier is null or t.subscriptionTier = :tier)
           order by t.createdAt desc, t.id desc
           """)
    Page<Tenant> search(@Param("search") String search,
                        @Param("status") TenantStatus status,
                        @Param("tier") SubscriptionTier tier,
                        Pageable pageable);
}
