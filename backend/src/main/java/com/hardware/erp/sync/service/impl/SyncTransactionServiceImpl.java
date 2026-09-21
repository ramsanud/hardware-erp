package com.hardware.erp.sync.service.impl;

import com.hardware.erp.sync.dto.SyncDtos.SyncBatchRequest;
import com.hardware.erp.sync.dto.SyncDtos.SyncBatchResponse;
import com.hardware.erp.sync.dto.SyncDtos.SyncTransactionRequest;
import com.hardware.erp.sync.dto.SyncDtos.SyncTransactionResult;
import com.hardware.erp.sync.service.SyncTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * CR-091 Phase 9. Only INVOICE transactions exist today (the brief's own
 * recommended initial scope) - creating one replays through the real,
 * already-tested InvoiceService.create(), so an offline sale gets exactly
 * the same stock check, GST split, cost freeze and customer-ledger entry
 * as one entered online. Nothing here duplicates that logic.
 *
 * Each transaction is processed by {@link SyncTransactionExecutor}, a
 * SEPARATE bean called through its Spring proxy (never a same-class call),
 * so its REQUIRES_NEW genuinely opens a new transaction instead of being a
 * self-invocation that silently runs in the caller's own transaction -
 * exactly the trap this codebase's own BUG-BE-002 already named. One
 * invoice's insufficient-stock conflict must not roll back the nine other,
 * unrelated invoices already committed earlier in the same batch.
 */
@Service
@RequiredArgsConstructor
public class SyncTransactionServiceImpl implements SyncTransactionService {

    private final SyncTransactionExecutor executor;

    @Override
    public SyncBatchResponse sync(SyncBatchRequest request) {
        List<SyncTransactionResult> results = new ArrayList<>(request.transactions().size());
        for (SyncTransactionRequest transaction : request.transactions()) {
            results.add(executor.processOne(transaction));
        }
        return new SyncBatchResponse(results);
    }
}
