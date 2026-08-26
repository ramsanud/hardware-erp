import { useLocation, useNavigate } from 'react-router-dom';
import { PageHeader } from '@/shared/components/PageHeader';
import { useToast } from '@/modules/auth/hooks/useToast';
import {
  QuotationWizard, type QuotationWizardInitialCustomer, type QuotationWizardInitialItem,
} from '../forms/QuotationWizard';
import { quotationService } from '../services/quotationService';
import { QUOTATION_ROUTES } from '../constants';
import type { QuotationRequest } from '../types';

interface LocationState {
  customer?: QuotationWizardInitialCustomer;
  items?: QuotationWizardInitialItem[];
  /**
   * Present when editing an existing DRAFT/SENT quotation rather than creating
   * a new one. Reuses this page so the wizard's steps, totals and validation
   * cannot drift between the create and edit paths.
   */
  editQuotationId?: number;
  editQuotationNumber?: string;
}

export function QuotationCreatePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const state = location.state as LocationState | null;
  const initialCustomer = state?.customer;
  const initialItems = state?.items;
  const editId = state?.editQuotationId;
  const isEdit = typeof editId === 'number';

  const handleSubmit = async (request: QuotationRequest) => {
    const quotation = isEdit
      ? await quotationService.update(editId, request)
      : await quotationService.create(request);
    toast.success(`Quotation ${quotation.quotationNumber} ${isEdit ? 'updated' : 'created'}.`);
    navigate(QUOTATION_ROUTES.detail(quotation.id), { replace: true });
  };

  return (
    <>
      <PageHeader
        title={isEdit ? `Edit quotation ${state?.editQuotationNumber ?? ''}`.trim() : 'New quotation'}
        description={isEdit
          ? 'Add or change items. The quotation number and date stay the same.'
          : "A price quote for a customer - nothing is charged or moved from stock until it's converted to an invoice."}
      />
      <QuotationWizard onSubmit={handleSubmit}
                       onCancel={() => navigate(isEdit ? QUOTATION_ROUTES.detail(editId) : QUOTATION_ROUTES.list)}
                       submitLabel={isEdit ? 'Save changes' : undefined}
                       initialCustomer={initialCustomer} initialItems={initialItems} />
    </>
  );
}
