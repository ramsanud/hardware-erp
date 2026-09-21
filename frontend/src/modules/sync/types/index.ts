import type { InvoiceRequest } from '@/modules/invoice/types';

/** Backend: sync/dto/SyncDtos.java (CR-091 Phase 9). */

export type SyncTransactionStatus = 'PENDING' | 'SYNCED' | 'FAILED' | 'CONFLICT';

export interface SyncTransactionRequest {
  /** Client-generated, stable across retries - the idempotency key. */
  clientUuid: string;
  deviceId: string;
  transactionType: 'INVOICE';
  clientCreatedAt: string;
  payload: InvoiceRequest;
}

export interface SyncTransactionResult {
  clientUuid: string;
  status: SyncTransactionStatus;
  /** True when this UUID had already been synced - nothing new was created. */
  replay: boolean;
  resultReferenceType?: string | null;
  resultReferenceId?: number | null;
  resultReferenceNumber?: string | null;
  conflictReason?: string | null;
  errorMessage?: string | null;
}

export interface SyncBatchResponse {
  results: SyncTransactionResult[];
}

/** One row of the device's own outbox, held in IndexedDB until the server has answered for it. */
export interface OutboxItem extends SyncTransactionRequest {
  status: SyncTransactionStatus;
  /** A human label built at queue time - the server prices the invoice, so no total is known offline. */
  customerName: string;
  itemCount: number;
  lastAttemptAt?: string | null;
  resultReferenceId?: number | null;
  resultReferenceNumber?: string | null;
  conflictReason?: string | null;
}
