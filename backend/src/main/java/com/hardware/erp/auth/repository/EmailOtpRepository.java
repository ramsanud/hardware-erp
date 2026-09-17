package com.hardware.erp.auth.repository;

import com.hardware.erp.auth.entity.EmailOtp;
import com.hardware.erp.auth.entity.EmailOtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** CR-078. Every lookup is by (email, purpose): a code proves one thing about one address. */
public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    /** The newest code for this address and purpose, consumed or not - the caller decides what its state means. */
    Optional<EmailOtp> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, EmailOtpPurpose purpose);

    /** Issuing a new code must kill any outstanding one, so at most one code is ever live per address and purpose. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailOtp o set o.consumedAt = :now "
         + "where o.email = :email and o.purpose = :purpose and o.consumedAt is null")
    int consumeAllFor(@Param("email") String email, @Param("purpose") EmailOtpPurpose purpose,
                      @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from EmailOtp o where o.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
