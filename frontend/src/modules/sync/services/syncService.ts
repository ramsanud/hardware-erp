import { apiPost } from '@/services/apiClient';
import { ApiError } from '@/shared/types/api';
import { outbox } from '../lib/outbox';
import type { SyncBatchResponse, SyncTransactionRequest } from '../types';

/** Backend: sync/controller/SyncTransactionController.java (CR-091 Phase 9). INVOICE_CREATE. */
export const syncService = {
  upload: (transactions: SyncTransactionRequest[]) =>
    apiPost<SyncBatchResponse>('/v1/sync/transactions', { transactions }),
};

export interface SyncRunSummary {
  attempted: number;
  synced: number;
  conflicts: number;
  failed: number;
}

let inFlight: Promise<SyncRunSummary> | null = null;

/**
 * Pushes every PENDING (and previously FAILED) outbox row to the server in
 * one batch and records each answer. Idempotent by client UUID on the
 * server, so calling this twice - or a retry after a lost response - can
 * never create a second invoice. Serialised: a second call while one is in
 * flight simply joins it.
 */
export function syncOutbox(): Promise<SyncRunSummary> {
  if (inFlight) return inFlight;
  inFlight = (async () => {
    const summary: SyncRunSummary = { attempted: 0, synced: 0, conflicts: 0, failed: 0 };
    const items = (await outbox.list()).filter((item) => item.status === 'PENDING' || item.status === 'FAILED');
    if (items.length === 0) return summary;
    summary.attempted = items.length;

    const transactions: SyncTransactionRequest[] = items.map(({
      clientUuid, deviceId, transactionType, clientCreatedAt, payload,
    }) => ({ clientUuid, deviceId, transactionType, clientCreatedAt, payload }));

    try {
      const response = await syncService.upload(transactions);
      for (const result of response.results) {
        await outbox.applyResult(result);
        if (result.status === 'SYNCED') summary.synced += 1;
        else if (result.status === 'CONFLICT') summary.conflicts += 1;
        else summary.failed += 1;
      }
    } catch (caught) {
      // Still unreachable, or refused outright (e.g. session expired). Leave
      // the rows PENDING so the next online moment picks them up; only an
      // answer from the server ever moves a row out of the queue.
      const reason = caught instanceof ApiError ? caught.message : 'Could not reach the server';
      if (caught instanceof ApiError && caught.status !== 0 && caught.status !== 408) {
        await outbox.markFailed(transactions.map((t) => t.clientUuid), reason);
        summary.failed = transactions.length;
      }
      throw caught;
    }
    return summary;
  })().finally(() => { inFlight = null; });
  return inFlight;
}
