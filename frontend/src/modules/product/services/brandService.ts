import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { BrandRequest, BrandResponse } from '../types';

/** Backend: product/controller/BrandController.java */
export const brandService = {
  list: () => apiGet<BrandResponse[]>('/v1/brands'),

  get: (id: number) => apiGet<BrandResponse>(`/v1/brands/${id}`),

  create: (body: BrandRequest) => apiPost<BrandResponse>('/v1/brands', body),

  update: (id: number, body: BrandRequest) => apiPut<BrandResponse>(`/v1/brands/${id}`, body),

  /** Hard delete. Rejected if any product still references it. */
  remove: (id: number) => apiDelete(`/v1/brands/${id}`),
};
