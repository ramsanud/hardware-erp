package com.hardware.erp.common.sequence.impl;

import com.hardware.erp.common.sequence.DocumentSequence;
import com.hardware.erp.common.sequence.DocumentSequenceRepository;
import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentSequenceServiceImpl implements DocumentSequenceService {

    private final DocumentSequenceRepository repository;

    /**
     * MANDATORY, not REQUIRED: allocating outside a transaction would take a
     * row lock and release it immediately, restoring exactly the race this
     * class exists to remove. Making that a startup-visible contract failure
     * is better than making it a rare production bug.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType docType, Long tenantId) {
        DocumentSequence sequence = repository.lockForUpdate(tenantId, docType)
                .orElseGet(() -> {
                    // First document of this type for this tenant. The insert
                    // is conflict-tolerant, so a thread that loses the race to
                    // create the row still finds it on the second read.
                    repository.insertIfAbsent(tenantId, docType.name());
                    return repository.lockForUpdate(tenantId, docType).orElseThrow(
                            () -> new IllegalStateException(
                                    "document_sequence row missing after insert for tenant "
                                            + tenantId + " / " + docType));
                });

        long allocated = sequence.getNextValue();
        sequence.setNextValue(allocated + 1);
        // No explicit save: the entity is managed, and the row lock is held
        // until the caller's transaction commits or rolls back.
        return docType.format(allocated);
    }
}
