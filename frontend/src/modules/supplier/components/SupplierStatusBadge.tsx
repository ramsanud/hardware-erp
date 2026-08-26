import { Badge } from '@/shared/components/ui/badge';
import type { SupplierStatus } from '../types';

const VARIANT: Record<SupplierStatus, 'success' | 'secondary' | 'destructive'> = {
  ACTIVE: 'success',
  INACTIVE: 'secondary',
  BLOCKED: 'destructive',
};

const LABEL: Record<SupplierStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  BLOCKED: 'Blocked',
};

export function SupplierStatusBadge({ status }: { status: SupplierStatus }) {
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}
