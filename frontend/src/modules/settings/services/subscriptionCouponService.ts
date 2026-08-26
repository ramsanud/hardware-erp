import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  SubscriptionCouponRedemptionResponse, SubscriptionCouponRequest, SubscriptionCouponResponse,
  SubscriptionCouponStatus,
} from '../types';

/** Backend: tenant/controller/SubscriptionCouponController.java (CR-032) */
export const subscriptionCouponService = {
  search: (params: { search?: string; status?: SubscriptionCouponStatus; size?: number } = {}) =>
    apiGet<PageResponse<SubscriptionCouponResponse>>('/v1/subscription-coupons', { params }),

  create: (body: SubscriptionCouponRequest) =>
    apiPost<SubscriptionCouponResponse>('/v1/subscription-coupons', body),

  update: (id: number, body: SubscriptionCouponRequest) =>
    apiPut<SubscriptionCouponResponse>(`/v1/subscription-coupons/${id}`, body),

  remove: (id: number) => apiDelete(`/v1/subscription-coupons/${id}`),

  redeem: (code: string) =>
    apiPost<SubscriptionCouponRedemptionResponse>('/v1/subscription-coupons/redeem', { code }),
};
