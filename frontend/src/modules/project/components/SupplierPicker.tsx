import { useEffect, useRef, useState } from 'react';
import { Plus, Search } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { supplierService } from '@/modules/supplier/services/supplierService';
import { SupplierQuickAddDialog } from '@/modules/supplier/forms/SupplierQuickAddDialog';
import type { SupplierSummaryResponse } from '@/modules/supplier/types';

interface SupplierPickerProps {
  onPick: (supplier: SupplierSummaryResponse) => void;
}

/** Same pattern as ProductPicker/CustomerPicker. Supplier is optional on a project material (request §9-10), so this is only ever shown behind an explicit "add supplier" toggle, never forced. */
export function SupplierPicker({ onPick }: SupplierPickerProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SupplierSummaryResponse[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [addingSupplier, setAddingSupplier] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // A frontend gate only - POST /v1/suppliers is @PreAuthorize'd on the same
  // permission, so hiding the button is a courtesy, not the control.
  const { hasPermission } = useAuth();
  const canAddSupplier = hasPermission(PERMISSIONS.SUPPLIER_MANAGE);

  useEffect(() => {
    const term = query.trim();
    if (term.length < 1) {
      setResults([]);
      return;
    }
    setLoading(true);
    const timer = setTimeout(() => {
      supplierService.search({ search: term, size: 8 })
        .then((page) => setResults(page.content))
        .finally(() => setLoading(false));
    }, 250);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div ref={containerRef} className="relative">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input
          value={query}
          onChange={(event) => { setQuery(event.target.value); setOpen(true); }}
          onFocus={() => setOpen(true)}
          placeholder="Search by name, code or mobile…"
          className="pl-9"
          autoComplete="off"
        />
      </div>

      {open && query.trim().length > 0 ? (
        <div className="absolute z-20 mt-1 w-full overflow-hidden rounded-md border bg-popover shadow-md">
          <div className="max-h-64 overflow-y-auto">
            {loading ? (
              <p className="px-3 py-3 text-sm text-muted-foreground">Searching…</p>
            ) : results.length === 0 ? (
              // "No matching suppliers" used to be a dead end mid-purchase
              // (BUG-FE-022): the owner had to abandon the form, go and create
              // the supplier, and start again. Same inline-add contract as the
              // product picker and the work-type select.
              <div className="px-3 py-3 text-sm text-muted-foreground">
                <p>No matching suppliers.</p>
                {canAddSupplier ? (
                  <Button type="button" variant="ghost" size="sm"
                          className="mt-1 h-auto px-0 py-1 text-primary hover:bg-transparent hover:underline"
                          onClick={() => { setOpen(false); setAddingSupplier(true); }}>
                    <Plus className="h-3.5 w-3.5" />
                    Add &quot;{query.trim()}&quot; as a new supplier
                  </Button>
                ) : null}
              </div>
            ) : (
              results.map((supplier) => (
                <button
                  key={supplier.id}
                  type="button"
                  onClick={() => { onPick(supplier); setQuery(''); setResults([]); setOpen(false); }}
                  className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-accent"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{supplier.supplierName}</span>
                    <span className="block text-xs text-muted-foreground">{supplier.supplierCode}</span>
                  </span>
                </button>
              ))
            )}
          </div>
        </div>
      ) : null}

      <SupplierQuickAddDialog
        open={addingSupplier}
        onOpenChange={setAddingSupplier}
        initialName={query.trim()}
        onCreated={(supplier) => {
          // Select it straight away - the owner asked for this supplier by
          // name, so making them search for it again would be busywork.
          onPick(supplier);
          setQuery('');
          setResults([]);
          setOpen(false);
        }}
      />
    </div>
  );
}
