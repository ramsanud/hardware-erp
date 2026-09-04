import { useEffect, useState } from 'react';
import { DatabaseBackup, Download } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/shared/components/ui/card';
import { EmptyState } from '@/shared/components/EmptyState';
import { downloadBlob, formatDateTime } from '@/shared/lib/utils';
import { useToast } from '@/modules/auth/hooks/useToast';
import { platformAdminBackupService } from '../services/platformAdminBackupService';
import type { TenantExportFormat, TenantExportLogResponse } from '../types';

interface TenantBackupCardProps {
  tenantId: number;
  canView: boolean;
  canExport: boolean;
}

/**
 * CR-057 phase 11 - "Backup Center" as an honest, on-demand tenant data
 * export, not a fake automated-backup status board. This app has no
 * snapshot/blob storage infrastructure to back a real "last successful
 * backup" claim - see TenantDataExportService's own javadoc.
 */
export function TenantBackupCard({ tenantId, canView, canExport }: TenantBackupCardProps) {
  const toast = useToast();
  const [history, setHistory] = useState<TenantExportLogResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState<TenantExportFormat | null>(null);

  const reload = () => {
    if (!canView) { setLoading(false); return; }
    setLoading(true);
    platformAdminBackupService.history(tenantId)
      .then(setHistory)
      .catch(() => setHistory([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => { reload(); }, [tenantId, canView]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleExport = async (format: TenantExportFormat) => {
    setExporting(format);
    try {
      const blob = await platformAdminBackupService.export(tenantId, format);
      downloadBlob(blob, `tenant-${tenantId}-export.${format === 'JSON' ? 'json' : 'zip'}`);
      toast.success('Export downloaded.');
      reload();
    } catch {
      toast.error(null, 'Could not build the export. Check the history below - it may still have been logged.');
    } finally {
      setExporting(null);
    }
  };

  return (
    <Card className="lg:col-span-3">
      <CardHeader className="flex-row items-center justify-between gap-4 space-y-0">
        <div>
          <div className="flex items-center gap-2">
            <DatabaseBackup className="h-4 w-4 text-primary" aria-hidden />
            <CardTitle className="text-base">Backup Center</CardTitle>
          </div>
          <CardDescription>
            On-demand export of this tenant&apos;s core records - no automated backup infrastructure is
            configured in this environment, so this is the honest, real alternative: generated fresh on
            every download, never stored.
          </CardDescription>
        </div>
        {canExport ? (
          <div className="flex shrink-0 gap-2">
            <Button variant="outline" size="sm" loading={exporting === 'JSON'} onClick={() => handleExport('JSON')}>
              <Download className="h-3.5 w-3.5" />
              JSON
            </Button>
            <Button variant="outline" size="sm" loading={exporting === 'CSV'} onClick={() => handleExport('CSV')}>
              <Download className="h-3.5 w-3.5" />
              CSV
            </Button>
          </div>
        ) : null}
      </CardHeader>
      <CardContent>
        {!canView ? (
          <p className="text-xs text-muted-foreground">You do not have permission to view export history.</p>
        ) : loading ? (
          <p className="text-xs text-muted-foreground">Loading...</p>
        ) : history.length === 0 ? (
          <EmptyState icon={DatabaseBackup} title="No exports yet" description="Export this tenant's data above to see it logged here." />
        ) : (
          <ul className="space-y-2">
            {history.slice(0, 10).map((entry) => (
              <li key={entry.id} className="flex items-center justify-between rounded-md border px-3 py-2 text-sm">
                <span className="flex items-center gap-2">
                  <Badge variant="outline">{entry.format}</Badge>
                  {formatDateTime(entry.createdAt)}
                  {entry.recordCount != null ? (
                    <span className="text-muted-foreground">· {entry.recordCount} records</span>
                  ) : null}
                </span>
                <Badge variant={entry.status === 'COMPLETED' ? 'success' : 'destructive'}>
                  {entry.status === 'COMPLETED' ? 'Completed' : `Failed${entry.errorDetail ? `: ${entry.errorDetail}` : ''}`}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
