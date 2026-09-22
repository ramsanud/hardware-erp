package com.hardware.erp.sync.repository;

import com.hardware.erp.sync.entity.SyncTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SyncTransactionRepository extends JpaRepository<SyncTransaction, Long> {

    Optional<SyncTransaction> findByTenantIdAndClientUuid(Long tenantId, UUID clientUuid);
}
