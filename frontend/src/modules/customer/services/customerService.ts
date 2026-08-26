import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import { ApiError, type PageResponse } from '@/shared/types/api';
import type { InvoiceSummaryResponse } from '@/modules/invoice/types';
import type { QuotationSummaryResponse } from '@/modules/quotation/types';
import type {
  CustomerCreditCheckResponse, CustomerFinancialSummaryResponse, CustomerProductHistoryResponse, CustomerRequest,
  CustomerResponse, CustomerSearchParams, CustomerSummaryResponse,
} from '../types';

/** Backend: customer/controller/CustomerController.java */
export const customerService = {
  search: (params: CustomerSearchParams) =>
    apiGet<PageResponse<CustomerSummaryResponse>>('/v1/customers', { params }),

  get: (id: number) => apiGet<CustomerResponse>(`/v1/customers/${id}`),

  create: (body: CustomerRequest) => apiPost<CustomerResponse>('/v1/customers', body),

  update: (id: number, body: CustomerRequest) => apiPut<CustomerResponse>(`/v1/customers/${id}`, body),

  deactivate: (id: number) => apiDelete(`/v1/customers/${id}`),

  financialSummary: (id: number) =>
    apiGet<CustomerFinancialSummaryResponse>(`/v1/customers/${id}/financial-summary`),

  recentInvoices: (id: number, size = 10) =>
    apiGet<PageResponse<InvoiceSummaryResponse>>(`/v1/customers/${id}/invoices`, { params: { size } }),

  recentQuotations: (id: number, size = 10) =>
    apiGet<PageResponse<QuotationSummaryResponse>>(`/v1/customers/${id}/quotations`, { params: { size } }),

  productHistory: (id: number) =>
    apiGet<CustomerProductHistoryResponse[]>(`/v1/customers/${id}/products`),

  /**
   * CR-030 - null means no existing customer at this mobile number (a new
   * walk-in), not a failure. 404 is the server's way of saying exactly
   * that, so it's swallowed here rather than left for every call site to
   * special-case.
   */
  creditCheckByMobile: async (mobile: string): Promise<CustomerCreditCheckResponse | null> => {
    try {
      return await apiGet<CustomerCreditCheckResponse>('/v1/customers/credit-check', { params: { mobile } });
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return null;
      throw error;
    }
  },
};
