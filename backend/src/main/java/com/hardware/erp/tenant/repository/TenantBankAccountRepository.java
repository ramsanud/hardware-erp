package com.hardware.erp.tenant.repository;

import com.hardware.erp.tenant.entity.TenantBankAccount;
import com.hardware.erp.tenant.entity.TenantBankAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantBankAccountRepository extends JpaRepository<TenantBankAccount, Long> {

    List<TenantBankAccount> findByTenantIdAndStatusOrderByDefaultAccountDescLabelAsc(
            Long tenantId, TenantBankAccountStatus status);

    Optional<TenantBankAccount> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * Every active account for a duplicate check against the *decrypted*
     * account number - accountNumber is AES-GCM ciphertext at rest (via
     * BankAccountNumberConverter, non-deterministic per encryption), so a
     * database-level "exists by account number" query can never match even
     * for the identical plaintext. The duplicate check runs in the service,
     * over this small, already tenant-scoped list.
     */
    List<TenantBankAccount> findByTenantIdAndStatus(Long tenantId, TenantBankAccountStatus status);
}
