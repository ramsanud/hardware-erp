import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  CouponRequest, CouponResponse, CouponSearchParams, CouponSummaryResponse,
} from '../types';

/** Backend: coupon/controller/CouponController.java */
export const couponService = {
  search: (params: CouponSearchParams) =>
    apiGet<PageResponse<CouponSummaryResponse>>('/v1/coupons', { params }),

  get: (id: number) => apiGet<CouponResponse>(`/v1/coupons/${id}`),

  create: (body: CouponRequest) => apiPost<CouponResponse>('/v1/coupons', body),

  update: (id: number, body: CouponRequest) =>
    apiPut<CouponResponse>(`/v1/coupons/${id}`, body),

  remove: (id: number) => apiDelete(`/v1/coupons/${id}`),
};
