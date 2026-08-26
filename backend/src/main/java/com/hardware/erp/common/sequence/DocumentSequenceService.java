package com.hardware.erp.common.sequence;

public interface DocumentSequenceService {

    /**
     * Allocates and returns the next formatted code for this tenant, e.g.
     * "INV-000419" (CR-041).
     *
     * Runs inside the caller's transaction on purpose. A rolled-back invoice
     * must not consume a number, because GST requires a consecutive serial
     * with no gaps; and holding the row lock until the caller commits is what
     * makes the allocation safe rather than merely narrow. Callers must
     * therefore allocate BEFORE taking any other row lock (stock, coupon) so
     * lock ordering stays consistent across the application.
     */
    String next(DocumentType docType, Long tenantId);
}
