import { useCallback, useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { Loader2, Lock, Send } from 'lucide-react';
import { BackLink } from '@/shared/components/BackLink';
import { Button } from '@/shared/components/ui/button';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { ErrorState } from '@/shared/components/ErrorState';
import { PageHeader } from '@/shared/components/PageHeader';
import { formatDateTime } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { platformAdminSupportService } from '../services/platformAdminSupportService';
import type { SupportTicketDetailResponse, TicketPriority, TicketStatus } from '../types';

const STATUS_BADGE: Record<TicketStatus, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  OPEN: 'destructive', IN_PROGRESS: 'secondary', WAITING_FOR_USER: 'outline', RESOLVED: 'default', CLOSED: 'outline',
};

export function PlatformAdminSupportDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const toast = useToast();
  const { admin } = usePlatformAdminAuth();
  const canManage = admin?.permissions.includes('SUPPORT_MANAGE') ?? false;

  const [ticket, setTicket] = useState<SupportTicketDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [reply, setReply] = useState('');
  const [internal, setInternal] = useState(false);
  const [sending, setSending] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTicket(await platformAdminSupportService.get(id));
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  if (Number.isNaN(id)) return <Navigate to={PLATFORM_ADMIN_ROUTES.support} replace />;

  const submitReply = async () => {
    if (!reply.trim()) return;
    setSending(true);
    try {
      await platformAdminSupportService.reply(id, reply.trim(), internal);
      setReply('');
      setInternal(false);
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not send this message.');
    } finally {
      setSending(false);
    }
  };

  const changeStatus = async (status: TicketStatus) => {
    try {
      await platformAdminSupportService.changeStatus(id, status);
      toast.success('Status updated.');
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not update status.');
    }
  };

  const changePriority = async (priority: TicketPriority) => {
    try {
      await platformAdminSupportService.changePriority(id, priority);
      toast.success('Priority updated.');
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not update priority.');
    }
  };

  if (error) return <Card><ErrorState error={error} onRetry={load} /></Card>;
  if (loading || !ticket) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  return (
    <>
      <BackLink to={PLATFORM_ADMIN_ROUTES.support} label="Support Center" />

      <PageHeader
        title={ticket.subject}
        description={`${ticket.tenantName} · Raised by ${ticket.raisedByName} · ${ticket.category}`}
        actions={<Badge variant={STATUS_BADGE[ticket.status]}>{ticket.status.replace(/_/g, ' ')}</Badge>}
      />

      <div className="grid gap-5 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          <Card>
            <CardHeader><CardTitle className="text-base">Description</CardTitle></CardHeader>
            <CardContent className="whitespace-pre-wrap text-sm">{ticket.description}</CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="text-base">Conversation</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              {ticket.messages.length === 0 ? (
                <p className="text-sm text-muted-foreground">No replies yet.</p>
              ) : ticket.messages.map((message) => (
                <div
                  key={message.id}
                  className={
                    message.internal
                      ? 'rounded-md border border-dashed border-amber-500/50 bg-amber-500/10 p-3'
                      : message.authorType === 'PLATFORM_ADMIN' ? 'rounded-md bg-muted/50 p-3' : 'rounded-md border p-3'
                  }
                >
                  <div className="mb-1 flex items-center justify-between text-xs text-muted-foreground">
                    <span className="flex items-center gap-1.5 font-medium text-foreground">
                      {message.internal ? <Lock className="h-3 w-3" /> : null}
                      {message.authorName}
                      {message.internal ? <Badge variant="outline" className="ml-1 text-[10px]">Internal note</Badge> : null}
                    </span>
                    <span>{formatDateTime(message.createdAt)}</span>
                  </div>
                  <p className="whitespace-pre-wrap text-sm">{message.message}</p>
                </div>
              ))}

              {canManage ? (
                <div className="space-y-2 pt-2">
                  <textarea
                    rows={3}
                    placeholder="Reply to the tenant, or check 'Internal note' for a team-only comment…"
                    value={reply}
                    onChange={(e) => setReply(e.target.value)}
                    className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  />
                  <div className="flex items-center justify-between">
                    <label className="flex items-center gap-2 text-sm text-muted-foreground">
                      <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} />
                      Internal note (never visible to the tenant)
                    </label>
                    <Button onClick={() => void submitReply()} loading={sending}>
                      <Send className="h-4 w-4" />
                      Send
                    </Button>
                  </div>
                </div>
              ) : null}
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader><CardTitle className="text-base">Ticket actions</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="mb-1 text-xs text-muted-foreground">Priority</p>
              <Select value={ticket.priority} onValueChange={(v) => void changePriority(v as TicketPriority)} disabled={!canManage}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map((p) => <SelectItem key={p} value={p}>{p}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div>
              <p className="mb-1 text-xs text-muted-foreground">Status</p>
              <Select value={ticket.status} onValueChange={(v) => void changeStatus(v as TicketStatus)} disabled={!canManage}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {['OPEN', 'IN_PROGRESS', 'WAITING_FOR_USER', 'RESOLVED', 'CLOSED'].map((s) => (
                    <SelectItem key={s} value={s}>{s.replace(/_/g, ' ')}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
