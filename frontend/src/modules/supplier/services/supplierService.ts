import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  SupplierContactRequest, SupplierContactResponse, SupplierDeletedResponse, SupplierRequest,
  SupplierResponse, SupplierSearchParams, SupplierSummaryResponse,
} from '../types';

/** Backend: supplier/controller/SupplierController.java */
export const supplierService = {
  search: (params: SupplierSearchParams) =>
    apiGet<PageResponse<SupplierSummaryResponse>>('/v1/suppliers', { params }),

  /** Distinct cities, for the filter dropdown. */
  cities: () => apiGet<string[]>('/v1/suppliers/cities'),

  get: (id: number) => apiGet<SupplierResponse>(`/v1/suppliers/${id}`),

  /** CR-018: the full, unmasked bank account number, decrypted on demand. Never cache the result beyond the reveal toggle being open. */
  revealBankAccountNumber: (id: number) => apiGet<string>(`/v1/suppliers/${id}/bank-account-number`),

  create: (body: SupplierRequest) => apiPost<SupplierResponse>('/v1/suppliers', body),

  update: (id: number, body: SupplierRequest) =>
    apiPut<SupplierResponse>(`/v1/suppliers/${id}`, body),

  /** Soft delete. Purchase orders and payments reference supplier_id permanently. */
  remove: (id: number) => apiDelete(`/v1/suppliers/${id}`),

  /** CR-058. Deleted rows are invisible to `search` - @SQLRestriction hides them - so recovery needs its own endpoint. Requires SUPPLIER_MANAGE. Not paginated. */
  listDeleted: () => apiGet<SupplierDeletedResponse[]>('/v1/suppliers/deleted'),

  /** CR-058. Undoes `remove`, in place: same id, same code, same contacts, same purchase history. */
  restore: (id: number) => apiPost<void>(`/v1/suppliers/${id}/restore`),

  addContact: (supplierId: number, body: SupplierContactRequest) =>
    apiPost<SupplierContactResponse>(`/v1/suppliers/${supplierId}/contacts`, body),

  updateContact: (supplierId: number, contactId: number, body: SupplierContactRequest) =>
    apiPut<SupplierContactResponse>(`/v1/suppliers/${supplierId}/contacts/${contactId}`, body),

  removeContact: (supplierId: number, contactId: number) =>
    apiDelete(`/v1/suppliers/${supplierId}/contacts/${contactId}`),
};
