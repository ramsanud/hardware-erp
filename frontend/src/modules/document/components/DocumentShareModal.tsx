import { useEffect, useState } from 'react';
import { Camera, Download, FileImage, FileSpreadsheet, FileText, Mail, MessageCircle } from 'lucide-react';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { FormField } from '@/shared/components/FormField';
import { useToast } from '@/modules/auth/hooks/useToast';
import { cn, downloadBlob } from '@/shared/lib/utils';
import { captureElementAsPng, shareOrDownloadImage } from '@/shared/utils/shareImage';
import { documentJobService } from '../services/documentJobService';
import { useDocumentJob } from '../hooks/useDocumentJob';
import { AsyncExportStatusBanner } from './AsyncExportStatusBanner';
import type { ReportJobFormat, ShareChannel, ShareFormat } from '../types';

/*
  CR-101. One share sheet for every report and document: pick a format
  (PDF | Image | Excel), pick where it goes (Download | WhatsApp | Email).
  The file is built by the background job queue (useDocumentJob) so a
  year-long Day Book never sits inside a click handler; the sheet shows the
  job's progress inline and acts the moment it is COMPLETED.

  WhatsApp never receives the file from a URL (CR-080's own limit): on a
  phone the device's share sheet takes it, otherwise it is downloaded and
  the customer's chat - or WhatsApp's own contact chooser - opens with the
  caption typed, for the person to attach it. The same rule
  InvoiceDetailPage.handleShareViaApp already follows.

  `captureElement` is the immediate path: html2canvas on what is on screen
  right now, no server round trip - for "send me that as a picture".
*/

export interface DocumentShareSource {
  /** Server code, e.g. "DAY_BOOK" - the same one /v1/documents/jobs takes. */
  reportType: string;
  /** The report's own filters, as the synchronous export would send them. */
  params: Record<string, string>;
  /** What the person sees in the title, e.g. "Day Book 01-09-2026 to 20-09-2026". */
  label: string;
}

interface DocumentShareModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  source: DocumentShareSource;
  /** Pre-fills the WhatsApp recipient; without it the contact chooser opens. */
  recipientMobile?: string | null;
  recipientEmail?: string | null;
  /** When given, adds "Image of this screen" - an html2canvas capture shared at once. */
  captureElement?: () => HTMLElement | null;
  /** Formats this document can take. Excel is meaningless for a single invoice, for instance. */
  formats?: ShareFormat[];
}

const FORMAT_META: Record<ShareFormat, { label: string; icon: typeof FileText; job: ReportJobFormat; ext: string }> = {
  PDF: { label: 'PDF', icon: FileText, job: 'PDF', ext: 'pdf' },
  PNG: { label: 'Image', icon: FileImage, job: 'PNG', ext: 'png' },
  XLSX: { label: 'Excel', icon: FileSpreadsheet, job: 'XLSX', ext: 'xlsx' },
};

