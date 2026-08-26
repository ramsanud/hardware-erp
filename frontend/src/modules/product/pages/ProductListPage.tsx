import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ImageIcon, MoreHorizontal, Package, Pencil, Plus, Trash2, Upload,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { SearchInput } from '@/shared/components/SearchInput';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { UnsavedChangesDialog } from '@/shared/components/UnsavedChangesDialog';
import { useAuthenticatedImage } from '@/shared/hooks/useAuthenticatedImage';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PRODUCT_ROUTES, PRODUCT_STATUS_OPTIONS } from '../constants';
import { categoryService } from '../services/categoryService';
import { brandService } from '../services/brandService';
import { productService } from '../services/productService';
import { ProductForm, PRODUCT_FORM_ID } from '../forms/ProductForm';
import { ProductStatusBadge } from '../components/ProductStatusBadge';
import { ImportProductsDialog } from '../components/ImportProductsDialog';
import { toProductRequest } from '../lib/toProductRequest';
import type {
  BrandResponse, CategoryResponse, ProductResponse, ProductStatus, ProductSummaryResponse,
} from '../types';
import type { ProductValues } from '../validation/schemas';

const ALL = '__all__';

function ProductThumbnail({ productId, hasImage, productName }: { productId: number; hasImage: boolean; productName: string }) {
  const src = useAuthenticatedImage(hasImage ? productService.imageUrl(productId) : null);
  return (
    <span className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-md border bg-muted">
      {src ? (
        <img src={src} alt={productName} className="h-full w-full object-cover" />
      ) : (
        <ImageIcon className="h-4 w-4 text-muted-foreground" aria-hidden />
      )}
    </span>
  );
}

