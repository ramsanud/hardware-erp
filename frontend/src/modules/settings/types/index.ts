export type SubscriptionTier = 'FREE' | 'PRO' | 'MAX';

export interface TenantSettingsResponse {
  id: number;
  name: string;
  gstNo?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  stateCode?: string | null;
  pincode?: string | null;
  signatoryName?: string | null;
  hasLogo: boolean;
  hasSignatureImage: boolean;
  hasUpiQrImage: boolean;
  panNo?: string | null;
  phone?: string | null;
  email?: string | null;
  bankAccountName?: string | null;
  bankAccountNo?: string | null;
  bankIfsc?: string | null;
  bankName?: string | null;
  upiId?: string | null;
  subscriptionTier: SubscriptionTier;
  /** CR-032 - null means subscriptionTier is permanent, not from a trial coupon. */
  subscriptionTrialExpiresAt?: string | null;
}

export type SubscriptionCouponStatus = 'ACTIVE' | 'INACTIVE';

/** CR-032 - a trial code the OWNER creates and redeems to grant their own tenant a plan for free, for a limited time. */
export interface SubscriptionCouponRequest {
  code: string;
  description?: string | null;
  grantedTier: SubscriptionTier;
  trialDays: number;
  validFrom?: string | null;
  validUntil?: string | null;
  usageLimit?: number | null;
  status: SubscriptionCouponStatus;
}

export interface SubscriptionCouponResponse {
  id: number;
  code: string;
  description?: string | null;
  grantedTier: SubscriptionTier;
  trialDays: number;
  validFrom?: string | null;
  validUntil?: string | null;
  usageLimit?: number | null;
  timesUsed: number;
  status: SubscriptionCouponStatus;
  currentlyRedeemable: boolean;
}

export interface SubscriptionCouponRedemptionResponse {
  grantedTier: SubscriptionTier;
  trialExpiresAt: string;
}

/** CR-031 (Customer 360 §27-40) - usage against the tenant's tier entitlement limits. A limit of -1 means unlimited (MAX tier) - never render it as a 0% bar. */
export interface UsageSummaryResponse {
  tier: SubscriptionTier;
  ownerCount: number;
  maxOwners: number;
  customerCount: number;
  maxCustomers: number;
  supplierCount: number;
  maxSuppliers: number;
  productCount: number;
  maxProducts: number;
}

/** CR-036 - one of possibly several accounts a shop can receive payment into, each with its own set of uploaded QR codes, selectable per invoice. */
export interface TenantBankAccountQrResponse {
  id: number;
  label: string;
}

export type TenantBankAccountStatus = 'ACTIVE' | 'INACTIVE';

export interface TenantBankAccountResponse {
  id: number;
  label: string;
  bankName: string;
  accountHolderName: string;
  accountNumberMasked?: string | null;
  ifscCode: string;
  upiId?: string | null;
  defaultAccount: boolean;
  status: TenantBankAccountStatus;
  qrCodes: TenantBankAccountQrResponse[];
}

export interface TenantBankAccountRequest {
  label: string;
  bankName: string;
  accountHolderName: string;
  accountNumber: string;
  ifscCode: string;
  upiId?: string | null;
  defaultAccount: boolean;
}

export interface TenantSettingsRequest {
  name: string;
  gstNo?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  stateCode?: string | null;
  pincode?: string | null;
  signatoryName?: string | null;
  panNo?: string | null;
  phone?: string | null;
  email?: string | null;
  bankAccountName?: string | null;
  bankAccountNo?: string | null;
  bankIfsc?: string | null;
  bankName?: string | null;
  upiId?: string | null;
  subscriptionTier?: SubscriptionTier | null;
}
