package com.hardware.erp.branch.repository;

import com.hardware.erp.branch.entity.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    Page<StockTransfer> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    Optional<StockTransfer> findByIdAndTenantId(Long id, Long tenantId);
}
