package com.hardware.erp.document.repository;

import com.hardware.erp.document.entity.ReportJob;
import com.hardware.erp.document.entity.ReportJobFormat;
import com.hardware.erp.document.entity.ReportJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/** CR-101. Every read is tenant-scoped by the caller; there is no unscoped finder to misuse. */
public interface ReportJobRepository extends JpaRepository<ReportJob, Long> {

    /** Download path only - the one place file_data is actually needed, one row at a time. */
    Optional<ReportJob> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * Interface projection: Spring Data selects these columns only, so a
     * status-list page never pulls file_data (potentially megabytes per row)
     * across the wire just to show a spinner or a tick. Both methods below
     * are explicit @Query, not derived, so their names never collide with
     * {@link #findByIdAndTenantId} - Java does not allow overloading on
     * return type alone.
     */
    interface Summary {
        Long getId();
        String getReportType();
        ReportJobFormat getFormat();
        ReportJobStatus getStatus();
        String getFileName();
        Integer getFileSizeBytes();
        String getErrorMessage();
        LocalDateTime getCreatedAt();
        LocalDateTime getCompletedAt();
    }

    @Query("select j from ReportJob j where j.tenantId = :tenantId order by j.createdAt desc")
    Page<Summary> pageSummaries(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query("select j from ReportJob j where j.id = :id and j.tenantId = :tenantId")
    Optional<Summary> findSummary(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /** ReportJobCleanupJob (CR-101): every tenant, any status, older than the retention cutoff. */
    @Modifying
    @Transactional
    @Query("delete from ReportJob j where j.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
