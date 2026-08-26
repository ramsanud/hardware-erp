import { apiGet } from '@/services/apiClient';
import type { SubscriptionTier } from '../types';

export interface TenantBrandResponse {
  name: string;
  hasLogo: boolean;
  subscriptionTier: SubscriptionTier;
}

/** Visible to every authenticated user, unlike GET /v1/settings which needs SETTINGS_VIEW. */
export const brandService = {
  logoUrl: '/v1/settings/logo',
  get: () => apiGet<TenantBrandResponse>('/v1/settings/brand'),
};
