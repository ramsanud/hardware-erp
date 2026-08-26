package com.hardware.erp.tenant.repository;

import com.hardware.erp.tenant.entity.TenantBankAccountQr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantBankAccountQrRepository extends JpaRepository<TenantBankAccountQr, Long> {

    @Query("select q from TenantBankAccountQr q where q.id = :id and q.bankAccount.tenant.id = :tenantId")
    Optional<TenantBankAccountQr> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<TenantBankAccountQr> findByBankAccountId(Long bankAccountId);
}
