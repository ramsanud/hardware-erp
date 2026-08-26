import { useEffect, useRef, useState } from 'react';
import { Plus, Search } from 'lucide-react';
import { Input } from '@/shared/components/ui/input';
import { Button } from '@/shared/components/ui/button';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { productService } from '@/modules/product/services/productService';
import { categoryService } from '@/modules/product/services/categoryService';
import { brandService } from '@/modules/product/services/brandService';
import { toProductRequest } from '@/modules/product/lib/toProductRequest';
import { ProductForm } from '@/modules/product/forms/ProductForm';
import type { ProductSummaryResponse, CategoryResponse, BrandResponse } from '@/modules/product/types';
import type { ProductValues } from '@/modules/product/validation/schemas';

interface ProductPickerProps {
  onPick: (product: ProductSummaryResponse) => void;
  /** Products already added to the invoice - shown greyed out, not re-addable directly. */
  excludeIds: number[];
}

/**
 * A debounced search box with a dropdown of matches. No Combobox primitive
 * exists in shared/ui yet, so this is the minimal version rather than
 * pulling in a new dependency for one screen.
 */
export function ProductPicker({ onPick, excludeIds }: ProductPickerProps) {
  const { hasPermission } = useAuth();
  const canAddProduct = hasPermission(PERMISSIONS.PRODUCT_MANAGE);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ProductSummaryResponse[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Inline "Add product" (CR-030 §15) - a product typed here that doesn't
  // exist yet shouldn't force the shop to abandon the invoice/quotation
  // they're mid-way through building. Categories/brands are only fetched
  // the first time the dialog actually opens, not on every wizard load.
  const [addingProduct, setAddingProduct] = useState(false);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [brands, setBrands] = useState<BrandResponse[]>([]);
  const [catalogueLoaded, setCatalogueLoaded] = useState(false);

  useEffect(() => {
    const term = query.trim();
    if (term.length < 1) {
      setResults([]);
      return;
    }
    setLoading(true);
    const timer = setTimeout(() => {
      productService.search({ search: term, status: 'ACTIVE', size: 8 })
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

  const openAddProduct = () => {
    setOpen(false);
    setAddingProduct(true);
    if (!catalogueLoaded) {
      Promise.all([categoryService.list(), brandService.list()]).then(([cats, brs]) => {
        setCategories(cats);
        setBrands(brs);
        setCatalogueLoaded(true);
      });
    }
  };

  const handleCreateProduct = async (values: ProductValues) => {
    const created = await productService.create(toProductRequest(values));
    setAddingProduct(false);
    setQuery('');
    onPick({
      id: created.id,
      productCode: created.productCode,
      productName: created.productName,
      categoryName: created.categoryName,
      brandName: created.brandName,
      unit: created.unit,
      sellingPriceDisplay: created.sellingPriceDisplay,
      gstRatePercent: String(created.gstRatePercent),
      status: created.status,
      hasImage: created.hasImage,
    });
  };

  return (
    <div ref={containerRef} className="relative">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input
          value={query}
          onChange={(event) => { setQuery(event.target.value); setOpen(true); }}
          onFocus={() => setOpen(true)}
          placeholder="Search by product name, code or barcode…"
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
              <div className="px-3 py-3 text-sm text-muted-foreground">
                <p>No matching products.</p>
                {canAddProduct ? (
                  <Button type="button" variant="ghost" size="sm" className="mt-1 h-auto px-0 py-1 text-primary hover:bg-transparent hover:underline"
                          onClick={openAddProduct}>
                    <Plus className="h-3.5 w-3.5" />
                    Add &quot;{query.trim()}&quot; as a new product
                  </Button>
                ) : null}
              </div>
            ) : (
              results.map((product) => {
                const alreadyAdded = excludeIds.includes(product.id);
                return (
                  <button
                    key={product.id}
                    type="button"
                    disabled={alreadyAdded}
                    onClick={() => {
                      onPick(product);
                      setQuery('');
                      setResults([]);
                      setOpen(false);
                    }}
                    className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-accent disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <span className="min-w-0">
                      <span className="block truncate font-medium">{product.productName}</span>
                      <span className="block text-xs text-muted-foreground">
                        {product.productCode} · {alreadyAdded ? 'Already added' : `₹${product.sellingPriceDisplay} / ${product.unit}`}
                      </span>
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </div>
      ) : null}

      <Dialog open={addingProduct} onOpenChange={setAddingProduct}>
        <DialogContent className="max-w-2xl">
          <DialogHeader><DialogTitle>Add product</DialogTitle></DialogHeader>
          {catalogueLoaded ? (
            <ProductForm
              categories={categories} brands={brands}
              initialProductName={query.trim()}
              onSubmit={handleCreateProduct}
              onCancel={() => setAddingProduct(false)}
              onCategoryCreated={(category) => setCategories((current) => [...current, category])}
              onBrandCreated={(brand) => setBrands((current) => [...current, brand])}
            />
          ) : (
            <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
