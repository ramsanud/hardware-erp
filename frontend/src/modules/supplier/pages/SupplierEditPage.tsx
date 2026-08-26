import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { Card } from '@/shared/components/ui/card';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { SupplierWizard } from '../forms/SupplierWizard';
import { supplierService } from '../services/supplierService';
import { toSupplierRequest } from '../lib/toSupplierRequest';
import { SUPPLIER_ROUTES } from '../constants';
import type { SupplierResponse } from '../types';
import type { SupplierValues } from '../validation/schemas';

export function SupplierEditPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const navigate = useNavigate();
  const toast = useToast();

  const [supplier, setSupplier] = useState<SupplierResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    if (Number.isNaN(id)) return;
    setLoading(true);
    supplierService.get(id)
      .then(setSupplier)
      .catch((caught) => setError(caught instanceof ApiError ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 })))
      .finally(() => setLoading(false));
  }, [id]);

  if (Number.isNaN(id)) return <Navigate to={SUPPLIER_ROUTES.list} replace />;

  const handleSubmit = async (values: SupplierValues) => {
    const updated = await supplierService.update(id, toSupplierRequest(values));
    toast.success(`${updated.supplierName} updated.`);
    navigate(SUPPLIER_ROUTES.detail(id), { replace: true });
  };

  if (error) return <Card><ErrorState error={error} onRetry={() => window.location.reload()} /></Card>;
  if (loading || !supplier) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  return (
    <>
      <PageHeader title={`Edit ${supplier.supplierName}`} description={supplier.supplierCode} />
      <SupplierWizard supplier={supplier} onSubmit={handleSubmit} onCancel={() => navigate(SUPPLIER_ROUTES.detail(id))} />
    </>
  );
}
