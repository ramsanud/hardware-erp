import { useCallback, useEffect, useState } from 'react';
import { MessageSquareText } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Card } from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { BackLink } from '@/shared/components/BackLink';
import { SETTINGS_ROUTES } from '@/modules/settings/constants';
import { ErrorState } from '@/shared/components/ErrorState';
import { EmptyState } from '@/shared/components/EmptyState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { formatDateTime } from '@/shared/lib/utils';
import { DEFAULT_PAGE_SIZE } from '@/shared/constants';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import {
  notificationService, type NotificationChannel, type NotificationLogStatus,
} from '../services/notificationService';

const CHANNEL_OPTIONS: { value: NotificationChannel | 'ALL'; label: string }[] = [
  { value: 'WHATSAPP', label: 'WhatsApp' },
  { value: 'SMS', label: 'SMS' },
  { value: 'EMAIL', label: 'Email' },
  { value: 'ALL', label: 'All channels' },
];

const STATUS_VARIANT: Record<NotificationLogStatus, 'success' | 'warning' | 'destructive' | 'secondary'> = {
  SENT: 'secondary',
  LOGGED_ONLY: 'warning',
  FAILED: 'destructive',
  DELIVERED: 'success',
  READ: 'success',
};

/**
 * CR-056 §13 - defaults to WhatsApp, since that is what the spec's own
 * mockup names ("WhatsApp Message History"), but every channel shares one
 * audit trail (notification_log) so switching is just a filter, not a
 * different page. DELIVERED/READ only ever appear once a real Meta webhook
 * event has confirmed them (WhatsAppWebhookController) - never assumed
 * from a successful send.
 */
export function NotificationHistoryPage() {
  const [channel, setChannel] = useState<NotificationChannel | 'ALL'>('WHATSAPP');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  useEffect(() => { setPage(0); }, [channel, size]);

  const fetcher = useCallback(
    () => notificationService.log({ channel: channel === 'ALL' ? undefined : channel, page, size }),
    [channel, page, size],
  );
  const { data, loading, error, reload } = useAsyncList(fetcher, [channel, page, size]);

  return (
    <>
      <BackLink to={SETTINGS_ROUTES.whatsapp} label="WhatsApp Business" />
      <PageHeader title="WhatsApp Message History" description="Every outbound notification attempt, with the real delivery status Meta reported - never assumed." />

      <div className="flex items-center gap-2">
        <Select value={channel} onValueChange={(v) => setChannel(v as NotificationChannel | 'ALL')}>
          <SelectTrigger className="w-40"><SelectValue /></SelectTrigger>
          <SelectContent>
            {CHANNEL_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Recipient</TableHead>
                  <TableHead className="hidden sm:table-cell">Type</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={4} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((entry) => (
                    <TableRow key={entry.id}>
                      <TableCell className="tabular">{formatDateTime(entry.createdAt)}</TableCell>
                      <TableCell className="tabular">{entry.recipient}</TableCell>
                      <TableCell className="hidden sm:table-cell">
                        {entry.relatedEntityType ?? '—'}
                        {entry.relatedEntityId ? ` #${entry.relatedEntityId}` : ''}
                      </TableCell>
                      <TableCell>
                        <Badge variant={STATUS_VARIANT[entry.status]}>{entry.status}</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              )}
            </Table>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState icon={MessageSquareText} title="No messages yet"
                          description="Outbound notifications on this channel will appear here." />
            ) : null}
          </>
        )}
      </Card>

      {data ? <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} /> : null}
    </>
  );
}
