import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { LifeBuoy, Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import { Input } from '@/shared/components/ui/input';
import { Badge } from '@/shared/components/ui/badge';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  ColumnSettings, useColumnPreferences, type ColumnDef,
} from '@/shared/components/table/ColumnPreferences';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { FormField } from '@/shared/components/FormField';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE } from '@/shared/constants';
import { formatDateTime } from '@/shared/lib/utils';
import { useToast } from '@/modules/auth/hooks/useToast';
import { SUPPORT_ROUTES, TICKET_CATEGORY_OPTIONS } from '../constants';
import { supportTicketService } from '../services/supportTicketService';
import type { SupportTicketSummaryResponse, TicketCategory, TicketStatus } from '../types';

const ticketSchema = z.object({
  subject: z.string().trim().min(1, 'Enter a subject').max(200),
  description: z.string().trim().min(1, 'Describe the issue').max(4000),
  category: z.enum(['LOGIN', 'INVOICE', 'PAYMENT', 'PURCHASE', 'INVENTORY', 'WHATSAPP', 'SUBSCRIPTION', 'TECHNICAL', 'OTHER']),
});
type TicketFormValues = z.infer<typeof ticketSchema>;

const STATUS_BADGE: Record<TicketStatus, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  OPEN: 'destructive', IN_PROGRESS: 'secondary', WAITING_FOR_USER: 'outline', RESOLVED: 'default', CLOSED: 'outline',
};

/** CR-068. Column catalogue - ids are persisted, so never rename them. */
const TICKET_COLUMNS: ColumnDef<SupportTicketSummaryResponse>[] = [
  {
    id: 'subject',
    header: 'Subject',
    locked: true,
    cellClassName: 'font-medium',
    cell: (row) => row.subject,
  },
  {
    id: 'category',
    header: 'Category',
    headClassName: 'hidden sm:table-cell',
    cellClassName: 'hidden sm:table-cell text-xs text-muted-foreground',
    cell: (row) => row.category,
  },
  {
    id: 'status',
    header: 'Status',
    cellClassName: 'status-on-card',
    cell: (row) => <Badge variant={STATUS_BADGE[row.status]}>{row.status.replace(/_/g, ' ')}</Badge>,
  },
  {
    id: 'raised',
    header: 'Raised',
    headClassName: 'hidden md:table-cell',
    cellClassName: 'hidden md:table-cell text-xs text-muted-foreground',
    cell: (row) => formatDateTime(row.createdAt),
  },
];

export function SupportTicketListPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const columns = useColumnPreferences('supportTicket', TICKET_COLUMNS);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [dialogOpen, setDialogOpen] = useState(false);

  const fetcher = useCallback(() => supportTicketService.list(page, size), [page, size]);
  const { data, loading, error, reload } = useAsyncList(fetcher, [page, size]);

  const {
    register, control, handleSubmit, reset, formState: { errors, isSubmitting },
  } = useForm<TicketFormValues>({
    resolver: zodResolver(ticketSchema),
    defaultValues: { subject: '', description: '', category: 'OTHER' },
  });

  const submit = handleSubmit(async (values) => {
    try {
      const created = await supportTicketService.create(values as { subject: string; description: string; category: TicketCategory });
      toast.success('Support ticket raised.');
      reset();
      setDialogOpen(false);
      await reload();
      navigate(SUPPORT_ROUTES.detail(created.id));
    } catch (caught) {
      toast.error(caught, 'Could not raise this ticket.');
    }
  });

  return (
    <>
      <PageHeader
        title="Support"
        description="Raise a ticket for anything not working as expected."
        actions={
          <div className="flex items-center gap-2">
            <ColumnSettings preferences={columns} label="ticket" />
            <Button onClick={() => setDialogOpen(true)}>
              <Plus className="h-4 w-4" />
              <span>New ticket</span>
            </Button>
          </div>
        }
      />

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  {columns.visible.map((column) => (
                    <TableHead key={column.id} className={columns.resolveClassName(column, 'head')}>
                      {column.header}
                    </TableHead>
                  ))}
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={columns.visible.length} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow key={row.id} className="cursor-pointer" onClick={() => navigate(SUPPORT_ROUTES.detail(row.id))}>
                      {columns.visible.map((column) => (
                        <TableCell key={column.id} className={columns.resolveClassName(column, 'cell')}>
                          {column.cell(row)}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              )}
            </Table>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState
                icon={LifeBuoy}
                title="No support tickets yet"
                description="When you raise a ticket, it will appear here and you can track replies from our team."
                action={<Button onClick={() => setDialogOpen(true)}>New ticket</Button>}
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <Dialog open={dialogOpen} onOpenChange={(open) => { if (!isSubmitting) setDialogOpen(open); }}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader><DialogTitle>Raise a support ticket</DialogTitle></DialogHeader>
          <form onSubmit={submit} className="space-y-4" noValidate>
            <FormField id="subject" label="Subject" error={errors.subject?.message} required>
              <Input id="subject" autoFocus {...register('subject')} />
            </FormField>
            <FormField id="category" label="Category" error={errors.category?.message} required>
              <Controller
                control={control}
                name="category"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="category"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {TICKET_CATEGORY_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </FormField>
            <FormField id="description" label="Describe the issue" error={errors.description?.message} required>
              <textarea
                id="description"
                rows={5}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                {...register('description')}
              />
            </FormField>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)} disabled={isSubmitting}>Cancel</Button>
              <Button type="submit" loading={isSubmitting}>Submit</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
