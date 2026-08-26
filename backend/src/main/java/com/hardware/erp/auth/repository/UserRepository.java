package com.hardware.erp.auth.repository;

import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * mobile_no and email stay globally unique across every tenant, by
     * deliberate design (CR-016): login accepts a single `identifier` field
     * with no tenant selector, so it must resolve to exactly one user
     * platform-wide. These two checks are therefore never tenant-scoped.
     */
    boolean existsByMobileNo(String mobileNo);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByMobileNoAndIdNot(String mobileNo, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** employee_code is an internal HR code, unique per shop, not platform-wide (CR-016). */
    boolean existsByEmployeeCodeAndTenantId(String employeeCode, Long tenantId);

    boolean existsByEmployeeCodeAndTenantIdAndIdNot(String employeeCode, Long tenantId, Long id);

    /**
     * Login accepts mobile or email in the single `identifier` field, because
     * counter staff do not remember which one they were registered with.
     * Not tenant-scoped - see the class comment above.
     */
    @Query("select u from User u where u.mobileNo = :identifier " +
           "or lower(u.email) = lower(:identifier)")
    Optional<User> findByIdentifier(@Param("identifier") String identifier);

    /**
     * Every lookup by primary key that could act on another tenant's row
     * must go through this, not findById - a user id alone does not prove
     * the row belongs to the caller's shop (CR-016).
     */
    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    // :search is cast explicitly at every use. Left as a bare
    // ":param is null" check, Hibernate infers its bind type from that
    // context alone and sends it to PostgreSQL as bytea, which then fails
    // the later lower()/concat() calls with "function lower(bytea) does not
    // exist" - see BUG-SUP-004 in BUG_REGISTRY.md (found in the Supplier
    // module first; this query has the identical shape).
    @Query("""
           select u from User u
           where u.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(u.fullName) like lower(concat('%', cast(:search as string), '%'))
                  or u.mobileNo like concat('%', cast(:search as string), '%')
                  or lower(u.email) like lower(concat('%', cast(:search as string), '%'))
                  or lower(u.employeeCode) like lower(concat('%', cast(:search as string), '%')))
             and (:status is null or u.status = :status)
             and (:roleId is null or u.role.id = :roleId)
           """)
    Page<User> search(@Param("tenantId") Long tenantId,
                      @Param("search") String search,
                      @Param("status") UserStatus status,
                      @Param("roleId") Long roleId,
                      Pageable pageable);

    long countByRoleId(Long roleId);

    /** Used to invalidate sessions for every holder of a role after a permission change. */
    List<User> findAllByRoleId(Long roleId);

    /**
     * Counts active owners *for one tenant* under a write lock.
     *
     * Without the lock, two admins deactivating the two remaining owners at the
     * same instant would each read a count of 2, each pass the last-owner check,
     * and leave the shop with no owner and no way back in. The lock serialises
     * that window. Scoped by tenant_id - without it, one shop's owner count
     * would be inflated by every other shop's owners, defeating the check
     * entirely (CR-016).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.tenant.id = :tenantId and u.role.code = 'OWNER' " +
           "and u.status = com.hardware.erp.auth.entity.UserStatus.ACTIVE")
    List<User> lockActiveOwners(@Param("tenantId") Long tenantId);

    /**
     * CR-031 - entitlement check when adding a new owner. Deliberately not
     * the locking lockActiveOwners() above: this only needs a read for a
     * limit check, and holding a write lock here would serialise it against
     * that method's own write lock for no reason - two different concerns
     * (min-one-owner on removal vs. max-owners-per-tier on creation) that
     * happen to count the same rows.
     */
    @Query("select count(u) from User u where u.tenant.id = :tenantId and u.role.code = 'OWNER' " +
           "and u.status = com.hardware.erp.auth.entity.UserStatus.ACTIVE")
    long countActiveOwners(@Param("tenantId") Long tenantId);
}
