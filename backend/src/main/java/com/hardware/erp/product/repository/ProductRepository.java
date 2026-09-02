package com.hardware.erp.product.repository;

import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);

    /** Batch lookup for a client-supplied id list (e.g. a coupon's product restriction) - every id must resolve within this, or it doesn't belong to the caller's tenant (CR-016). */
    List<Product> findAllByIdInAndTenantId(List<Long> ids, Long tenantId);

    /** Supplier Bill Import existing-product detection (spec §8) - exact code/name match, case-insensitive since a bill's printed casing is never reliable. */
    Optional<Product> findByTenantIdAndProductCodeIgnoreCase(Long tenantId, String productCode);

    Optional<Product> findByTenantIdAndProductNameIgnoreCase(Long tenantId, String productName);

    boolean existsByProductCodeAndTenantId(String productCode, Long tenantId);

    boolean existsByProductCodeAndTenantIdAndIdNot(String productCode, Long tenantId, Long id);

    boolean existsByProductNameIgnoreCaseAndTenantId(String productName, Long tenantId);

    boolean existsByProductNameIgnoreCaseAndTenantIdAndIdNot(
            String productName, Long tenantId, Long id);

    boolean existsByBarcodeAndTenantId(String barcode, Long tenantId);

    boolean existsByBarcodeAndTenantIdAndIdNot(String barcode, Long tenantId, Long id);

    /** Category and brand cannot be deleted while a product still references them. */
    long countByCategoryIdAndTenantId(Long categoryId, Long tenantId);

    long countByBrandIdAndTenantId(Long brandId, Long tenantId);

    /**
     * Counter staff search by whatever identifies the item fastest: a
     * scanned barcode, the code on the manufacturer's box, or the model
     * number - never by name alone (FEATURE_REGISTRY Module 6). :search is
     * cast explicitly at every use for the same reason as
     * SupplierRepository.search - see BUG-SUP-004 in BUG_REGISTRY.md.
     */
    @Query("""
           select p from Product p
           where p.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(p.productName) like lower(concat('%', cast(:search as string), '%'))
                  or lower(p.productCode) like lower(concat('%', cast(:search as string), '%'))
                  or lower(coalesce(p.barcode, '')) like lower(concat('%', cast(:search as string), '%'))
                  or lower(coalesce(p.manufacturerCode, '')) like lower(concat('%', cast(:search as string), '%'))
                  or lower(coalesce(p.modelNo, '')) like lower(concat('%', cast(:search as string), '%')))
             and (:status is null or p.status = :status)
             and (:categoryId is null or p.category.id = :categoryId)
             and (:brandId is null or p.brand.id = :brandId)
           """)
    Page<Product> search(@Param("tenantId") Long tenantId,
                         @Param("search") String search,
                         @Param("status") ProductStatus status,
                         @Param("categoryId") Long categoryId,
                         @Param("brandId") Long brandId,
                         Pageable pageable);


    long countByStatusAndTenantId(ProductStatus status, Long tenantId);

    /** Platform Admin tenant usage summary. */
    long countByTenantId(Long tenantId);
}
