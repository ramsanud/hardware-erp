package com.hardware.erp.platformadmin.repository;

import com.hardware.erp.auth.entity.RevokedReason;
import com.hardware.erp.platformadmin.entity.PlatformAdminRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlatformAdminRefreshTokenRepository extends JpaRepository<PlatformAdminRefreshToken, Long> {

    Optional<PlatformAdminRefreshToken> findByTokenHash(String tokenHash);

    @Query("select t from PlatformAdminRefreshToken t where t.admin.id = :adminId and t.revokedAt is null "
            + "order by t.createdAt desc")
    List<PlatformAdminRefreshToken> findActiveByAdminId(@Param("adminId") Long adminId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PlatformAdminRefreshToken t set t.revokedAt = :now, t.revokedReason = :reason "
            + "where t.admin.id = :adminId and t.revokedAt is null")
    int revokeAllForAdmin(@Param("adminId") Long adminId,
                          @Param("reason") RevokedReason reason,
                          @Param("now") LocalDateTime now);

    /** Security Center dashboard - a real "usable right now" count, not every row ever created. */
    @Query("select count(t) from PlatformAdminRefreshToken t where t.revokedAt is null and t.expiresAt > :now")
    long countActive(@Param("now") LocalDateTime now);
}
