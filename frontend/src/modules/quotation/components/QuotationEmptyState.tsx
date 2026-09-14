import { FilterX, Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';

interface QuotationEmptyStateProps {
  /** True when a search, status or date filter is set - "Clear filters" is only offered when there is something to clear. */
  filtered: boolean;
  onCreate: () => void;
  onClearFilters: () => void;
}

/**
 * CR-083. An empty list is either a brand-new shop (nothing quoted yet) or a
 * filter that matched nothing; the copy and the second button change with
 * that, the illustration does not. The drawing is inline SVG on tokens -
 * currentColor and the primary / muted hues - so it follows every colour
 * theme and both modes like the rest of the page.
 */
export function QuotationEmptyState({ filtered, onCreate, onClearFilters }: QuotationEmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 px-6 py-14 text-center" data-testid="quotation-empty">
      <svg width="132" height="104" viewBox="0 0 132 104" fill="none" aria-hidden className="text-muted-foreground">
        <rect x="30" y="8" width="72" height="88" rx="8" className="fill-muted" />
        <rect x="38" y="2" width="56" height="88" rx="8" className="fill-card stroke-border" strokeWidth="1.5" />
        <rect x="48" y="16" width="22" height="4" rx="2" className="fill-primary/40" />
        <rect x="48" y="28" width="36" height="3" rx="1.5" fill="currentColor" opacity="0.35" />
        <rect x="48" y="37" width="30" height="3" rx="1.5" fill="currentColor" opacity="0.35" />
        <rect x="48" y="46" width="34" height="3" rx="1.5" fill="currentColor" opacity="0.35" />
        <rect x="48" y="60" width="16" height="3" rx="1.5" fill="currentColor" opacity="0.5" />
        <rect x="70" y="60" width="14" height="3" rx="1.5" className="fill-primary/60" />
        <circle cx="96" cy="78" r="16" className="fill-primary/10 stroke-primary" strokeWidth="1.5" />
        <path d="M96 71v14M89 78h14" className="stroke-primary" strokeWidth="2" strokeLinecap="round" />
      </svg>
      <div>
        <p className="font-medium">{filtered ? 'No quotations match these filters' : 'No quotations yet'}</p>
        <p className="mt-1 max-w-sm text-sm text-muted-foreground">
          {filtered
            ? 'Try a different search, status or date range - or start a fresh quote for this customer.'
            : 'Quote a price for a customer without touching stock. Convert it to an invoice when they say yes.'}
        </p>
      </div>
      <div className="flex flex-wrap items-center justify-center gap-2">
        <PermissionGate permission={PERMISSIONS.QUOTATION_MANAGE}>
          <Button onClick={onCreate}>
            <Plus className="h-4 w-4" />
            Create new quotation
          </Button>
        </PermissionGate>
        {filtered ? (
          <Button variant="outline" onClick={onClearFilters}>
            <FilterX className="h-4 w-4" />
            Clear filters
          </Button>
        ) : null}
      </div>
    </div>
  );
}
