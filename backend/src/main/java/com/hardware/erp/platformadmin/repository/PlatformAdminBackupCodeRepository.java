package com.hardware.erp.platformadmin.repository;

import com.hardware.erp.platformadmin.entity.PlatformAdminBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformAdminBackupCodeRepository extends JpaRepository<PlatformAdminBackupCode, Long> {

    List<PlatformAdminBackupCode> findByAdminId(Long adminId);

    Optional<PlatformAdminBackupCode> findByAdminIdAndCodeHash(Long adminId, String codeHash);

    @Modifying
    @Query("delete from PlatformAdminBackupCode c where c.admin.id = :adminId")
    void deleteAllByAdminId(@Param("adminId") Long adminId);
}
