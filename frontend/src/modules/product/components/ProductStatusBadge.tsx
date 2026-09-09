import { StatusBadge } from '@/shared/components/StatusBadge';
import type { BrandStatus, CategoryStatus, ProductStatus } from '../types';

/**
 * Active is green, Inactive is red. Inactive was grey (`secondary`), which put
 * "this product cannot be sold" in the same colour the UI uses for neutral
 * metadata - on a long catalogue the state was effectively invisible.
 *
 * The wording stays "Active"/"Inactive" rather than "Available"/"Not
 * available": it is the value the API returns, the value the status filter
 * offers, and the value the form's own dropdown sets, so renaming it only
 * here would put three different words on one concept.
 */
const LABEL: Record<'ACTIVE' | 'INACTIVE', string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

const TONE = {
  ACTIVE: 'positive',
  INACTIVE: 'negative',
} as const;

export function ProductStatusBadge({ status }: { status: ProductStatus }) {
  return <StatusBadge tone={TONE[status]} label={LABEL[status]} />;
}

export function CategoryStatusBadge({ status }: { status: CategoryStatus }) {
  return <StatusBadge tone={TONE[status]} label={LABEL[status]} />;
}

export function BrandStatusBadge({ status }: { status: BrandStatus }) {
  return <StatusBadge tone={TONE[status]} label={LABEL[status]} />;
}
