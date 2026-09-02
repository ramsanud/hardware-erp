package com.hardware.erp.tenant.repository;

import com.hardware.erp.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** CR-053 backlog item 5. Only active tenants with at least one reminder switched on - the daily job has nothing to do for anyone else. */
    @Query("select t from Tenant t where t.status = 'ACTIVE' "
            + "and (t.paymentDueReminderEnabled = true or t.lowStockAlertEnabled = true)")
    List<Tenant> findActiveWithAnyReminderEnabled();
}
