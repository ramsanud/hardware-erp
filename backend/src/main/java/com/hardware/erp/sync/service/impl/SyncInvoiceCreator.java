package com.hardware.erp.sync.service.impl;

import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * CR-091 Phase 9. A separate bean and a separate REQUIRES_NEW transaction
 * from {@link SyncTransactionExecutor#processOne} - found by
 * {@code OfflineSyncIT.insufficientStockAtSyncTimeIsAConflictNotASilentRetry}.
 *
 * InvoiceServiceImpl.create() is plain {@code @Transactional} (REQUIRED),
 * so calling it directly from processOne() makes it join processOne()'s own
 * REQUIRES_NEW transaction. When it throws (e.g. INSUFFICIENT_STOCK), the
 * Spring transaction interceptor marks THAT SHARED transaction
 * rollback-only before the exception ever reaches processOne()'s catch
 * block - catching it there stops the exception, but not the rollback-only
 * flag. processOne() then goes on to save a SyncTransaction row recording
 * the CONFLICT, which appears to succeed, only for the commit at the end of
 * processOne()'s own transaction to throw UnexpectedRollbackException -
 * discarding that very row and turning one bad line in a sync batch into a
 * 500 for the whole request.
 *
 * Running the attempt here, in its own REQUIRES_NEW transaction, means a
 * failure rolls back only this isolated transaction. processOne() catches
 * the exception in a transaction of its own (or none at all until it saves)
 * and can still record the outcome normally.
 */
@Component
@RequiredArgsConstructor
public class SyncInvoiceCreator {

    private final InvoiceService invoiceService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InvoiceResponse create(InvoiceRequest request) {
        return invoiceService.create(request);
    }
}
