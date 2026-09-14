import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Clock, FileText } from 'lucide-react';
import { Card, CardContent, CardHeader } from '@/shared/components/ui/card';
import { EmptyState } from '@/shared/components/EmptyState';
import { formatRelative } from '@/shared/lib/utils';
import { AUTH_ROUTES } from '@/modules/auth/constants';
import { activityLogService } from '@/modules/activity/services/activityLogService';
import type { ActivityLogResponse } from '@/modules/activity/types';

/**
 * CR-082. The last few business changes, from the activity log CR-072 made
 * readable. Rendered only for AUDIT_VIEW holders (the caller gates it) -
 * the endpoint would 403 anyone else, and an empty card that says "no
 * activity" to someone who is not allowed to see it would be a lie.
 */
const VERB: Record<ActivityLogResponse['action'], string> = {
  CREATE: 'created',
  UPDATE: 'updated',
  DELETE: 'removed',
  IMPORT: 'imported',
};

function describe(entry: ActivityLogResponse): string {
  const what = entry.entityLabel ?? `${entry.entityType.toLowerCase()}${entry.entityId ? ` #${entry.entityId}` : ''}`;
  return `${entry.fullName ?? 'System'} ${VERB[entry.action] ?? entry.action.toLowerCase()} ${what}`;
}

export function RecentActionsCard() {
  const [entries, setEntries] = useState<ActivityLogResponse[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    activityLogService.search({ size: 5 })
      .then((page) => { if (!cancelled) setEntries(page.content); })
      .catch(() => { if (!cancelled) setEntries([]); });
    return () => { cancelled = true; };
  }, []);

  return (
    <Card className="h-full">
      <CardHeader className="flex-row items-start justify-between gap-3 space-y-0 pb-3">
        <div className="flex items-center gap-3.5">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Clock className="h-5 w-5" aria-hidden />
          </span>
          <div>
            <p className="text-base font-semibold">Recent Actions</p>
            <p className="text-xs text-muted-foreground">Latest updates from your shop</p>
          </div>
        </div>
        <Link
          to={AUTH_ROUTES.activityLog}
          className="flex shrink-0 items-center gap-1 pt-1 text-xs text-muted-foreground hover:text-foreground"
        >
          View all <ArrowRight className="h-3 w-3" aria-hidden />
        </Link>
      </CardHeader>
      <CardContent className="space-y-1">
        {entries === null ? (
          <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>
        ) : entries.length === 0 ? (
          <EmptyState icon={FileText} title="No recent activity" description="You'll see your latest actions here." />
        ) : (
          entries.map((entry) => (
            <div key={entry.id} className="flex items-center justify-between gap-3 rounded-md px-2 py-2 text-sm">
              <span className="min-w-0 truncate">{describe(entry)}</span>
              <span className="shrink-0 text-xs text-muted-foreground">{formatRelative(entry.createdAt)}</span>
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
}
