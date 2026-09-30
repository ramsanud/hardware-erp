import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bell, BellOff, CheckCheck, Loader2, Radar } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent } from '@/shared/components/ui/card';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { cn } from '@/shared/lib/utils';
import { ApiError, type PageResponse } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { SUBSTITUTE_ROUTES } from '@/modules/substitute/constants';
import { discoveryService } from '../services/discoveryService';
import type { OwnerNotificationResponse } from '../types';

/** CR-090. The shop's in-app notifications. A PRODUCT_DISCOVERY one links to its product request. */
export function NotificationsPage() {
  const toast = useToast();
  const [unreadOnly, setUnreadOnly] = useState(true);
  const [page, setPage] = useState<PageResponse<OwnerNotificationResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    discoveryService.notifications(unreadOnly)
      .then(setPage)
      .catch((caught) => setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 })))
      .finally(() => setLoading(false));
  }, [unreadOnly]);

  useEffect(load, [load]);

  const markRead = async (id: number) => {
    try {
      const updated = await discoveryService.markRead(id);
      setPage((p) => p ? { ...p, content: p.content.map((n) => (n.id === id ? updated : n)) } : p);
    } catch (caught) {
      toast.error(caught, 'Could not mark as read.');
    }
  };

  const markAll = async () => {
    try {
      await discoveryService.markAllRead();
      load();
    } catch (caught) {
      toast.error(caught, 'Could not mark all as read.');
    }
  };

  const linkFor = (n: OwnerNotificationResponse) =>
    n.referenceType === 'PRODUCT_REQUEST' && n.referenceId ? SUBSTITUTE_ROUTES.detail(n.referenceId) : null;

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        title="Notifications"
        description="Alerts for your shop - nearby product availability and, later, daily summaries."
        actions={(
          <div className="flex flex-wrap gap-2">
            <Button variant={unreadOnly ? 'default' : 'outline'} size="sm" onClick={() => setUnreadOnly(true)}>Unread</Button>
            <Button variant={!unreadOnly ? 'default' : 'outline'} size="sm" onClick={() => setUnreadOnly(false)}>All</Button>
            <Button variant="outline" size="sm" onClick={markAll}><CheckCheck className="mr-2 h-4 w-4" /> Mark all read</Button>
          </div>
        )}
      />

      {error ? (
        <Card><ErrorState error={error} onRetry={load} /></Card>
      ) : loading || !page ? (
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" /></div>
      ) : page.content.length === 0 ? (
        <Card><CardContent className="py-10">
          <EmptyState icon={unreadOnly ? BellOff : Bell} title={unreadOnly ? 'Nothing unread' : 'No notifications yet'}
                      description="When a nearby participating shop may have a product you are out of, it shows up here." />
        </CardContent></Card>
      ) : (
        <div className="space-y-2">
          {page.content.map((n) => {
            const to = linkFor(n);
            return (
              <Card key={n.id} className={cn(!n.readAt && 'border-primary/40 bg-primary/5')}>
                <CardContent className="flex flex-wrap items-start justify-between gap-3 py-4">
                  <div className="flex min-w-0 flex-1 gap-3">
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10">
                      <Radar className="h-4 w-4 text-primary" />
                    </div>
                    <div className="min-w-0">
                      <p className={cn('font-medium', !n.readAt && 'text-foreground')}>{n.title}</p>
                      <p className="mt-0.5 text-sm text-muted-foreground">{n.body}</p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {new Date(n.createdAt).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
                      </p>
                    </div>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    {to ? (
                      <Button size="sm" asChild onClick={() => { if (!n.readAt) void markRead(n.id); }}>
                        <Link to={to}>View nearby availability</Link>
                      </Button>
                    ) : null}
                    {!n.readAt ? (
                      <Button variant="ghost" size="sm" onClick={() => markRead(n.id)}>Mark read</Button>
                    ) : null}
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
