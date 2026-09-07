import { StatusBadge, type StatusBadgeProps } from '@/shared/components/StatusBadge';
import type { SupplierStatus } from '../types';

/**
 * Three states, so Inactive and Blocked cannot both be plain red. Inactive
 * takes the soft red tint (consistent with every other module's Inactive),
 * Blocked takes the solid fill - louder for the state that was deliberately
 * acted on, and still distinguishable without relying on hue.
 */
const TONE: Record<SupplierStatus, StatusBadgeProps['tone']> = {
  ACTIVE: 'positive',
  INACTIVE: 'negative',
  BLOCKED: 'critical',
};

const LABEL: Record<SupplierStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  BLOCKED: 'Blocked',
};

export function SupplierStatusBadge({ status }: { status: SupplierStatus }) {
  return <StatusBadge tone={TONE[status]} label={LABEL[status]} />;
}
