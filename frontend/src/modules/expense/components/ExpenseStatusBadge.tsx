import { Badge } from '@/shared/components/ui/badge';
import type { ExpenseStatus } from '../types';

const LABEL: Record<ExpenseStatus, string> = {
  ACTIVE: 'Active',
  CANCELLED: 'Cancelled',
};

const VARIANT: Record<ExpenseStatus, 'success' | 'secondary'> = {
  ACTIVE: 'success',
  CANCELLED: 'secondary',
};

export function ExpenseStatusBadge({ status }: { status: ExpenseStatus }) {
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}
