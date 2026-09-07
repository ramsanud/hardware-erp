import { StatusBadge, type StatusBadgeProps } from '@/shared/components/StatusBadge';
import type { RoleStatus, UserStatus } from '../types';

/** Same three-state reasoning as SupplierStatusBadge: Suspended outranks Inactive. */
const USER_TONE: Record<UserStatus, StatusBadgeProps['tone']> = {
  ACTIVE: 'positive',
  INACTIVE: 'negative',
  SUSPENDED: 'critical',
};

const USER_LABEL: Record<UserStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  SUSPENDED: 'Suspended',
};

export function UserStatusBadge({ status }: { status: UserStatus }) {
  return <StatusBadge tone={USER_TONE[status]} label={USER_LABEL[status]} />;
}

export function RoleStatusBadge({ status }: { status: RoleStatus }) {
  return (
    <StatusBadge
      tone={status === 'ACTIVE' ? 'positive' : 'negative'}
      label={status === 'ACTIVE' ? 'Active' : 'Inactive'}
    />
  );
}
