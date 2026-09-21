import { useLocation, useNavigate } from 'react-router-dom';
import { PageHeader } from '@/shared/components/PageHeader';
import { useToast } from '@/modules/auth/hooks/useToast';
import { ApiError } from '@/shared/types/api';
import { outbox } from '@/modules/sync/lib/outbox';
import { notifyOutboxChanged } from '@/modules/sync/hooks/useOutbox';
import { SYNC_ROUTES } from '@/modules/sync/constants';
import {
  InvoiceWizard, type InvoiceWizardInitialCustomer, type InvoiceWizardInitialItem,
} from '../forms/InvoiceWizard';
import { invoiceService } from '../services/invoiceService';
import { INVOICE_ROUTES } from '../constants';
import type { InvoiceRequest } from '../types';

interface LocationState {
  customer?: InvoiceWizardInitialCustomer;
  items?: InvoiceWizardInitialItem[];
  /**
   * Present when amending an existing unpaid invoice rather than raising a
   * new one. Reuses this page instead of a second near-identical one, so the
   * wizard's steps, totals and validation cannot drift between the two.
   */
  editInvoiceId?: number;
  editInvoiceNumber?: string;
}

/**
 * A full page, not a modal - twenty-odd fields across four steps does not
 * fit comfortably in a dialog at any viewport. The wizard itself supplies
 * the fixed Back/Next navigation bar. Arriving from a customer's own page
 * (CR-030 §11) pre-fills the Customer step via router state - never a
 * required param, so "New invoice" from the sidebar/dashboard still works
 * exactly as before with a blank form. Arriving from "Repeat" on a past
 * invoice (CR-030 §45) additionally pre-fills the item list.
 */
export function InvoiceCreatePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const state = location.state as LocationState | null;
  const initialCustomer = state?.customer;
  const initialItems = state?.items;
  const editId = state?.editInvoiceId;
  const isEdit = typeof editId === 'number';

  const handleSubmit = async (request: InvoiceRequest) => {
    if (isEdit) {
      const invoice = await invoiceService.update(editId, request);
      toast.success(`Invoice ${invoice.invoiceNumber} updated.`);
      navigate(INVOICE_ROUTES.detail(invoice.id), { replace: true });
      return;
    }
    // CR-091 Phase 9. Unreachable server - queue it on this device rather
    // than lose the sale. Only a NETWORK/TIMEOUT failure qualifies: a 4xx
    // is the server's real answer and must be shown, never queued.
    if (!navigator.onLine) {
      await queueOffline(request);
      return;
    }
    try {
      const invoice = await invoiceService.create(request);
      toast.success(`Invoice ${invoice.invoiceNumber} created.`);
      navigate(INVOICE_ROUTES.detail(invoice.id), { replace: true });
    } catch (caught) {
      if (caught instanceof ApiError && (caught.status === 0 || caught.status === 408)) {
        await queueOffline(request);
        return;
      }
      throw caught;
    }
  };

  const queueOffline = async (request: InvoiceRequest) => {
    await outbox.queueInvoice(request);
    notifyOutboxChanged();
    toast.info('Saved on this device. It will be sent to the server when you are back online.');
    navigate(SYNC_ROUTES.outbox, { replace: true });
  };

  return (
    <>
      <PageHeader
        title={isEdit ? `Edit invoice ${state?.editInvoiceNumber ?? ''}`.trim() : 'New invoice'}
        description={isEdit
          ? 'Add or change items. The invoice number and date stay the same, and stock adjusts by the difference.'
          : 'Add the customer, the items sold, and an optional payment.'}
      />
      <InvoiceWizard onSubmit={handleSubmit}
                     onCancel={() => navigate(isEdit ? INVOICE_ROUTES.detail(editId) : INVOICE_ROUTES.list)}
                     submitLabel={isEdit ? 'Save changes' : undefined}
                     editing={isEdit}
                     initialCustomer={initialCustomer} initialItems={initialItems} />
    </>
  );
}
