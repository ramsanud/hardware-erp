package com.hardware.erp.sync.service;

import com.hardware.erp.sync.dto.SyncDtos.SyncBatchRequest;
import com.hardware.erp.sync.dto.SyncDtos.SyncBatchResponse;

/**
 * CR-091 Phase 9. The server side of offline sync. One call processes a
 * whole batch; each transaction inside it succeeds, conflicts or fails
 * independently - one bad row in a batch of ten never blocks the other nine.
 */
public interface SyncTransactionService {

    SyncBatchResponse sync(SyncBatchRequest request);
}
