import { useCallback, useEffect, useState } from 'react';
import { Database, Download, Loader2, Send } from 'lucide-react';
import { apiGet, apiGetBlob, apiPost } from '@/services/apiClient';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { downloadBlob, formatDateTime } from '@/shared/lib/utils';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { toast as sonner } from 'sonner';
import { useToast } from '@/modules/auth/hooks/useToast';
import { useFeatureGate } from '@/modules/subscription/hooks/useFeatureGate';
import { UpgradeDialog } from '@/modules/subscription/components/UpgradeDialog';

/** Backend: backup/controller/TenantBackupController.java + summary/DailySummaryController.java (CR-092). */
interface BackupSummary {
  id: number;
  format: 'JSON' | 'CSV';
  triggerType: 'MANUAL' | 'SCHEDULED';
  status: 'COMPLETED' | 'FAILED';
  recordCount?: number | null;
  fileSizeBytes?: number | null;
  errorDetail?: string | null;
  createdAt: string;
}

function kb(bytes?: number | null): string {
  if (!bytes) return '—';
  return bytes < 1024 ? `${bytes} B` : `${Math.round(bytes / 1024)} KB`;
}

/**
 * CR-092. Owner-only (BACKUP_MANAGE). "Back up now" is available on every
 * plan that can export data; the nightly automatic backup needs the
 * Premium plan and the server keeps the newest seven. The daily summary
 * control lives here too: one owner-facing "keep me informed" card.
 */
export function BackupsCard() {
  const toast = useToast();
  const gate = useFeatureGate();
  const [backups, setBackups] = useState<BackupSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [taking, setTaking] = useState(false);
  const [downloadingId, setDownloadingId] = useState<number | null>(null);
  const [sendingSummary, setSendingSummary] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setBackups(await apiGet<BackupSummary[]>('/v1/backups'));
    } catch {
      // Stable callback on purpose: useToast() is a fresh object per render and would re-arm the effect below.
      sonner.error('Could not load backups.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void reload(); }, [reload]);

  const takeNow = async () => {
    setTaking(true);
    try {
      const result = await gate.guard(() => apiPost<BackupSummary>('/v1/backups?format=JSON'));
      if (result === undefined) return;
      toast.success(`Backup taken - ${result.recordCount ?? 0} records.`);
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not take a backup.');
    } finally {
      setTaking(false);
    }
  };

  const download = async (backup: BackupSummary) => {
    setDownloadingId(backup.id);
    try {
      const blob = await apiGetBlob(`/v1/backups/${backup.id}/download`);
      downloadBlob(blob, `backup-${backup.createdAt.slice(0, 10)}.${backup.format === 'JSON' ? 'json' : 'zip'}`);
    } catch (caught) {
      toast.error(caught, 'Could not download the backup.');
    } finally {
      setDownloadingId(null);
    }
  };

  const sendSummary = async () => {
    setSendingSummary(true);
    try {
      const status = await apiPost<string>('/v1/daily-summary/send');
      if (status === 'SENT') toast.success("Today's summary sent to your phone/email.");
      else if (status === 'LOGGED_ONLY') toast.info("Today's summary is in your notifications. Sending it to your phone needs a connected channel and the Premium plan.");
      else toast.info(`Summary recorded in notifications (delivery: ${status}).`);
    } catch (caught) {
      toast.error(caught, 'Could not send the summary.');
    } finally {
      setSendingSummary(false);
    }
  };

  return (
    <PermissionGate permission={PERMISSIONS.BACKUP_MANAGE}>
      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0">
          <div>
            <CardTitle className="flex items-center gap-2 text-base"><Database className="h-4 w-4 text-primary" aria-hidden />Backups &amp; daily summary</CardTitle>
            <CardDescription>
              A backup is a complete copy of your products, customers, suppliers, invoices, quotations, purchases, expenses and workers.
              On the Premium plan one is taken automatically every night and the last seven are kept.
            </CardDescription>
          </div>
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" onClick={() => void sendSummary()} loading={sendingSummary}>
              <Send className="h-4 w-4" />
              Send today&apos;s summary
            </Button>
            <Button type="button" size="sm" onClick={() => void takeNow()} loading={taking}>
              <Database className="h-4 w-4" />
              Back up now
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex items-center justify-center py-6 text-muted-foreground"><Loader2 className="h-5 w-5 animate-spin" aria-label="Loading" /></div>
          ) : backups.length === 0 ? (
            <p className="text-sm text-muted-foreground">No backups yet.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Taken</TableHead>
                  <TableHead>How</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Records</TableHead>
                  <TableHead className="text-right">Size</TableHead>
                  <TableHead className="text-right"><span className="sr-only">Download</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {backups.map((b) => (
                  <TableRow key={b.id}>
                    <TableCell className="whitespace-nowrap">{formatDateTime(b.createdAt)}</TableCell>
                    <TableCell>{b.triggerType === 'SCHEDULED' ? 'Automatic' : 'Manual'} · {b.format}</TableCell>
                    <TableCell>
                      <Badge variant={b.status === 'COMPLETED' ? 'success' : 'destructive'}>{b.status === 'COMPLETED' ? 'Completed' : 'Failed'}</Badge>
                      {b.errorDetail ? <span className="block text-xs text-muted-foreground">{b.errorDetail}</span> : null}
                    </TableCell>
                    <TableCell className="text-right tabular">{b.recordCount ?? '—'}</TableCell>
                    <TableCell className="text-right tabular">{kb(b.fileSizeBytes)}</TableCell>
                    <TableCell className="text-right">
                      {b.status === 'COMPLETED' ? (
                        <Button type="button" variant="ghost" size="sm" loading={downloadingId === b.id} onClick={() => void download(b)}>
                          <Download className="h-4 w-4" />
                          Download
                        </Button>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
      <UpgradeDialog details={gate.details} onClose={gate.close} />
    </PermissionGate>
  );
}
