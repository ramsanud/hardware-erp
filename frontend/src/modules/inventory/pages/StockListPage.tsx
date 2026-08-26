import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Boxes } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import { Checkbox } from '@/shared/components/ui/checkbox';
import { Input } from '@/shared/components/ui/input';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { SearchInput } from '@/shared/components/SearchInput';
import { FormField } from '@/shared/components/FormField';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { useToast } from '@/modules/auth/hooks/useToast';
import { stockService } from '../services/stockService';
import type { StockResponse } from '../types';

const adjustSchema = z.object({
  quantityChange: z.string().trim().min(1, 'Enter a quantity').refine((v) => Number(v) !== 0, 'Cannot be zero'),
  notes: z.string().trim().max(255).optional().or(z.literal('')),
});
type AdjustValues = z.infer<typeof adjustSchema>;

export function StockListPage() {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const [search, setSearch] = useState('');
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [adjusting, setAdjusting] = useState<StockResponse | null>(null);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, lowStockOnly, size]);

  const fetcher = useCallback(
    () => stockService.search({ search: debouncedSearch || undefined, lowStockOnly, page, size }),
    [debouncedSearch, lowStockOnly, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, lowStockOnly, page, size]);

  const {
    register, handleSubmit, reset, formState: { errors, isSubmitting },
  } = useForm<AdjustValues>({ resolver: zodResolver(adjustSchema), defaultValues: { quantityChange: '', notes: '' } });

  const submitAdjust = handleSubmit(async (values) => {
    if (!adjusting) return;
    try {
      await stockService.adjust(adjusting.productId, {
        quantityChange: Number(values.quantityChange),
        notes: values.notes || null,
      });
      toast.success('Stock adjusted.');
      setAdjusting(null);
      reset();
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not adjust stock.');
    }
  });

  return (
    <>
      <PageHeader title="Stock" description="Current quantity on hand for every product." />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Product name or code…" />
        <label className="flex items-center gap-2 text-sm">
          <Checkbox checked={lowStockOnly} onCheckedChange={(v) => setLowStockOnly(Boolean(v))} />
          Low stock only
        </label>
      </div>

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead>Quantity on hand</TableHead>
                  <TableHead className="hidden sm:table-cell">Reorder level</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={5} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow key={row.productId}>
                      <TableCell>
                        <span className="font-medium">{row.productName}</span>
                        <span className="tabular mt-0.5 block text-xs text-muted-foreground">{row.productCode}</span>
                      </TableCell>
                      <TableCell className="tabular">{row.quantityOnHand} {row.unit}</TableCell>
                      <TableCell className="tabular hidden sm:table-cell">{row.reorderLevel}</TableCell>
                      <TableCell>
                        <Badge variant={row.lowStock ? 'warning' : 'success'}>
                          {row.lowStock ? 'Low stock' : 'OK'}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {hasPermission(PERMISSIONS.INVENTORY_ADJUST) ? (
                          <Button variant="ghost" size="sm" onClick={() => setAdjusting(row)}>Adjust</Button>
                        ) : null}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              )}
            </Table>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState icon={Boxes} title="No products match these filters" />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <Dialog open={adjusting !== null} onOpenChange={(open) => { if (!open) { setAdjusting(null); reset(); } }}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader><DialogTitle>Adjust stock — {adjusting?.productName}</DialogTitle></DialogHeader>
          <form onSubmit={submitAdjust} className="space-y-4" noValidate>
            <p className="text-sm text-muted-foreground">
              Currently {adjusting?.quantityOnHand} {adjusting?.unit}. Enter a positive number to
              add stock, or a negative number to remove it.
            </p>
            <FormField id="quantityChange" label="Quantity change" error={errors.quantityChange?.message} required>
              <Input id="quantityChange" type="number" step="any" autoFocus placeholder="e.g. 10 or -5"
                     {...register('quantityChange')} />
            </FormField>
            <FormField id="notes" label="Reason (optional)" error={errors.notes?.message}>
              <Input id="notes" placeholder="Stock take, damage, found stock…" {...register('notes')} />
            </FormField>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setAdjusting(null)} disabled={isSubmitting}>
                Cancel
              </Button>
              <Button type="submit" loading={isSubmitting}>Save adjustment</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
