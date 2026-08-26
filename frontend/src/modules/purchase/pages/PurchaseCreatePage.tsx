import { useNavigate } from 'react-router-dom';
import { PageHeader } from '@/shared/components/PageHeader';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PurchaseForm } from '../forms/PurchaseForm';
import { purchaseService } from '../services/purchaseService';
import { PURCHASE_ROUTES } from '../constants';
import type { PurchaseRequest } from '../types';

export function PurchaseCreatePage() {
  const navigate = useNavigate();
  const toast = useToast();

  const handleSubmit = async (request: PurchaseRequest) => {
    const purchase = await purchaseService.create(request);
    toast.success(`Purchase ${purchase.purchaseNumber} recorded, stock updated.`);
    navigate(PURCHASE_ROUTES.detail(purchase.id), { replace: true });
  };

  return (
    <>
      <PageHeader title="New purchase" description="Record a bill received from a supplier - stock updates immediately." />
      <div className="max-w-4xl">
        <PurchaseForm onSubmit={handleSubmit} onCancel={() => navigate(PURCHASE_ROUTES.list)} />
      </div>
    </>
  );
}
