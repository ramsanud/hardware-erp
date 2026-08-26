import { Badge } from '@/shared/components/ui/badge';
import type { InvoiceStatus } from '../types';

const LABEL: Record<InvoiceStatus, string> = {
  UNPAID: 'Unpaid',
  PARTIALLY_PAID: 'Partially paid',
  PAID: 'Paid',
  CANCELLED: 'Cancelled',
};

const VARIANT: Record<InvoiceStatus, 'success' | 'warning' | 'secondary' | 'destructive'> = {
  UNPAID: 'warning',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
  CANCELLED: 'destructive',
};

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}
