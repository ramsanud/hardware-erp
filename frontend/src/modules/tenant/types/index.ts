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
  /** CR-078. The six-digit code sent to `email` by /register/send-code. Required unless the deployment switched registration-email-verification off. */
  emailCode?: string;
}

export interface TenantRegistrationResponse {
  tenantId: number;
  slug: string;
  shopName: string;
  ownerMobileNo: string;
}

/** CR-078. Sent to /v1/tenants/register/send-code. */
export interface RegistrationCodeRequest {
  email: string;
}
