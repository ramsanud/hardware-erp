package com.hardware.erp.auth.repository;

import com.hardware.erp.auth.entity.UserBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBackupCodeRepository extends JpaRepository<UserBackupCode, Long> {

    List<UserBackupCode> findByUserId(Long userId);

    Optional<UserBackupCode> findByUserIdAndCodeHash(Long userId, String codeHash);

    @Modifying
    @Query("delete from UserBackupCode c where c.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
