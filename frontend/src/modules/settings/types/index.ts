export type SubscriptionTier = 'FREE' | 'PRO' | 'MAX';

/** CR-053 phase 1 - a shop-wide default colour/font skin for the generated invoice PDF, never a photographic background. */
export type InvoiceTheme = 'CLASSIC' | 'MINIMAL' | 'BOLD' | 'ELEGANT';

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
  /** CR-053 phase 1. */
  invoiceTheme: InvoiceTheme;

  /** CR-053 backlog item 1 - "Additional Settings" (myBillBook parity). */
  showItemDescription: boolean;
  showAlternateUnit: boolean;
  /** Gates the Price History section on the Product Detail page, not the invoice PDF. */
  showPriceHistory: boolean;
  /** Gates whether the "Free Qty" field appears at all on invoice line entry. */
  enableFreeQuantity: boolean;
  showInvoiceTime: boolean;
  showItemImage: boolean;
  /** Presence is the toggle - blank prints nothing on the invoice. */
  invoiceTagline?: string | null;

  /** CR-053 backlog item 3. Informational only - never applied to a stored total. */
  tdsEnabled: boolean;
  tdsSectionCode?: string | null;
  tdsRatePercent: number;
  tcsEnabled: boolean;
  tcsSectionCode?: string | null;
  tcsRatePercent: number;

  /** CR-053 backlog item 4. Shows the e-Invoice review section on Invoice detail - generation itself always stays disabled. */
  einvoiceEnabled: boolean;

  /** CR-053 backlog item 5. Read once a day by the backend's ReminderSchedulerService. */
  paymentDueReminderEnabled: boolean;
  lowStockAlertEnabled: boolean;
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

// ---------------------------------------------------------------
// CR-057 phase 9 - Razorpay checkout to move a tenant's own plan up.
// Mirrors backend/billing/dto/*.java.
// ---------------------------------------------------------------

export type SubscriptionOrderStatus = 'CREATED' | 'PAID' | 'FAILED' | 'CANCELLED';
export type SubscriptionPaymentStatus = 'CAPTURED' | 'FAILED';
export type PaymentSource = 'CLIENT_VERIFY' | 'WEBHOOK';

export interface SubscriptionOrderResponse {
  orderId: number;
  razorpayOrderId: string;
  /** Public key - safe to hand to Razorpay's Checkout.js widget. */
  razorpayKeyId: string;
  requestedTier: SubscriptionTier;
  amountPaise: number;
  currency: string;
  status: SubscriptionOrderStatus;
}

export interface VerifyPaymentRequest {
  razorpayOrderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
}

export interface SubscriptionPaymentResponse {
  paymentId: number;
  orderId: number;
  requestedTier: SubscriptionTier;
  amountPaise: number;
  currency: string;
  status: SubscriptionPaymentStatus;
  source: PaymentSource;
  capturedAt?: string | null;
}

export interface TenantBillingHistoryResponse {
  currentTier: SubscriptionTier;
  payments: SubscriptionPaymentResponse[];
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
  /** CR-053 phase 1. Null means "leave unchanged", same convention as subscriptionTier above. */
  invoiceTheme?: InvoiceTheme | null;

  /** CR-053 backlog item 1. Unlike invoiceTheme, plain booleans with no "leave unchanged" state - always sent. */
  showItemDescription: boolean;
  showAlternateUnit: boolean;
  showPriceHistory: boolean;
  enableFreeQuantity: boolean;
  showInvoiceTime: boolean;
  showItemImage: boolean;
  invoiceTagline?: string | null;

  /** CR-053 backlog item 3. */
  tdsEnabled: boolean;
  tdsSectionCode?: string | null;
  tdsRatePercent: number;
  tcsEnabled: boolean;
  tcsSectionCode?: string | null;
  tcsRatePercent: number;

  /** CR-053 backlog item 4. */
  einvoiceEnabled: boolean;

  /** CR-053 backlog item 5. */
  paymentDueReminderEnabled: boolean;
  lowStockAlertEnabled: boolean;
}

/** Mirrors backend notification/entity/WhatsAppConnectionStatus.java. */
export type WhatsAppConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NEEDS_ATTENTION';

/** Mirrors backend notification/dto/WhatsAppConnectionResponse.java - never carries an access token. */
export interface WhatsAppConnectionResponse {
  connected: boolean;
  status: WhatsAppConnectionStatus;
  businessName?: string | null;
  phoneNumberMasked?: string | null;
  connectedAt?: string | null;
  lastVerifiedAt?: string | null;
}

/** Mirrors backend notification/dto/WhatsAppConnectionRequest.java. */
export interface WhatsAppConnectionRequest {
  businessAccountId: string;
  phoneNumberId: string;
  accessToken: string;
}