export function DocumentShareModal({
  open, onOpenChange, source, recipientMobile, recipientEmail, captureElement,
  formats = ['PDF', 'PNG', 'XLSX'],
}: DocumentShareModalProps) {
  const toast = useToast();
  const { job, working, error, enqueue, reset } = useDocumentJob();
  const [format, setFormat] = useState<ShareFormat>(formats[0]);
  const [pendingChannel, setPendingChannel] = useState<ShareChannel | null>(null);
  const [email, setEmail] = useState(recipientEmail ?? '');
  const [emailOpen, setEmailOpen] = useState(false);
  const [busy, setBusy] = useState<ShareChannel | 'CAPTURE' | null>(null);

  // A closed sheet forgets its job: the next open may want another format,
  // and a stale COMPLETED banner would offer yesterday's file.
  useEffect(() => {
    if (!open) {
      reset();
      setPendingChannel(null);
      setEmailOpen(false);
      setBusy(null);
    }
  }, [open, reset]);

  const meta = FORMAT_META[format];
  const jobReady = job?.status === 'COMPLETED' && job.format === meta.job;

  /** Ensures a COMPLETED job in the chosen format exists, queuing one if needed. Returns null while it is still building. */
  const ensureJob = async (channel: ShareChannel) => {
    if (jobReady) return job;
    setPendingChannel(channel);
    const created = await enqueue({ reportType: source.reportType, format: meta.job, params: source.params });
    if (created?.status === 'COMPLETED') return created;
    return null;
  };

  const deliver = async (channel: ShareChannel) => {
    const ready = job?.status === 'COMPLETED' ? job : null;
    if (!ready) return;
    setBusy(channel);
    try {
      if (channel === 'DOWNLOAD') {
        downloadBlob(await documentJobService.download(ready.id), ready.fileName ?? `${source.reportType}.${meta.ext}`);
      } else if (channel === 'WHATSAPP') {
        const blob = await documentJobService.download(ready.id);
        const link = await documentJobService.whatsAppLink(ready.id, recipientMobile ?? undefined);
        const outcome = await shareOrDownloadImage(blob, {
          fileName: ready.fileName ?? `${source.reportType}.${meta.ext}`,
          title: source.label,
          text: link.message,
          fallbackUrl: link.url,
        });
        if (outcome === 'downloaded') {
          toast.info('The file was downloaded - attach it in the WhatsApp chat that just opened.');
        }
      } else if (channel === 'EMAIL') {
        const status = await documentJobService.email(ready.id, email.trim());
        if (status === 'SENT') {
          toast.success(`Sent to ${email.trim()}.`);
          setEmailOpen(false);
        } else if (status === 'LOGGED_ONLY') {
          toast.info('Email is not configured on this server yet - nothing was actually sent.');
        } else {
          toast.error(new Error('Send failed'), 'Could not send the email.');
        }
      }
    } catch (caught) {
      toast.error(caught, 'Could not share the document.');
    } finally {
      setBusy(null);
      setPendingChannel(null);
    }
  };

  // The job the person was waiting for has just finished: carry out the
  // channel they clicked, once, rather than making them click again.
  useEffect(() => {
    if (pendingChannel && jobReady && busy === null) {
      void deliver(pendingChannel);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pendingChannel, jobReady]);

  const start = async (channel: ShareChannel) => {
    if (channel === 'EMAIL' && !emailOpen) {
      setEmailOpen(true);
      return;
    }
    if (channel === 'EMAIL' && !email.trim()) return;
    const ready = await ensureJob(channel);
    if (ready) {
      setPendingChannel(channel);
    }
  };

  const captureNow = async () => {
    const element = captureElement?.();
    if (!element) return;
    setBusy('CAPTURE');
    try {
      const blob = await captureElementAsPng(element);
      const outcome = await shareOrDownloadImage(blob, {
        fileName: `${source.reportType.toLowerCase().replace(/_/g, '-')}.png`,
        title: source.label,
        text: source.label,
      });
      if (outcome === 'downloaded') toast.success('Image saved.');
    } catch (caught) {
      toast.error(caught, 'Could not capture the screen.');
    } finally {
      setBusy(null);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md" data-share-modal>
        <DialogHeader>
          <DialogTitle>Share</DialogTitle>
          <DialogDescription>{source.label}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div role="radiogroup" aria-label="Format" className="grid grid-cols-3 gap-2" data-share-formats>
            {formats.map((f) => {
              const m = FORMAT_META[f];
              const Icon = m.icon;
              const active = f === format;
              return (
                <Button
                  key={f}
                  type="button"
                  role="radio"
                  aria-checked={active}
                  variant={active ? 'secondary' : 'outline'}
                  className={cn('h-auto flex-col gap-1 py-3', active && 'ring-1 ring-primary/40')}
                  onClick={() => setFormat(f)}
                  disabled={working}
                  data-share-format={f}
                >
                  <Icon className="h-5 w-5" aria-hidden />
                  <span className="text-xs">{m.label}</span>
                </Button>
              );
            })}
          </div>

          <div className="grid gap-2" data-share-channels>
            <Button type="button" variant="outline" className="justify-start" onClick={() => void start('DOWNLOAD')}
              loading={busy === 'DOWNLOAD' || (working && pendingChannel === 'DOWNLOAD')} disabled={working || busy !== null}
              data-share-channel="DOWNLOAD">
              <Download className="h-4 w-4" aria-hidden />
              Download {meta.label}
            </Button>
            {format !== 'XLSX' ? (
              <Button type="button" variant="outline" className="justify-start" onClick={() => void start('WHATSAPP')}
                loading={busy === 'WHATSAPP' || (working && pendingChannel === 'WHATSAPP')} disabled={working || busy !== null}
                data-share-channel="WHATSAPP">
                <MessageCircle className="h-4 w-4" aria-hidden />
                {recipientMobile ? `WhatsApp ${recipientMobile}` : 'WhatsApp'}
              </Button>
            ) : null}
            <Button type="button" variant="outline" className="justify-start" onClick={() => void start('EMAIL')}
              loading={busy === 'EMAIL' || (working && pendingChannel === 'EMAIL')} disabled={working || busy !== null}
              data-share-channel="EMAIL">
              <Mail className="h-4 w-4" aria-hidden />
              Email {meta.label}
            </Button>
            {emailOpen ? (
              <div className="flex items-end gap-2 rounded-md border p-3" data-share-email>
                <FormField id="share-email" label="Send to" className="flex-1">
                  <Input id="share-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                    placeholder="name@example.com" autoFocus />
                </FormField>
                <Button type="button" onClick={() => void start('EMAIL')} disabled={!email.trim() || working || busy !== null}>
                  Send
                </Button>
              </div>
            ) : null}
            {captureElement ? (
              <Button type="button" variant="ghost" className="justify-start" onClick={() => void captureNow()}
                loading={busy === 'CAPTURE'} disabled={busy !== null} data-share-channel="CAPTURE">
                <Camera className="h-4 w-4" aria-hidden />
                Image of this screen
              </Button>
            ) : null}
          </div>

          <AsyncExportStatusBanner job={job} error={error} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
