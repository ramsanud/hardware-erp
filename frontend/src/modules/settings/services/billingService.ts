import { apiGet, apiPost } from '@/services/apiClient';
import type {
  SubscriptionOrderResponse, SubscriptionTier, TenantBillingHistoryResponse, VerifyPaymentRequest,
} from '../types';

/** Backend: billing/controller/SubscriptionBillingController.java (CR-057 phase 9) */
export const billingService = {
  checkout: (requestedTier: SubscriptionTier) =>
    apiPost<SubscriptionOrderResponse>('/v1/billing/checkout', { requestedTier }),

  verify: (body: VerifyPaymentRequest) => apiPost<void>('/v1/billing/verify', body),

  history: () => apiGet<TenantBillingHistoryResponse>('/v1/billing/history'),
};
