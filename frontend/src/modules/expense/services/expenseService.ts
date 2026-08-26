import {
  apiDelete, apiGet, apiPost, apiPut, apiUploadFile,
} from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  BusinessExpenseRequest, BusinessExpenseResponse, ExpenseSearchParams, ExpenseTotalResponse,
} from '../types';

/** Backend: expense/controller/BusinessExpenseController.java */
export const expenseService = {
  search: (params: ExpenseSearchParams) =>
    apiGet<PageResponse<BusinessExpenseResponse>>('/v1/expenses', { params }),

  total: (fromDate?: string, toDate?: string) =>
    apiGet<ExpenseTotalResponse>('/v1/expenses/total', { params: { fromDate, toDate } }),

  get: (id: number) => apiGet<BusinessExpenseResponse>(`/v1/expenses/${id}`),

  create: (body: BusinessExpenseRequest) => apiPost<BusinessExpenseResponse>('/v1/expenses', body),

  update: (id: number, body: BusinessExpenseRequest) =>
    apiPut<BusinessExpenseResponse>(`/v1/expenses/${id}`, body),

  cancel: (id: number) => apiPost<void>(`/v1/expenses/${id}/cancel`),

  receiptUrl: (id: number) => `/v1/expenses/${id}/receipt`,
  uploadReceipt: (id: number, file: File) => apiUploadFile(`/v1/expenses/${id}/receipt`, file),
  removeReceipt: (id: number) => apiDelete(`/v1/expenses/${id}/receipt`),
};
