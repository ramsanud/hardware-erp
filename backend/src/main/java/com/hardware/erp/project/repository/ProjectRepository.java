package com.hardware.erp.project.repository;

import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
           select p from Project p
           where p.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(p.projectName) like lower(concat('%', cast(:search as string), '%'))
                  or lower(p.projectNumber) like lower(concat('%', cast(:search as string), '%'))
                  or lower(p.customer.customerName) like lower(concat('%', cast(:search as string), '%')))
             and (:status is null or p.status = :status)
             and (:customerId is null or p.customer.id = :customerId)
           """)
    Page<Project> search(@Param("tenantId") Long tenantId, @Param("search") String search,
                         @Param("status") ProjectStatus status, @Param("customerId") Long customerId,
                         Pageable pageable);

    List<Project> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(Long tenantId, Long customerId);

    long countByTenantIdAndStatus(Long tenantId, ProjectStatus status);

}
