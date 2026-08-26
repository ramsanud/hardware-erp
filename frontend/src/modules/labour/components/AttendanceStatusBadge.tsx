import { Badge } from '@/shared/components/ui/badge';
import type { AttendanceStatus } from '../types';

const LABEL: Record<AttendanceStatus, string> = {
  PRESENT: 'Present',
  HALF_DAY: 'Half day',
  ABSENT: 'Absent',
};

const VARIANT: Record<AttendanceStatus, 'success' | 'warning' | 'secondary'> = {
  PRESENT: 'success',
  HALF_DAY: 'warning',
  ABSENT: 'secondary',
};

export function AttendanceStatusBadge({ status }: { status: AttendanceStatus }) {
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}
