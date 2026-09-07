import { StatusBadge } from '@/shared/components/StatusBadge';
import type { WorkerStatus } from '../types';

const LABEL: Record<WorkerStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

const TONE = { ACTIVE: 'positive', INACTIVE: 'negative' } as const;

export function WorkerStatusBadge({ status }: { status: WorkerStatus }) {
  return <StatusBadge tone={TONE[status]} label={LABEL[status]} />;
}
