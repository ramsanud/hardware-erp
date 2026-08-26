package com.hardware.erp.product.repository;

import com.hardware.erp.product.entity.Brand;
import com.hardware.erp.product.entity.BrandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByBrandCodeAndTenantId(String brandCode, Long tenantId);

    boolean existsByBrandCodeAndTenantIdAndIdNot(String brandCode, Long tenantId, Long id);

    boolean existsByBrandNameIgnoreCaseAndTenantId(String brandName, Long tenantId);

    /** Supplier Bill Import existing-brand detection (spec §9) - "Jindal"/"JINDAL"/"jindal" must all resolve to the same row. */
    Optional<Brand> findByBrandNameIgnoreCaseAndTenantId(String brandName, Long tenantId);

    boolean existsByBrandNameIgnoreCaseAndTenantIdAndIdNot(
            String brandName, Long tenantId, Long id);

    List<Brand> findAllByTenantIdOrderByBrandNameAsc(Long tenantId);

    List<Brand> findAllByTenantIdAndStatusOrderByBrandNameAsc(Long tenantId, BrandStatus status);

}
