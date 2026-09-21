import type { InvoiceRequest } from '@/modules/invoice/types';
import type { OutboxItem, SyncTransactionResult } from '../types';

/**
 * CR-091 Phase 9. The device's outbox: invoices raised while the server
 * could not be reached, kept in IndexedDB until the server has answered
 * for each one by client UUID. IndexedDB rather than localStorage because
 * a queue of invoice payloads is structured data that can outgrow a
 * string slot, and because it survives a tab crash mid-write.
 *
 * Only the outbox lives here. No access token is ever stored (rule 9) -
 * syncing still needs a live login; this only remembers WHAT to send.
 */
const DB_NAME = 'hardware-erp-outbox';
const STORE = 'transactions';
const DEVICE_KEY = 'erp.deviceId';

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: 'clientUuid' });
        store.createIndex('status', 'status', { unique: false });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('Could not open the outbox'));
  });
}

function tx<T>(mode: IDBTransactionMode, work: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return openDb().then((db) => new Promise<T>((resolve, reject) => {
    const transaction = db.transaction(STORE, mode);
    const request = work(transaction.objectStore(STORE));
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('Outbox operation failed'));
    transaction.oncomplete = () => db.close();
  }));
}

/** A stable per-device id, so the server can tell two phones' uploads apart. Not a credential. */
export function deviceId(): string {
  try {
    const existing = localStorage.getItem(DEVICE_KEY);
    if (existing) return existing;
    const fresh = crypto.randomUUID();
    localStorage.setItem(DEVICE_KEY, fresh);
    return fresh;
  } catch {
    return 'unknown-device';
  }
}

export const outbox = {
  async list(): Promise<OutboxItem[]> {
    const items = await tx<OutboxItem[]>('readonly', (store) => store.getAll());
    return items.sort((a, b) => a.clientCreatedAt.localeCompare(b.clientCreatedAt));
  },

  async pendingCount(): Promise<number> {
    const items = await outbox.list();
    return items.filter((item) => item.status === 'PENDING' || item.status === 'FAILED').length;
  },

  async queueInvoice(payload: InvoiceRequest): Promise<OutboxItem> {
    const item: OutboxItem = {
      clientUuid: crypto.randomUUID(),
      deviceId: deviceId(),
      transactionType: 'INVOICE',
      clientCreatedAt: new Date().toISOString().replace(/Z$/, ''),
      payload,
      status: 'PENDING',
      customerName: payload.customerName,
      itemCount: payload.items.length,
    };
    await tx('readwrite', (store) => store.put(item));
    return item;
  },

  async applyResult(result: SyncTransactionResult): Promise<void> {
    const existing = await tx<OutboxItem | undefined>('readonly', (store) => store.get(result.clientUuid));
    if (!existing) return;
    const updated: OutboxItem = {
      ...existing,
      status: result.status,
      lastAttemptAt: new Date().toISOString(),
      resultReferenceId: result.resultReferenceId ?? null,
      resultReferenceNumber: result.resultReferenceNumber ?? null,
      conflictReason: result.conflictReason ?? result.errorMessage ?? null,
    };
    await tx('readwrite', (store) => store.put(updated));
  },

  async markFailed(clientUuids: string[], reason: string): Promise<void> {
    for (const clientUuid of clientUuids) {
      const existing = await tx<OutboxItem | undefined>('readonly', (store) => store.get(clientUuid));
      if (!existing) continue;
      await tx('readwrite', (store) => store.put({
        ...existing, status: 'FAILED', lastAttemptAt: new Date().toISOString(), conflictReason: reason,
      }));
    }
  },

  async retry(clientUuid: string): Promise<void> {
    const existing = await tx<OutboxItem | undefined>('readonly', (store) => store.get(clientUuid));
    if (!existing) return;
    await tx('readwrite', (store) => store.put({ ...existing, status: 'PENDING', conflictReason: null }));
  },

  async remove(clientUuid: string): Promise<void> {
    await tx('readwrite', (store) => store.delete(clientUuid));
  },

  async clearSynced(): Promise<void> {
    const items = await outbox.list();
    for (const item of items) {
      if (item.status === 'SYNCED') await outbox.remove(item.clientUuid);
    }
  },
};
