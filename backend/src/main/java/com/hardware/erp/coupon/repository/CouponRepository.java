package com.hardware.erp.coupon.repository;

import com.hardware.erp.coupon.entity.Coupon;
import com.hardware.erp.coupon.entity.CouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /** Every lookup by primary key that could act on another tenant's coupon must go through this, never findById (CR-016). */
    Optional<Coupon> findByIdAndTenantId(Long id, Long tenantId);

    /** Case-insensitive - a customer reading a coupon off a flyer may type it in any case. */
    @Query("select c from Coupon c where c.tenant.id = :tenantId and upper(c.code) = upper(:code)")
    Optional<Coupon> findByTenantIdAndCodeIgnoreCase(@Param("tenantId") Long tenantId, @Param("code") String code);

    boolean existsByTenantIdAndCodeIgnoreCase(Long tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNot(Long tenantId, String code, Long id);

    @Query("""
           select c from Coupon c
           where c.tenant.id = :tenantId
             and (cast(:search as string) is null or lower(c.code) like lower(concat('%', cast(:search as string), '%')))
             and (:status is null or c.status = :status)
           order by c.createdAt desc
           """)
    Page<Coupon> search(@Param("tenantId") Long tenantId, @Param("search") String search,
                        @Param("status") CouponStatus status, Pageable pageable);
}
