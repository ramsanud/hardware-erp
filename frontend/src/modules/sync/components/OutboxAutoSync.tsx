import { useCallback } from 'react';
import { toast } from 'sonner';
import { useOutboxAutoSync } from '../hooks/useOutbox';
import type { SyncRunSummary } from '../services/syncService';

/**
 * Renders nothing. Lives in the authenticated layout so the online event is
 * heard on every page. Uses sonner directly rather than useToast() because
 * that hook returns a fresh object per render, which would re-arm the
 * effect - and re-run the sync check - on every layout render.
 */
export function OutboxAutoSync() {
  const onDone = useCallback((summary: SyncRunSummary) => {
    if (summary.attempted === 0) return;
    if (summary.conflicts || summary.failed) {
      toast(`Offline sync: ${summary.synced} sent, ${summary.conflicts + summary.failed} need attention - see Offline sync.`);
    } else {
      toast.success(`Offline sync: ${summary.synced} invoice${summary.synced === 1 ? '' : 's'} sent to the server.`);
    }
  }, []);
  useOutboxAutoSync(onDone);
  return null;
}
