import { apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { ExpenseCategoryRequest, ExpenseCategoryResponse } from '../types';

/** Backend: expense/controller/ExpenseCategoryController.java */
export const expenseCategoryService = {
  list: () => apiGet<ExpenseCategoryResponse[]>('/v1/expense-categories'),

  create: (body: ExpenseCategoryRequest) =>
    apiPost<ExpenseCategoryResponse>('/v1/expense-categories', body),

  update: (id: number, body: ExpenseCategoryRequest) =>
    apiPut<ExpenseCategoryResponse>(`/v1/expense-categories/${id}`, body),
};
