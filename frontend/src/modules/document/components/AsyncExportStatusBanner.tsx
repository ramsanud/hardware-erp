import { useEffect, useRef } from 'react';
import { CheckCircle2, Download, Loader2, XCircle } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/shared/components/ui/alert';
import { Button } from '@/shared/components/ui/button';
import { useToast } from '@/modules/auth/hooks/useToast';
import { downloadBlob } from '@/shared/lib/utils';
import { documentJobService } from '../services/documentJobService';
import type { ReportJob } from '../types';

interface AsyncExportStatusBannerProps {
  job: ReportJob | null;
  /** The hook's own error - a failed enqueue or a lost poll, distinct from a job that ended FAILED. */
  error?: string | null;
  onDismiss?: () => void;
}

const LABELS: Record<ReportJob['status'], string> = {
  PENDING: 'Queued - the export will start in a moment.',
  PROCESSING: 'Building the file in the background. You can keep working.',
  COMPLETED: 'Your file is ready.',
  FAILED: 'The export could not be built.',
};

/**
 * CR-101. One background export's state, in the page, with a toast the
 * moment it turns COMPLETED so a person who scrolled away still hears
 * about it. Nothing is drawn for a job that does not exist - no empty
 * "no exports" frame taking up a row (rule 12: never draw what is not there).
 */
export function AsyncExportStatusBanner({ job, error, onDismiss }: AsyncExportStatusBannerProps) {
  const toast = useToast();
  const announced = useRef<number | null>(null);

  useEffect(() => {
    if (!job || announced.current === job.id) return;
    if (job.status === 'COMPLETED') {
      announced.current = job.id;
      toast.success(`${job.fileName ?? 'Your export'} is ready to download.`);
    } else if (job.status === 'FAILED') {
      announced.current = job.id;
      toast.error(new Error(job.errorMessage ?? 'Export failed'), job.errorMessage ?? 'The export could not be built.');
    }
  }, [job, toast]);

  if (!job && !error) return null;

  const download = async () => {
    if (!job) return;
    try {
      downloadBlob(await documentJobService.download(job.id), job.fileName ?? `export-${job.id}`);
    } catch (caught) {
      toast.error(caught, 'Could not download the file.');
    }
  };

  const status = job?.status;
  const failed = status === 'FAILED' || Boolean(error);
  const done = status === 'COMPLETED';

  return (
    <Alert
      variant={failed ? 'destructive' : 'default'}
      className="flex flex-col gap-2 sm:flex-row sm:items-center"
      data-export-banner
      data-export-status={error ? 'ERROR' : status}
    >
      {done ? <CheckCircle2 className="h-4 w-4 text-success" aria-hidden />
        : failed ? <XCircle className="h-4 w-4" aria-hidden />
        : <Loader2 className="h-4 w-4 animate-spin text-primary" aria-hidden />}
      <div className="min-w-0 flex-1">
        <AlertTitle className="mb-0.5">
          {job ? `${humanise(job.reportType)} · ${job.format}` : 'Background export'}
        </AlertTitle>
        <AlertDescription className="text-xs">
          {error ?? (status === 'FAILED' && job?.errorMessage ? job.errorMessage : status ? LABELS[status] : '')}
        </AlertDescription>
      </div>
      <div className="flex shrink-0 items-center gap-2">
        {done ? (
          <Button type="button" size="sm" onClick={() => void download()} data-export-download>
            <Download className="h-4 w-4" aria-hidden />
            Download
          </Button>
        ) : null}
        {onDismiss && (done || failed) ? (
          <Button type="button" size="sm" variant="ghost" onClick={onDismiss}>Dismiss</Button>
        ) : null}
      </div>
    </Alert>
  );
}

/** DAY_BOOK -> "Day book". The server code is the identifier; the person reads words. */
export function humanise(reportType: string): string {
  const words = reportType.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}
