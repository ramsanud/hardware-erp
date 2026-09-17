package com.hardware.erp.inventory.repository;

import com.hardware.erp.inventory.entity.LowStockSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** CR-084. Every read is tenant-scoped by the caller; there is no unscoped finder to misuse. */
public interface LowStockSnapshotRepository extends JpaRepository<LowStockSnapshot, Long> {

    List<LowStockSnapshot> findByTenantIdAndTakenOnBetweenOrderByTakenOnAsc(Long tenantId, LocalDate from, LocalDate to);

    Optional<LowStockSnapshot> findByTenantIdAndTakenOn(Long tenantId, LocalDate takenOn);

    /**
     * Upsert on the (tenant, day) key. The job may run twice for one day (a
     * restart, a manual trigger) and the lazy first read may race the job;
     * either way the later count wins and no duplicate row is possible.
     */
    @Modifying
    @Transactional
    @Query(value = """
           insert into low_stock_snapshot (tenant_id, taken_on, low_stock_count, created_at)
           values (:tenantId, :takenOn, :count, current_timestamp)
           on conflict (tenant_id, taken_on)
           do update set low_stock_count = excluded.low_stock_count
           """, nativeQuery = true)
    void upsert(@Param("tenantId") Long tenantId, @Param("takenOn") LocalDate takenOn, @Param("count") int count);
}
