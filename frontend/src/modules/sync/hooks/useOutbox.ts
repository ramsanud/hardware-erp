import { useCallback, useEffect, useState } from 'react';
import { outbox } from '../lib/outbox';
import { syncOutbox, type SyncRunSummary } from '../services/syncService';
import type { OutboxItem } from '../types';

/** navigator.onLine plus the browser's own online/offline events. */
export function useOnlineStatus(): boolean {
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine));
  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener('online', up);
    window.addEventListener('offline', down);
    return () => {
      window.removeEventListener('online', up);
      window.removeEventListener('offline', down);
    };
  }, []);
  return online;
}

const OUTBOX_EVENT = 'erp:outbox-changed';

/** Anything that writes the outbox fires this so every listing on screen refreshes. */
export function notifyOutboxChanged(): void {
  window.dispatchEvent(new Event(OUTBOX_EVENT));
}

export function useOutbox() {
  const [items, setItems] = useState<OutboxItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const online = useOnlineStatus();

  const reload = useCallback(async () => {
    try {
      setItems(await outbox.list());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
    window.addEventListener(OUTBOX_EVENT, reload);
    return () => window.removeEventListener(OUTBOX_EVENT, reload);
  }, [reload]);

  const sync = useCallback(async (): Promise<SyncRunSummary> => {
    setSyncing(true);
    try {
      return await syncOutbox();
    } finally {
      setSyncing(false);
      notifyOutboxChanged();
    }
  }, []);

  const pendingCount = items.filter((item) => item.status === 'PENDING' || item.status === 'FAILED').length;

  return { items, loading, syncing, online, pendingCount, reload, sync };
}

/**
 * Mounted once in the authenticated layout: the moment the browser reports
 * itself online again, push whatever is queued. Failures are left in the
 * queue and surfaced on the Sync page; nothing here retries in a loop.
 */
export function useOutboxAutoSync(onDone?: (summary: SyncRunSummary) => void): void {
  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      if (!navigator.onLine) return;
      if ((await outbox.pendingCount()) === 0) return;
      try {
        const summary = await syncOutbox();
        if (!cancelled) onDone?.(summary);
      } catch {
        // Left PENDING - the Sync page shows it, and the next online event tries again.
      } finally {
        notifyOutboxChanged();
      }
    };
    void run();
    window.addEventListener('online', run);
    return () => {
      cancelled = true;
      window.removeEventListener('online', run);
    };
  }, [onDone]);
}
