package com.hardware.erp.supplier.repository;

import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.supplier.entity.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Soft-deleted rows are excluded automatically: Supplier carries
 * {@code @SQLRestriction("deleted_at is null")}, so every derived query and
 * every JPQL query below is already filtered. Only the two native queries at
 * the bottom bypass it, deliberately.
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndTenantId(Long id, Long tenantId);

    /** Platform Admin tenant data export (CR-057 phase 11). */
    List<Supplier> findByTenantId(Long tenantId);

    boolean existsBySupplierCodeAndTenantId(String supplierCode, Long tenantId);

    boolean existsBySupplierCodeAndTenantIdAndIdNot(String supplierCode, Long tenantId, Long id);

    boolean existsBySupplierNameIgnoreCaseAndTenantId(String supplierName, Long tenantId);

    boolean existsBySupplierNameIgnoreCaseAndTenantIdAndIdNot(String supplierName, Long tenantId, Long id);

    boolean existsByGstNoIgnoreCaseAndTenantId(String gstNo, Long tenantId);

    boolean existsByGstNoIgnoreCaseAndTenantIdAndIdNot(String gstNo, Long tenantId, Long id);

    long countByStatusAndTenantId(SupplierStatus status, Long tenantId);

    /**
     * One search box across name, code, mobile and city - the shop counter
     * types whatever it remembers about the supplier rather than choosing a
     * field first. No ORDER BY: sorting comes from the Pageable, which
     * SupplierController builds from a whitelisted sortBy so an injected
     * sort expression cannot reach the query.
     */
    @Query("""
           select s from Supplier s
           where s.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(s.supplierName) like lower(concat('%', cast(:search as string), '%'))
                  or lower(s.supplierCode) like lower(concat('%', cast(:search as string), '%'))
                  or s.mobileNo like concat('%', cast(:search as string), '%')
                  or lower(s.city) like lower(concat('%', cast(:search as string), '%')))
             and (:status is null or s.status = :status)
             and (cast(:city as string) is null or lower(s.city) = lower(cast(:city as string)))
           """)
    Page<Supplier> search(@Param("tenantId") Long tenantId,
                          @Param("search") String search,
                          @Param("status") SupplierStatus status,
                          @Param("city") String city,
                          Pageable pageable);

    /** Backs the city filter dropdown. Distinct, non-null, alphabetical. */
    @Query("""
           select distinct s.city from Supplier s
           where s.tenant.id = :tenantId and s.city is not null and s.city <> ''
           order by s.city asc
           """)
    List<String> findDistinctCities(@Param("tenantId") Long tenantId);

    /**
     * CR-018 one-time backfill only (SupplierBankAccountEncryptionRunner) -
     * the raw column value, bypassing BankAccountNumberConverter entirely,
     * so legacy plaintext rows (no "ENC:" prefix) can be found and encrypted.
     * Never used by ordinary request-serving code.
     */
    @Query(value = """
           select supplier_id, bank_account_no from supplier
           where bank_account_no is not null and bank_account_no not like 'ENC:%'
           """, nativeQuery = true)
    List<Object[]> findPlaintextBankAccountNumbers();

    /**
     * Writes an already-computed ciphertext string directly, bypassing the
     * JPA converter (and Hibernate's dirty-checking, which would otherwise
     * skip the UPDATE since the Java-level attribute value hasn't changed).
     * CR-018 one-time backfill only - see findPlaintextBankAccountNumbers.
     */
    @Modifying
    @Query(value = "update supplier set bank_account_no = :ciphertext where supplier_id = :id", nativeQuery = true)
    void writeEncryptedBankAccountNumber(@Param("id") Long id, @Param("ciphertext") String ciphertext);
}
