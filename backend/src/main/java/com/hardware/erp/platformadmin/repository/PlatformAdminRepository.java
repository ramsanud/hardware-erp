package com.hardware.erp.platformadmin.repository;

import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {

    Optional<PlatformAdmin> findByEmailIgnoreCase(String email);

    List<PlatformAdmin> findAllByOrderByCreatedAtDesc();

    /** Security Center - MFA coverage. */
    long countByMfaEnabled(boolean mfaEnabled);
}
