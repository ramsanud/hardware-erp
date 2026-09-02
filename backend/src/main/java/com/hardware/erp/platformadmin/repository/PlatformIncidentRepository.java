package com.hardware.erp.platformadmin.repository;

import com.hardware.erp.platformadmin.entity.IncidentSeverity;
import com.hardware.erp.platformadmin.entity.PlatformIncident;
import com.hardware.erp.platformadmin.entity.PlatformIncidentStatus;
import com.hardware.erp.platformadmin.entity.PlatformService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PlatformIncidentRepository extends JpaRepository<PlatformIncident, Long> {

    /** The dedup lookup recordFailure() uses before deciding to open a new incident vs bump an existing one. */
    Optional<PlatformIncident> findFirstByServiceAndStatusIn(
            PlatformService service, java.util.List<PlatformIncidentStatus> statuses);

    @Query("""
           select i from PlatformIncident i
           where (:service is null or i.service = :service)
             and (:status is null or i.status = :status)
             and (:severity is null or i.severity = :severity)
             and (cast(:fromDate as timestamp) is null or i.lastSeen >= :fromDate)
             and (cast(:toDate as timestamp) is null or i.lastSeen <= :toDate)
           order by i.lastSeen desc
           """)
    Page<PlatformIncident> search(@Param("service") PlatformService service,
                                   @Param("status") PlatformIncidentStatus status,
                                   @Param("severity") IncidentSeverity severity,
                                   @Param("fromDate") LocalDateTime fromDate,
                                   @Param("toDate") LocalDateTime toDate,
                                   Pageable pageable);

    long countByStatusIn(java.util.List<PlatformIncidentStatus> statuses);
}
