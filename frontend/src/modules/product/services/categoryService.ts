import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { CategoryRequest, CategoryResponse } from '../types';

/** Backend: product/controller/CategoryController.java */
export const categoryService = {
  list: () => apiGet<CategoryResponse[]>('/v1/categories'),

  get: (id: number) => apiGet<CategoryResponse>(`/v1/categories/${id}`),

  create: (body: CategoryRequest) => apiPost<CategoryResponse>('/v1/categories', body),

  update: (id: number, body: CategoryRequest) =>
    apiPut<CategoryResponse>(`/v1/categories/${id}`, body),

  /** Hard delete. Rejected if any product or sub-category still references it. */
  remove: (id: number) => apiDelete(`/v1/categories/${id}`),
};
