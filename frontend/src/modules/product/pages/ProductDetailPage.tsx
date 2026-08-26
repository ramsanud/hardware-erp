import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ImageIcon, Loader2, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { ApiError } from '@/shared/types/api';
import { formatDateTime } from '@/shared/lib/utils';
import { useAuthenticatedImage } from '@/shared/hooks/useAuthenticatedImage';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PRODUCT_ROUTES } from '../constants';
import { categoryService } from '../services/categoryService';
import { brandService } from '../services/brandService';
import { productService } from '../services/productService';
import { ProductForm } from '../forms/ProductForm';
import { ProductStatusBadge } from '../components/ProductStatusBadge';
import { toProductRequest } from '../lib/toProductRequest';
import type { BrandResponse, CategoryResponse, ProductResponse } from '../types';
import type { ProductValues } from '../validation/schemas';

function useProductDetail(id: number) {
  const [product, setProduct] = useState<ProductResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setProduct(await productService.get(id));
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void reload(); }, [reload]);

  return { product, loading, error, reload };
}

export function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const navigate = useNavigate();
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canEdit = hasPermission(PERMISSIONS.PRODUCT_MANAGE) && hasPermission(PERMISSIONS.PRODUCT_VIEW_COST);

  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [brands, setBrands] = useState<BrandResponse[]>([]);

  const validId = Boolean(params.id) && !Number.isNaN(id);
  const { product, loading, error, reload } = useProductDetail(validId ? id : -1);
  const imageSrc = useAuthenticatedImage(product?.hasImage ? productService.imageUrl(product.id) : null);

  useEffect(() => {
    void (async () => {
      try { setCategories(await categoryService.list()); } catch { setCategories([]); }
      try { setBrands(await brandService.list()); } catch { setBrands([]); }
    })();
  }, []);

  if (!validId) {
    return <Navigate to={PRODUCT_ROUTES.list} replace />;
  }

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  if (error || !product) {
    return (
      <ErrorState
        error={error ?? new ApiError({ message: 'Product not found', code: 'NOT_FOUND', status: 404 })}
        onRetry={reload}
      />
    );
  }

  const handleUpdate = async (values: ProductValues) => {
    await productService.update(product.id, toProductRequest(values));
    setEditing(false);
    toast.success('Product updated.');
    await reload();
  };

  const handleDeactivate = async () => {
    try {
      await productService.remove(product.id);
      toast.success(`${product.productName} has been deactivated.`);
      navigate(PRODUCT_ROUTES.list);
    } catch (caught) {
      toast.error(caught, 'Could not deactivate this product.');
      throw caught;
    }
  };

  return (
    <>
      <Link to={PRODUCT_ROUTES.list}
            className="mb-1 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" />
        Products
      </Link>

      <PageHeader
        title={product.productName}
        description={product.productCode}
        actions={
          <PermissionGate permission={PERMISSIONS.PRODUCT_MANAGE}>
            <div className="flex items-center gap-2">
              {canEdit ? (
                <Button variant="outline" onClick={() => setEditing(true)}>Edit</Button>
              ) : null}
              <Button variant="outline" className="text-destructive hover:text-destructive"
                      onClick={() => setDeleting(true)}>
                <Trash2 className="h-4 w-4" />
                <span className="hidden sm:inline">Deactivate</span>
              </Button>
            </div>
          </PermissionGate>
        }
      />

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="text-base">Overview</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-center pb-2">
              <span className="flex h-24 w-24 items-center justify-center overflow-hidden rounded-lg border bg-muted">
                {imageSrc ? (
                  <img src={imageSrc} alt={product.productName} className="h-full w-full object-cover" />
                ) : (
                  <ImageIcon className="h-8 w-8 text-muted-foreground" aria-hidden />
                )}
              </span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Status</span>
              <ProductStatusBadge status={product.status} />
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Category</span>
              <span>{product.categoryName ?? '—'}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Brand</span>
              <span>{product.brandName ?? '—'}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Unit</span>
              <span>{product.unit}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">GST rate</span>
              <span className="tabular">{product.gstRatePercent}%</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Created</span>
              <span className="tabular text-right">{formatDateTime(product.createdAt)}</span>
            </div>
          </CardContent>
        </Card>

        <div className="space-y-5 lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Identification</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Barcode</span>
                <span className="tabular">{product.barcode ?? '—'}</span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Manufacturer code</span>
                <span className="tabular">{product.manufacturerCode ?? '—'}</span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Model number</span>
                <span className="tabular">{product.modelNo ?? '—'}</span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">HSN/SAC code</span>
                <span className="tabular">{product.hsnCode ?? '—'}</span>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Pricing & stock thresholds</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
              <PermissionGate permission={PERMISSIONS.PRODUCT_VIEW_COST}>
                <div className="flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">Purchase price</span>
                  <span className="tabular">
                    {product.purchasePriceDisplay ? `₹${product.purchasePriceDisplay}` : '—'}
                  </span>
                </div>
              </PermissionGate>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Selling price</span>
                <span className="tabular">₹{product.sellingPriceDisplay}</span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">MRP</span>
                <span className="tabular">₹{product.mrpDisplay}</span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Minimum stock</span>
                <span className="tabular">{product.minimumStock}</span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Reorder level</span>
                <span className="tabular">{product.reorderLevel}</span>
              </div>
            </CardContent>
          </Card>

          {product.description ? (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Description</CardTitle>
              </CardHeader>
              <CardContent className="text-sm">{product.description}</CardContent>
            </Card>
          ) : null}
        </div>
      </div>

      <Dialog open={editing} onOpenChange={setEditing}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader><DialogTitle>Edit product</DialogTitle></DialogHeader>
          <ProductForm product={product} categories={categories} brands={brands}
                      onSubmit={handleUpdate} onCancel={() => setEditing(false)} onImageChanged={reload} />
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title="Deactivate this product?"
        description={`${product.productName} will no longer appear when raising a sale or purchase. Past history is retained.`}
        confirmLabel="Deactivate"
        destructive
        onConfirm={handleDeactivate}
      />
    </>
  );
}
