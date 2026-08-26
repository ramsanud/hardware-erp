package com.hardware.erp.auth.repository;

import com.hardware.erp.auth.entity.RefreshToken;
import com.hardware.erp.auth.entity.RevokedReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("select rt from RefreshToken rt where rt.user.id = :userId " +
           "and rt.revokedAt is null and rt.expiresAt > :now order by rt.createdAt desc")
    List<RefreshToken> findActiveSessions(@Param("userId") Long userId,
                                          @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken rt set rt.revokedAt = :now, rt.revokedReason = :reason " +
           "where rt.user.id = :userId and rt.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("reason") RevokedReason reason,
                         @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from RefreshToken rt where rt.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
