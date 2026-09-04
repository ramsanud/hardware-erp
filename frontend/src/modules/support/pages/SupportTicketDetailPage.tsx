import { useCallback, useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { Loader2, Send } from 'lucide-react';
import { BackLink } from '@/shared/components/BackLink';
import { Button } from '@/shared/components/ui/button';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { formatDateTime } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { SUPPORT_ROUTES } from '../constants';
import { supportTicketService } from '../services/supportTicketService';
import type { SupportTicketDetailResponse, TicketStatus } from '../types';

const STATUS_BADGE: Record<TicketStatus, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  OPEN: 'destructive', IN_PROGRESS: 'secondary', WAITING_FOR_USER: 'outline', RESOLVED: 'default', CLOSED: 'outline',
};

export function SupportTicketDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const toast = useToast();
  const [ticket, setTicket] = useState<SupportTicketDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [reply, setReply] = useState('');
  const [sending, setSending] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTicket(await supportTicketService.get(id));
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  if (Number.isNaN(id)) return <Navigate to={SUPPORT_ROUTES.list} replace />;

  const submitReply = async () => {
    if (!reply.trim()) return;
    setSending(true);
    try {
      await supportTicketService.reply(id, { message: reply.trim(), internal: false });
      setReply('');
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not send your reply.');
    } finally {
      setSending(false);
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
      <BackLink to={SUPPORT_ROUTES.list} label="Support" />

      <PageHeader
        title={ticket.subject}
        description={`${ticket.category} · Raised ${formatDateTime(ticket.createdAt)}`}
        actions={<Badge variant={STATUS_BADGE[ticket.status]}>{ticket.status.replace(/_/g, ' ')}</Badge>}
      />

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
            <div key={message.id} className={message.authorType === 'PLATFORM_ADMIN' ? 'rounded-md bg-muted/50 p-3' : 'rounded-md border p-3'}>
              <div className="mb-1 flex items-center justify-between text-xs text-muted-foreground">
                <span className="font-medium text-foreground">
                  {message.authorType === 'PLATFORM_ADMIN' ? 'Hardware ERP Support' : message.authorName}
                </span>
                <span>{formatDateTime(message.createdAt)}</span>
              </div>
              <p className="whitespace-pre-wrap text-sm">{message.message}</p>
            </div>
          ))}

          {ticket.status !== 'CLOSED' ? (
            <div className="flex flex-col gap-2 pt-2 sm:flex-row">
              <textarea
                rows={3}
                placeholder="Write a reply…"
                value={reply}
                onChange={(e) => setReply(e.target.value)}
                className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              />
              <Button onClick={() => void submitReply()} loading={sending} className="sm:self-end">
                <Send className="h-4 w-4" />
                Send
              </Button>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">This ticket is closed.</p>
          )}
        </CardContent>
      </Card>
    </>
  );
}
