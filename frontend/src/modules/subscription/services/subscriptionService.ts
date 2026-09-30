import { apiGet, apiPost } from '@/services/apiClient';
import type {
  CurrentSubscriptionResponse, FeatureAccessResponse, FeatureKey, SubscriptionPlanResponse, UsageResponse,
} from '../types';

/** Backend: subscription/controller/SubscriptionController.java, FeatureAccessController.java (CR-088) */
export const subscriptionService = {
  plans: () => apiGet<SubscriptionPlanResponse[]>('/v1/subscriptions/plans'),

  current: () => apiGet<CurrentSubscriptionResponse>('/v1/subscriptions/current'),

  usage: () => apiGet<UsageResponse>('/v1/subscriptions/usage'),

  upgrade: (planCode: string, reason?: string) =>
    apiPost<CurrentSubscriptionResponse>('/v1/subscriptions/upgrade', { planCode, reason }),

  cancel: (reason: string) =>
    apiPost<CurrentSubscriptionResponse>('/v1/subscriptions/cancel', { reason }),

  access: (featureKey: FeatureKey) =>
    apiGet<FeatureAccessResponse>(`/v1/features/${featureKey}/access`),
};
