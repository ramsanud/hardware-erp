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

/**
 * CR-083 palette: grey while it is only ours, blue once it is with the
 * customer, green when agreed, red when it died, violet once it became an
 * invoice - Converted and Accepted used to share green, which hid the one
 * distinction the list is most often scanned for.
 */
const VARIANT: Record<QuotationStatus, 'default' | 'success' | 'info' | 'secondary' | 'destructive'> = {
  DRAFT: 'secondary',
  SENT: 'default',
  ACCEPTED: 'success',
  REJECTED: 'destructive',
  EXPIRED: 'destructive',
  CONVERTED: 'info',
};

/** expired overrides the raw status label - EXPIRED is never actually stored (CR-022), it's computed. */
export function QuotationStatusBadge({ status, expired }: { status: QuotationStatus; expired?: boolean }) {
  if (expired && (status === 'DRAFT' || status === 'SENT' || status === 'ACCEPTED')) {
    return <Badge variant="destructive">Expired</Badge>;
  }
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}
