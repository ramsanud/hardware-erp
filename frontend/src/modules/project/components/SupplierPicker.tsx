import { useEffect, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { Input } from '@/shared/components/ui/input';
import { supplierService } from '@/modules/supplier/services/supplierService';
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
  const containerRef = useRef<HTMLDivElement>(null);

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
              <p className="px-3 py-3 text-sm text-muted-foreground">No matching suppliers.</p>
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
    </div>
  );
}
