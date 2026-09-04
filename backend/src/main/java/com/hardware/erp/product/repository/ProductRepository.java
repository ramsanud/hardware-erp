package com.hardware.erp.product.repository;

import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);

    /** Platform Admin tenant data export (CR-057 phase 11). */
    List<Product> findByTenantId(Long tenantId);

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

    /**
     * CR-058. Native on purpose - see SupplierRepository.findDeletedByTenantId
     * for the full reasoning: Product's own {@code @SQLRestriction("deleted_at
     * is null")} rewrites every JPQL/derived query above, so a soft-deleted row
     * is unreachable through the ORM (BUG-SUP-006). Still tenant-scoped.
     */
    @Query(value = """
           select * from product
           where tenant_id = :tenantId and deleted_at is not null
           order by deleted_at desc
           """, nativeQuery = true)
    List<Product> findDeletedByTenantId(@Param("tenantId") Long tenantId);

    /**
     * CR-058. One atomic guarded statement: the WHERE clause is the
     * authorisation check, so another tenant's row, a row that was never
     * deleted, and a nonexistent id all update zero rows and 404 identically.
     * `version` is incremented by hand because a native update bypasses
     * @Version.
     */
    @Modifying
    @Query(value = """
           update product
           set deleted_at = null, deleted_by = null, status = 'ACTIVE',
               version = version + 1
           where product_id = :id and tenant_id = :tenantId and deleted_at is not null
           """, nativeQuery = true)
    int restoreDeleted(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