export function ProductListPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { hasPermission } = useAuth();
  // Editing a product means seeing and re-submitting its purchase price
  // (ProductForm requires it). A PRODUCT_MANAGE holder without
  // PRODUCT_VIEW_COST would only ever see a masked/blank cost, and
  // resubmitting it would silently zero out the real purchase price - so
  // Edit is only offered when both permissions are held.
  const canEdit = hasPermission(PERMISSIONS.PRODUCT_MANAGE) && hasPermission(PERMISSIONS.PRODUCT_VIEW_COST);

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [categoryId, setCategoryId] = useState<string>(ALL);
  const [brandId, setBrandId] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [brands, setBrands] = useState<BrandResponse[]>([]);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<ProductSummaryResponse | null>(null);
  const [editingFull, setEditingFull] = useState<ProductResponse | null>(null);
  const [deleting, setDeleting] = useState<ProductSummaryResponse | null>(null);
  const [formDirty, setFormDirty] = useState(false);
  const [confirmingClose, setConfirmingClose] = useState(false);
  const [importing, setImporting] = useState(false);

  const requestCloseCreate = () => {
    if (formDirty) { setConfirmingClose(true); return; }
    setCreating(false);
  };
  const requestCloseEdit = () => {
    if (formDirty) { setConfirmingClose(true); return; }
    setEditing(null);
  };

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, categoryId, brandId, size]);

  const fetcher = useCallback(
    () => productService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as ProductStatus),
      categoryId: categoryId === ALL ? undefined : Number(categoryId),
      brandId: brandId === ALL ? undefined : Number(brandId),
      page,
      size,
      sortBy: 'productName',
      sortDir: 'asc',
    }),
    [debouncedSearch, status, categoryId, brandId, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher,
    [debouncedSearch, status, categoryId, brandId, page, size]);

  useEffect(() => {
    void (async () => {
      try {
        setCategories(await categoryService.list());
      } catch {
        setCategories([]);
      }
      try {
        setBrands(await brandService.list());
      } catch {
        setBrands([]);
      }
    })();
  }, []);

  useEffect(() => {
    if (!editing) { setEditingFull(null); return; }
    void (async () => {
      try {
        setEditingFull(await productService.get(editing.id));
      } catch (caught) {
        toast.error(caught, 'Could not load this product.');
        setEditing(null);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editing]);

  // Inline "Add new category/brand" inside ProductForm (CR-024) calls these
  // so the filter dropdowns above stay in sync with what was just created
  // there, without a full reload of the product list.
  const handleCategoryCreated = (category: CategoryResponse) => {
    setCategories((current) => [...current, category].sort((a, b) => a.categoryName.localeCompare(b.categoryName)));
  };
  const handleBrandCreated = (brand: BrandResponse) => {
    setBrands((current) => [...current, brand].sort((a, b) => a.brandName.localeCompare(b.brandName)));
  };

  const handleCreate = async (values: ProductValues) => {
    await productService.create(toProductRequest(values));
    setCreating(false);
    toast.success('Product created.');
    await reload();
  };

  const handleUpdate = async (values: ProductValues) => {
    if (!editingFull) return;
    await productService.update(editingFull.id, toProductRequest(values));
    setEditing(null);
    toast.success('Product updated.');
    await reload();
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await productService.remove(deleting.id);
      toast.success(`${deleting.productName} has been deactivated.`);
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not deactivate this product.');
      throw caught;
    }
  };

  return (
    <>
      <PageHeader
        title="Products"
        description="The sellable catalogue: what the shop stocks and sells."
        actions={
          <PermissionGate permission={PERMISSIONS.PRODUCT_MANAGE}>
            <div className="flex items-center gap-2">
              <Button variant="outline" onClick={() => setImporting(true)}>
                <Upload className="h-4 w-4" />
                <span className="hidden sm:inline">Import</span>
              </Button>
              <Button onClick={() => { setFormDirty(false); setCreating(true); }}>
                <Plus className="h-4 w-4" />
                <span className="hidden sm:inline">Add product</span>
              </Button>
            </div>
          </PermissionGate>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch}
                     placeholder="Name, code, barcode, model…" />

        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-40"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {PRODUCT_STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={categoryId} onValueChange={setCategoryId}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Category" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All categories</SelectItem>
            {categories.map((option) => (
              <SelectItem key={option.id} value={String(option.id)}>{option.categoryName}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={brandId} onValueChange={setBrandId}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Brand" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All brands</SelectItem>
            {brands.map((option) => (
              <SelectItem key={option.id} value={String(option.id)}>{option.brandName}</SelectItem>
            ))}
          </SelectContent>
        </Select>
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
                  <TableHead className="hidden sm:table-cell">Category</TableHead>
                  <TableHead className="hidden lg:table-cell">Brand</TableHead>
                  <TableHead className="hidden md:table-cell">Selling price</TableHead>
                  <TableHead className="hidden xl:table-cell">GST</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={7} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow
                      key={row.id}
                      className="cursor-pointer"
                      onClick={() => navigate(PRODUCT_ROUTES.detail(row.id))}
                    >
                      <TableCell>
                        <div className="flex items-center gap-3">
                          <ProductThumbnail productId={row.id} hasImage={row.hasImage} productName={row.productName} />
                          <div>
                            <span className="font-medium">{row.productName}</span>
                            <span className="tabular mt-0.5 block text-xs text-muted-foreground">
                              {row.productCode}
                            </span>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell className="hidden sm:table-cell">{row.categoryName ?? '—'}</TableCell>
                      <TableCell className="hidden lg:table-cell">{row.brandName ?? '—'}</TableCell>
                      <TableCell className="tabular hidden md:table-cell">
                        ₹{row.sellingPriceDisplay} / {row.unit}
                      </TableCell>
                      <TableCell className="tabular hidden xl:table-cell">{row.gstRatePercent}%</TableCell>
                      <TableCell><ProductStatusBadge status={row.status} /></TableCell>
                      <TableCell onClick={(event) => event.stopPropagation()}>
                        <PermissionGate permission={PERMISSIONS.PRODUCT_MANAGE}>
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="icon" className="h-8 w-8"
                                      aria-label={`Actions for ${row.productName}`}>
                                <MoreHorizontal className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              {canEdit ? (
                                <DropdownMenuItem onClick={() => { setFormDirty(false); setEditing(row); }}>
                                  <Pencil className="h-4 w-4" />
                                  Edit
                                </DropdownMenuItem>
                              ) : null}
                              <DropdownMenuItem destructive onClick={() => setDeleting(row)}>
                                <Trash2 className="h-4 w-4" />
                                Deactivate
                              </DropdownMenuItem>
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </PermissionGate>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              )}
            </Table>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState
                icon={Package}
                title="No products match these filters"
                description="Try clearing the search box or the status filter."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <Dialog open={creating} onOpenChange={(open) => { if (!open) requestCloseCreate(); }}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader><DialogTitle>Add product</DialogTitle></DialogHeader>
          <ProductForm categories={categories} brands={brands}
                      onSubmit={handleCreate} onCancel={requestCloseCreate} onDirtyChange={setFormDirty}
                      onCategoryCreated={handleCategoryCreated} onBrandCreated={handleBrandCreated} />
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => { if (!open) requestCloseEdit(); }}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader><DialogTitle>Edit product</DialogTitle></DialogHeader>
          {editingFull ? (
            <ProductForm product={editingFull} categories={categories} brands={brands}
                        onSubmit={handleUpdate} onCancel={requestCloseEdit} onDirtyChange={setFormDirty}
                        onCategoryCreated={handleCategoryCreated} onBrandCreated={handleBrandCreated}
                        onImageChanged={reload} />
          ) : null}
        </DialogContent>
      </Dialog>

      <ImportProductsDialog
        open={importing}
        onOpenChange={setImporting}
        onImported={async () => { setImporting(false); await reload(); }}
      />

      <UnsavedChangesDialog
        open={confirmingClose}
        onContinueEditing={() => setConfirmingClose(false)}
        onDiscard={() => { setConfirmingClose(false); setFormDirty(false); setCreating(false); setEditing(null); }}
        formId={PRODUCT_FORM_ID}
      />

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Deactivate this product?"
        description={`${deleting?.productName ?? 'This product'} will no longer appear when raising a sale or purchase. Past history is retained.`}
        confirmLabel="Deactivate"
        destructive
        onConfirm={handleDelete}
      />
    </>
  );
}
