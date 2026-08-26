import { Badge } from '@/shared/components/ui/badge';
import type { AuditAction } from '../types';

/** Events that always warrant attention, regardless of the success flag. */
const ALARMING: AuditAction[] = [
  'REFRESH_TOKEN_REUSE_DETECTED',
  'ACCOUNT_LOCKED',
  'RATE_LIMIT_EXCEEDED',
];

export function AuditActionBadge({ action, success }: { action: AuditAction; success: boolean }) {
  const variant = ALARMING.includes(action)
    ? 'destructive'
    : success ? 'secondary' : 'warning';

  return (
    <Badge variant={variant} className="font-mono text-[11px]">
      {action.replaceAll('_', ' ').toLowerCase()}
    </Badge>
  );
}
