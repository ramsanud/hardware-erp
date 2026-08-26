package com.hardware.erp.labour.repository;

import com.hardware.erp.labour.entity.Worker;
import com.hardware.erp.labour.entity.WorkerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    Optional<Worker> findByIdAndTenantId(Long id, Long tenantId);

    List<Worker> findByTenantIdAndStatusOrderByNameAsc(Long tenantId, WorkerStatus status);

    // Deliberately keyed on mobile number, not name: two workers called
    // "Ramesh" on one crew is ordinary and must stay allowed, whereas the same
    // mobile number twice is almost always the same person entered twice.
    boolean existsByTenantIdAndMobileNo(Long tenantId, String mobileNo);

    Optional<Worker> findByTenantIdAndMobileNo(Long tenantId, String mobileNo);

    @Query("""
           select w from Worker w
           where w.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(w.name) like lower(concat('%', cast(:search as string), '%'))
                  or lower(w.mobileNo) like lower(concat('%', cast(:search as string), '%')))
             and (:status is null or w.status = :status)
           order by w.name asc
           """)
    Page<Worker> search(@Param("tenantId") Long tenantId,
                         @Param("search") String search,
                         @Param("status") WorkerStatus status,
                         Pageable pageable);
}
