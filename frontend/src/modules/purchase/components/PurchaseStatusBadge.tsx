import { Badge } from '@/shared/components/ui/badge';
import type { PurchaseStatus } from '../types';

const LABEL: Record<PurchaseStatus, string> = {
  DRAFT: 'Draft',
  RECEIVED: 'Received',
  PARTIALLY_PAID: 'Partially paid',
  PAID: 'Paid',
  CANCELLED: 'Cancelled',
};

const VARIANT: Record<PurchaseStatus, 'success' | 'warning' | 'secondary' | 'destructive'> = {
  DRAFT: 'secondary',
  RECEIVED: 'warning',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
  CANCELLED: 'destructive',
};

export function PurchaseStatusBadge({ status }: { status: PurchaseStatus }) {
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}
