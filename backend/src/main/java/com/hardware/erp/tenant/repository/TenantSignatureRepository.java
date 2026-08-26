package com.hardware.erp.tenant.repository;

import com.hardware.erp.tenant.entity.TenantSignature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSignatureRepository extends JpaRepository<TenantSignature, Long> {
}
