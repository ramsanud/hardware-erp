import { Badge } from '@/shared/components/ui/badge';
import type { ProjectOutcome, ProjectStatus } from '../types';

const STATUS_LABEL: Record<ProjectStatus, string> = {
  UPCOMING: 'Upcoming',
  IN_PROGRESS: 'In progress',
  ON_HOLD: 'On hold',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
};

const STATUS_VARIANT: Record<ProjectStatus, 'default' | 'secondary' | 'warning' | 'destructive' | 'success'> = {
  UPCOMING: 'default',
  IN_PROGRESS: 'default',
  ON_HOLD: 'warning',
  CANCELLED: 'destructive',
  COMPLETED: 'success',
};

export function ProjectStatusBadge({ status, overdue }: { status: ProjectStatus; overdue?: boolean }) {
  if (overdue && (status === 'UPCOMING' || status === 'IN_PROGRESS')) {
    return <Badge variant="destructive">Overdue</Badge>;
  }
  return <Badge variant={STATUS_VARIANT[status]}>{STATUS_LABEL[status]}</Badge>;
}

const OUTCOME_LABEL: Record<ProjectOutcome, string> = { SUCCESS: 'Success', FAILURE: 'Failure' };
const OUTCOME_VARIANT: Record<ProjectOutcome, 'success' | 'destructive'> = { SUCCESS: 'success', FAILURE: 'destructive' };

export function ProjectOutcomeBadge({ outcome }: { outcome: ProjectOutcome }) {
  return <Badge variant={OUTCOME_VARIANT[outcome]}>{OUTCOME_LABEL[outcome]}</Badge>;
}
