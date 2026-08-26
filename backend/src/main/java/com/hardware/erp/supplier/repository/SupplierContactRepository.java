package com.hardware.erp.supplier.repository;

import com.hardware.erp.supplier.entity.SupplierContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierContactRepository extends JpaRepository<SupplierContact, Long> {

    List<SupplierContact> findBySupplierIdOrderByPrimaryDescContactNameAsc(Long supplierId);

    /**
     * Clears the primary flag before a new one is set. The partial unique index
     * would otherwise reject the second row, and the order of operations inside
     * one transaction matters.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SupplierContact c set c.primary = false where c.supplier.id = :supplierId")
    int clearPrimaryFor(@Param("supplierId") Long supplierId);

    long countBySupplierId(Long supplierId);
}
