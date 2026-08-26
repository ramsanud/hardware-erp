import type { SubscriptionTier } from '@/modules/settings/types';

export interface TenantRegistrationRequest {
  shopName: string;
  ownerFullName: string;
  mobileNo: string;
  email: string;
  password: string;
  subscriptionTier: SubscriptionTier;
  /** Must be true. Enforced server-side too (CR-040). */
  termsAccepted: boolean;
  /** The document versions shown to the user; rejected if not current. */
  termsVersion?: string;
  privacyVersion?: string;
  /** Optional and revocable. Absent or false is never treated as consent. */
  marketingConsent?: boolean;
}

export interface TenantRegistrationResponse {
  tenantId: number;
  slug: string;
  shopName: string;
  ownerMobileNo: string;
}
