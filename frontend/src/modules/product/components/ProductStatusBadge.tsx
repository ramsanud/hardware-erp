import { Badge } from '@/shared/components/ui/badge';
import type { BrandStatus, CategoryStatus, ProductStatus } from '../types';

const LABEL: Record<'ACTIVE' | 'INACTIVE', string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

const VARIANT: Record<'ACTIVE' | 'INACTIVE', 'success' | 'secondary'> = {
  ACTIVE: 'success',
  INACTIVE: 'secondary',
};

export function ProductStatusBadge({ status }: { status: ProductStatus }) {
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}

export function CategoryStatusBadge({ status }: { status: CategoryStatus }) {
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}

export function BrandStatusBadge({ status }: { status: BrandStatus }) {
  return <Badge variant={VARIANT[status]}>{LABEL[status]}</Badge>;
}
