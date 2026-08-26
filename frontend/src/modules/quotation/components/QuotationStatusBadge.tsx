import { Badge } from '@/shared/components/ui/badge';
import type { QuotationStatus } from '../types';

const LABEL: Record<QuotationStatus, string> = {
  DRAFT: 'Draft',
  SENT: 'Sent',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
  EXPIRED: 'Expired',
  CONVERTED: 'Converted',
};

const VARIANT: Record<QuotationStatus, 'success' | 'warning' | 'secondary' | 'destructive'> = {
  DRAFT: 'secondary',
  SENT: 'warning',
  ACCEPTED: 'success',
  REJECTED: 'destructive',
  EXPIRED: 'destructive',
  CONVERTED: 'success',
};

/** expired overrides the raw status label - EXPIRED is never actually stored (CR-022), it's computed. */
export function QuotationStatusBadge({ status, expired }: { status: QuotationStatus; expired?: boolean }) {
  if (expired && (status === 'DRAFT' || status === 'SENT' || status === 'ACCEPTED')) {
    return <Badge variant="destructive">Expired</Badge>;
  }
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}
