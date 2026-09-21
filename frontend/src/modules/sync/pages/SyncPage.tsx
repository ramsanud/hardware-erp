import { Link } from 'react-router-dom';
import {
  CloudOff, CloudUpload, Loader2, RefreshCw, Trash2, Wifi, WifiOff,
} from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { EmptyState } from '@/shared/components/EmptyState';
import { PageHeader } from '@/shared/components/PageHeader';
import { formatDateTime } from '@/shared/lib/utils';
import { useToast } from '@/modules/auth/hooks/useToast';
import { INVOICE_ROUTES } from '@/modules/invoice/constants';
import { outbox } from '../lib/outbox';
import { notifyOutboxChanged, useOutbox } from '../hooks/useOutbox';
import type { SyncTransactionStatus } from '../types';

const STATUS_VARIANT: Record<SyncTransactionStatus, 'warning' | 'success' | 'destructive' | 'info'> = {
  PENDING: 'warning',
  SYNCED: 'success',
  CONFLICT: 'destructive',
  FAILED: 'destructive',
};

const STATUS_LABEL: Record<SyncTransactionStatus, string> = {
  PENDING: 'Waiting to sync',
  SYNCED: 'Synced',
  CONFLICT: 'Conflict',
  FAILED: 'Failed',
};

/**
 * CR-091 Phase 9. What this device recorded while the server was out of
 * reach, and what happened to each item once it was sent. A CONFLICT is a
 * rule the server enforces that no longer held when the item arrived -
 * most often stock someone else sold in the meantime - and is never
 * retried silently: the owner decides.
 */
export function SyncPage() {
  const toast = useToast();
  const { items, loading, syncing, online, pendingCount, sync } = useOutbox();

  const handleSync = async () => {
    try {
      const summary = await sync();
      if (summary.attempted === 0) {
        toast.info('Nothing waiting to sync.');
      } else {
        toast.success(`${summary.synced} synced${summary.conflicts ? `, ${summary.conflicts} conflict${summary.conflicts === 1 ? '' : 's'}` : ''}${summary.failed ? `, ${summary.failed} failed` : ''}.`);
      }
    } catch (caught) {
      toast.error(caught, 'Could not sync - the items are still queued.');
    }
  };

  const handleRetry = async (clientUuid: string) => {
    await outbox.retry(clientUuid);
    notifyOutboxChanged();
  };

  const handleDiscard = async (clientUuid: string) => {
    await outbox.remove(clientUuid);
    notifyOutboxChanged();
    toast.info('Removed from this device. Nothing was sent to the server.');
  };

  const handleClearSynced = async () => {
    await outbox.clearSynced();
    notifyOutboxChanged();
  };

  return (
    <>
      <PageHeader
        title="Offline sync"
        description="Invoices this device recorded while the server could not be reached, and the result once each was sent."
        actions={(
          <div className="flex items-center gap-2">
            <Badge variant={online ? 'success' : 'warning'} className="gap-1">
              {online ? <Wifi className="h-3 w-3" aria-hidden /> : <WifiOff className="h-3 w-3" aria-hidden />}
              {online ? 'Online' : 'Offline'}
            </Badge>
            <Button type="button" onClick={() => void handleSync()} loading={syncing} disabled={!online || pendingCount === 0}>
              <CloudUpload className="h-4 w-4" />
              Sync now{pendingCount ? ` (${pendingCount})` : ''}
            </Button>
          </div>
        )}
      />

      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-3 space-y-0">
          <div>
            <CardTitle className="text-base">Outbox</CardTitle>
            <CardDescription>
              Each item carries a client id the server remembers, so re-sending after a lost connection can never create a duplicate.
            </CardDescription>
          </div>
          {items.some((item) => item.status === 'SYNCED') ? (
            <Button type="button" variant="ghost" size="sm" onClick={() => void handleClearSynced()}>
              <Trash2 className="h-4 w-4" />
              Clear synced
            </Button>
          ) : null}
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex items-center justify-center py-8 text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" aria-label="Loading" />
            </div>
          ) : items.length === 0 ? (
            <EmptyState icon={CloudOff} title="Nothing queued" description="An invoice raised while the server is unreachable is kept here and sent the moment you are back online." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Recorded</TableHead>
                  <TableHead>Customer</TableHead>
                  <TableHead>Items</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Result</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.clientUuid}>
                    <TableCell className="whitespace-nowrap">{formatDateTime(item.clientCreatedAt)}</TableCell>
                    <TableCell>
                      <span className="block font-medium">{item.customerName}</span>
                      <span className="block text-xs text-muted-foreground">{item.payload.customerMobile}</span>
                    </TableCell>
                    <TableCell className="tabular">{item.itemCount}</TableCell>
                    <TableCell><Badge variant={STATUS_VARIANT[item.status]}>{STATUS_LABEL[item.status]}</Badge></TableCell>
                    <TableCell className="max-w-xs text-sm">
                      {item.status === 'SYNCED' && item.resultReferenceId ? (
                        <Link to={INVOICE_ROUTES.detail(item.resultReferenceId)} className="text-primary hover:underline">{item.resultReferenceNumber}</Link>
                      ) : item.conflictReason ? (
                        <span className="text-muted-foreground">{item.conflictReason}</span>
                      ) : '—'}
                    </TableCell>
                    <TableCell className="text-right">
                      {item.status === 'FAILED' || item.status === 'CONFLICT' ? (
                        <div className="flex justify-end gap-1">
                          {item.status === 'FAILED' ? (
                            <Button type="button" variant="ghost" size="sm" onClick={() => void handleRetry(item.clientUuid)}>
                              <RefreshCw className="h-4 w-4" />
                              Retry
                            </Button>
                          ) : null}
                          <Button type="button" variant="ghost" size="sm" onClick={() => void handleDiscard(item.clientUuid)}>
                            <Trash2 className="h-4 w-4" />
                            Discard
                          </Button>
                        </div>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </>
  );
}
