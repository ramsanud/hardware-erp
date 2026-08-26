package com.hardware.erp.common.sequence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    /**
     * PESSIMISTIC_WRITE issues SELECT ... FOR UPDATE. Every concurrent
     * allocator for the same tenant and document type queues here, which is
     * precisely the point - the previous MAX+1 approach had no such gate.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.tenantId = :tenantId and s.docType = :docType")
    Optional<DocumentSequence> lockForUpdate(@Param("tenantId") Long tenantId,
                                             @Param("docType") DocumentType docType);

    /**
     * Creates the row for a tenant that has never issued this document type.
     *
     * ON CONFLICT DO NOTHING rather than a find-then-save: two threads can
     * reach a missing row simultaneously, and the loser must not blow up on
     * uk_document_sequence. Whichever thread wins, both then re-read and lock
     * the same row. PostgreSQL-specific by design - this project is
     * PostgreSQL only.
     */
    @Modifying
    @Query(value = """
           insert into document_sequence (tenant_id, doc_type, next_value, created_at)
           values (:tenantId, :docType, 1, now())
           on conflict (tenant_id, doc_type) do nothing
           """, nativeQuery = true)
    void insertIfAbsent(@Param("tenantId") Long tenantId, @Param("docType") String docType);
}
