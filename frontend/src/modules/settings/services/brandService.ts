import { apiGet } from '@/services/apiClient';
import type { SubscriptionTier } from '../types';

export interface TenantBrandResponse {
  name: string;
  hasLogo: boolean;
  subscriptionTier: SubscriptionTier;
  /** CR-053 backlog item 1 - visible to every authenticated user, same reasoning as the fields above. */
  showPriceHistory: boolean;
  enableFreeQuantity: boolean;
  /** CR-053 backlog item 3 - informational-only TDS/TCS, same reasoning. */
  tdsEnabled: boolean;
  tdsRatePercent: number;
  tcsEnabled: boolean;
  tcsRatePercent: number;
  /** CR-053 backlog item 4 - same reasoning as the fields above. */
  einvoiceEnabled: boolean;
}

/** Visible to every authenticated user, unlike GET /v1/settings which needs SETTINGS_VIEW. */
export const brandService = {
  logoUrl: '/v1/settings/logo',
  get: () => apiGet<TenantBrandResponse>('/v1/settings/brand'),
};
