import { Badge } from '@/shared/components/ui/badge';
import type { RoleStatus, UserStatus } from '../types';

const USER_VARIANT: Record<UserStatus, 'success' | 'secondary' | 'destructive'> = {
  ACTIVE: 'success',
  INACTIVE: 'secondary',
  SUSPENDED: 'destructive',
};

const USER_LABEL: Record<UserStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  SUSPENDED: 'Suspended',
};

export function UserStatusBadge({ status }: { status: UserStatus }) {
  return <Badge variant={USER_VARIANT[status]}>{USER_LABEL[status]}</Badge>;
}

export function RoleStatusBadge({ status }: { status: RoleStatus }) {
  return (
    <Badge variant={status === 'ACTIVE' ? 'success' : 'secondary'}>
      {status === 'ACTIVE' ? 'Active' : 'Inactive'}
    </Badge>
  );
}
