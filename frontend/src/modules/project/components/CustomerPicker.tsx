import { useEffect, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { Input } from '@/shared/components/ui/input';
import { customerService } from '@/modules/customer/services/customerService';
import type { CustomerSummaryResponse } from '@/modules/customer/types';

interface CustomerPickerProps {
  onPick: (customer: CustomerSummaryResponse) => void;
  placeholder?: string;
}

/**
 * Server-side search over existing customers, matching ProductPicker's
 * pattern exactly - a project must reference a real customer master
 * record, never free-typed name/mobile text the way the older Invoice/
 * Quotation wizards accept (those predate the full Customer module).
 */
export function CustomerPicker({ onPick, placeholder }: CustomerPickerProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<CustomerSummaryResponse[]>([]);
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
      customerService.search({ search: term, status: 'ACTIVE', size: 8 })
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
          placeholder={placeholder ?? 'Search by name, code or mobile…'}
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
              <p className="px-3 py-3 text-sm text-muted-foreground">No matching customers.</p>
            ) : (
              results.map((customer) => (
                <button
                  key={customer.id}
                  type="button"
                  onClick={() => { onPick(customer); setQuery(''); setResults([]); setOpen(false); }}
                  className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-accent"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{customer.customerName}</span>
                    <span className="block text-xs text-muted-foreground">{customer.customerCode} · {customer.mobileNo}</span>
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
