import { Badge } from '@/shared/components/ui/badge';
import type { WorkerStatus } from '../types';

const LABEL: Record<WorkerStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

const VARIANT: Record<WorkerStatus, 'success' | 'secondary'> = {
  ACTIVE: 'success',
  INACTIVE: 'secondary',
};

export function WorkerStatusBadge({ status }: { status: WorkerStatus }) {
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}
