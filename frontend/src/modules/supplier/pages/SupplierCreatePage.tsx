import { useNavigate } from 'react-router-dom';
import { PageHeader } from '@/shared/components/PageHeader';
import { useToast } from '@/modules/auth/hooks/useToast';
import { SupplierWizard } from '../forms/SupplierWizard';
import { supplierService } from '../services/supplierService';
import { toSupplierRequest } from '../lib/toSupplierRequest';
import { SUPPLIER_ROUTES } from '../constants';
import type { SupplierValues } from '../validation/schemas';

/**
 * A full page, not a modal - twenty fields across five steps does not fit
 * comfortably in a dialog at any viewport. The wizard supplies its own
 * fixed Back/Next navigation bar.
 */
export function SupplierCreatePage() {
  const navigate = useNavigate();
  const toast = useToast();

  const handleSubmit = async (values: SupplierValues) => {
    const supplier = await supplierService.create(toSupplierRequest(values));
    toast.success(`${supplier.supplierName} created.`);
    navigate(SUPPLIER_ROUTES.detail(supplier.id), { replace: true });
  };

  return (
    <>
      <PageHeader title="Add supplier" description="Basic details, contact, address, and bank information." />
      <SupplierWizard onSubmit={handleSubmit} onCancel={() => navigate(SUPPLIER_ROUTES.list)} />
    </>
  );
}
