package com.hardware.erp.legal.repository;

import com.hardware.erp.legal.entity.ConsentType;
import com.hardware.erp.legal.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    /**
     * The user's current position on one consent type. Newest row wins - the
     * table is append-only, so an earlier GRANTED row is history, not state.
     */
    Optional<UserConsent> findFirstByUserIdAndConsentTypeOrderByRecordedAtDescIdDesc(
            Long userId, ConsentType consentType);

    List<UserConsent> findByUserIdOrderByRecordedAtDesc(Long userId);
}
