package com.hardware.erp.common.idempotency;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Same two-step "insert if absent, then lock" pattern as
 * DocumentSequenceRepository (CR-041), and for the identical reason: two
 * concurrent requests reaching a not-yet-existing row must not both insert
 * it, and whichever loses that race must then block on the winner's lock
 * rather than proceed independently.
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IdempotencyRecord r where r.tenantId = :tenantId and r.idempotencyKey = :key")
    Optional<IdempotencyRecord> lockForUpdate(@Param("tenantId") Long tenantId, @Param("key") String key);

    /**
     * response_status = 0 is the in-flight placeholder - never a real HTTP
     * status - so IdempotencyServiceImpl can tell "someone is executing this
     * right now" apart from "here is the finished result".
     */
    @Modifying
    @Query(value = """
           insert into idempotency_record
               (tenant_id, idempotency_key, operation, request_hash, response_status, response_body, created_at)
           values (:tenantId, :key, :operation, :requestHash, 0, '', now())
           on conflict (tenant_id, idempotency_key) do nothing
           """, nativeQuery = true)
    void insertPlaceholderIfAbsent(@Param("tenantId") Long tenantId, @Param("key") String key,
                                   @Param("operation") String operation, @Param("requestHash") String requestHash);
}
