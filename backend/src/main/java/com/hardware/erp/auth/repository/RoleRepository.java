package com.hardware.erp.auth.repository;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    /** role_code/role_name are unique per tenant, not platform-wide (CR-016). */
    Optional<Role> findByCodeAndTenantId(String code, Long tenantId);

    boolean existsByCodeAndTenantId(String code, Long tenantId);

    boolean existsByCodeAndTenantIdAndIdNot(String code, Long tenantId, Long id);

    boolean existsByNameIgnoreCaseAndTenantId(String name, Long tenantId);

    boolean existsByNameIgnoreCaseAndTenantIdAndIdNot(String name, Long tenantId, Long id);

    /**
     * Every lookup by primary key that could act on another tenant's role
     * must go through this, not findById - a role id alone does not prove
     * it belongs to the caller's shop (CR-016).
     */
    Optional<Role> findByIdAndTenantId(Long id, Long tenantId);

    List<Role> findAllByTenantIdAndStatusOrderByNameAsc(Long tenantId, RoleStatus status);

    /** Fetch join avoids N+1 when the role list screen renders permission counts. */
    @Query("select distinct r from Role r left join fetch r.permissions "
         + "where r.tenant.id = :tenantId order by r.name asc")
    List<Role> findAllWithPermissions(@Param("tenantId") Long tenantId);
}
