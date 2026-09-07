import { StatusBadge } from '@/shared/components/StatusBadge';
import type { CouponStatus } from '../types';

const LABEL: Record<CouponStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

const TONE = { ACTIVE: 'positive', INACTIVE: 'negative' } as const;

export function CouponStatusBadge({ status }: { status: CouponStatus }) {
  return <StatusBadge tone={TONE[status]} label={LABEL[status]} />;
}
