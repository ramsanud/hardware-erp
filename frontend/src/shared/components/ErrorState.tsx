import type { LucideIcon } from 'lucide-react';
import {
  AlertTriangle, Clock, FileQuestion, Hourglass, RefreshCw, ServerCrash, ShieldX, WifiOff,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { useOnlineStatus } from '@/shared/hooks/useOnlineStatus';
import type { ApiError } from '@/shared/types/api';

interface ErrorStateProps {
  error: ApiError;
  onRetry?: () => void;
}

interface Presentation {
  icon: LucideIcon;
  title: string;
  /** Omitted when the server's own message already says it. */
  description?: string;
  /** Icon tint. Only a failure the user cannot act on is drawn destructive. */
  tone: 'destructive' | 'warning' | 'muted';
}

/**
 * CR-100. What the failure looks like depends on what kind of failure it is.
 *
 * Before this, a 403, a dropped Wi-Fi connection and a 500 all drew the same
 * red triangle over the raw server message. The message was often right, but
 * the picture told the user "something broke" when the truth was "you are
 * offline" or "your role cannot see this" - both of which they can do
 * something about. The mapping is by code first (the codes GlobalExceptionHandler
 * actually emits, see ApiErrorCode) and by status second.
 */
function present(error: ApiError, online: boolean): Presentation {
  if (error.code === 'NETWORK_ERROR') {
    return online
      ? {
        icon: ServerCrash,
        title: 'Cannot reach the server',
        description: 'The connection was refused. Wait a moment and try again; if it keeps happening, the server may be down.',
        tone: 'warning',
      }
      : {
        icon: WifiOff,
        title: 'You are offline',
        description: 'Check your internet connection. This page will work again once you are back online.',
        tone: 'warning',
      };
  }
  if (error.code === 'TIMEOUT') {
    return { icon: Hourglass, title: 'The server is taking longer than usual', description: error.message, tone: 'warning' };
  }
  if (error.isRateLimited) {
    return { icon: Clock, title: 'Too many requests', description: 'Wait a moment before trying again.', tone: 'warning' };
  }
  if (error.isForbidden) {
    return {
      icon: ShieldX,
      title: 'You do not have access to this',
      description: 'Ask the shop owner to grant the required permission on your role.',
      tone: 'muted',
    };
  }
  if (error.status === 404) {
    return { icon: FileQuestion, title: 'Not found', description: error.message, tone: 'muted' };
  }
  if (error.status === 502 || error.status === 503 || error.status === 504) {
    return {
      icon: ServerCrash,
      title: 'The server is being updated',
      description: 'It should be back within a few minutes. Nothing you entered has been lost.',
      tone: 'warning',
    };
  }
  return { icon: AlertTriangle, title: error.message, tone: 'destructive' };
}

const TONE = {
  destructive: 'bg-destructive/10 text-destructive',
  warning: 'bg-warning/10 text-warning',
  muted: 'bg-muted text-muted-foreground',
} as const;

export function ErrorState({ error, onRetry }: ErrorStateProps) {
  const online = useOnlineStatus();
  const view = present(error, online);
  const Icon = view.icon;

  return (
    <div
      role="alert"
      data-error-code={error.code}
      className="flex flex-col items-center justify-center gap-3 px-6 py-16 text-center"
    >
      <div className={`rounded-full p-3 ${TONE[view.tone]}`}>
        <Icon className="h-6 w-6" aria-hidden />
      </div>
      <div>
        <p className="font-medium">{view.title}</p>
        {view.description ? (
          <p className="mt-1 max-w-sm text-sm text-muted-foreground">{view.description}</p>
        ) : null}
        {/* The request id is the only way support can find this in the logs. */}
        {error.requestId ? (
          <p className="mt-1 font-mono text-xs text-muted-foreground">
            Reference: {error.requestId}
          </p>
        ) : null}
      </div>
      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RefreshCw className="h-4 w-4" />
          Try again
        </Button>
      ) : null}
    </div>
  );
}
