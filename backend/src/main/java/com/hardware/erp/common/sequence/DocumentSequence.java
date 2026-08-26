package com.hardware.erp.common.sequence;

import com.hardware.erp.common.entity.BaseEntity;
import jakarta.persistence.*;

import lombok.*;

/**
 * One row per tenant per document type, holding the next number to issue
 * (CR-041). Plain {@code tenant_id} rather than a @ManyToOne Tenant: this row
 * is locked FOR UPDATE on the hot path of every document creation, and there
 * is no reason to make that path load a Tenant it never reads.
 *
 * Deliberately no @Version. Optimistic locking is the wrong tool here - two
 * concurrent allocations must queue and both succeed, not fail one with a
 * spurious 409. The pessimistic row lock in
 * {@link DocumentSequenceRepository#lockForUpdate} is what serialises them.
 */
@Entity
@Table(name = "document_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSequence extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_sequence_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private DocumentType docType;

    /** The next number to hand out - not the last one issued. */
    @Column(name = "next_value", nullable = false)
    @Builder.Default
    private Long nextValue = 1L;
}
