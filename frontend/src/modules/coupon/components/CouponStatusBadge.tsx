import { Badge } from '@/shared/components/ui/badge';
import type { CouponStatus } from '../types';

const LABEL: Record<CouponStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

const VARIANT: Record<CouponStatus, 'success' | 'secondary'> = {
  ACTIVE: 'success',
  INACTIVE: 'secondary',
};

export function CouponStatusBadge({ status }: { status: CouponStatus }) {
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}
