package com.hardware.erp.product.repository;

import com.hardware.erp.product.entity.Category;
import com.hardware.erp.product.entity.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByCategoryCodeAndTenantId(String categoryCode, Long tenantId);

    boolean existsByCategoryCodeAndTenantIdAndIdNot(String categoryCode, Long tenantId, Long id);

    boolean existsByCategoryNameIgnoreCaseAndTenantId(String categoryName, Long tenantId);

    /** Supplier Bill Import existing-category detection (spec §10). */
    Optional<Category> findByCategoryNameIgnoreCaseAndTenantId(String categoryName, Long tenantId);

    boolean existsByCategoryNameIgnoreCaseAndTenantIdAndIdNot(
            String categoryName, Long tenantId, Long id);

    List<Category> findAllByTenantIdOrderByCategoryNameAsc(Long tenantId);

    List<Category> findAllByTenantIdAndStatusOrderByCategoryNameAsc(
            Long tenantId, CategoryStatus status);


    long countByParentIdAndTenantId(Long parentId, Long tenantId);
}
