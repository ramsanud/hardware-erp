/** Mirrors backend/subscription/dto/*.java. */

export type SubscriptionStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'EXPIRED' | 'CANCELLED' | 'SUSPENDED';

export interface UsageLimitResponse {
  usageKey: string;
  label: string;
  includedCount: number;
}

export interface SubscriptionPlanResponse {
  planCode: string;
  tier: 'FREE' | 'PRO' | 'MAX';
  planName: string;
  tagline: string;
  pricePaise: number;
  priceDisplay: string;
  currency: string;
  billingPeriod: string;
  recommended: boolean;
  displayOrder: number;
  featureKeys: string[];
  usageLimits: UsageLimitResponse[];
}

export interface UsageItemResponse {
  usageKey: string;
  label: string;
  usedCount: number;
  includedCount: number;
  remainingCount: number;
  limitReached: boolean;
}

export interface UsageResponse {
  periodStart: string | null;
  periodEnd: string | null;
  planCode: string;
  items: UsageItemResponse[];
}

export interface SubscriptionHistoryResponse {
  fromPlanCode: string | null;
  toPlanCode: string;
  fromStatus: string | null;
  toStatus: string;
  reason: string | null;
  createdAt: string;
}

export interface CurrentSubscriptionResponse {
  planCode: string;
  planName: string;
  tier: 'FREE' | 'PRO' | 'MAX';
  status: SubscriptionStatus;
  effectivePlanCode: string;
  effectivePlanName: string;
  trialEndsAt: string | null;
  startedAt: string;
  endsAt: string | null;
  renewalAt: string | null;
  cancelledAt: string | null;
  paymentStatus: string | null;
  checkoutRequiredForUpgrade: boolean;
  featureKeys: string[];
  usage: UsageResponse;
  history: SubscriptionHistoryResponse[];
}

export interface FeatureAccessResponse {
  featureKey: string;
  featureName: string;
  allowed: boolean;
  currentPlanCode: string;
  currentPlanName: string;
  requiredPlanCode: string | null;
  requiredPlanName: string | null;
}

/** Mirrors backend/subscription/entity/FeatureKey.java - keep the two in sync by hand. */
export type FeatureKey =
  | 'GST_BILLING' | 'NON_GST_BILLING' | 'INVOICE_CANCELLATION' | 'PAYMENTS' | 'PRODUCTS' | 'LOW_STOCK_ALERT'
  | 'CUSTOMERS' | 'SUPPLIERS' | 'INVENTORY' | 'DASHBOARD' | 'BASIC_REPORTS' | 'DATA_EXPORT' | 'OFFLINE_SYNC'
  | 'QUOTATION' | 'SALES_ORDER' | 'DELIVERY_CHALLAN' | 'CREDIT_NOTE' | 'PURCHASE_RETURN' | 'INVOICE_TEMPLATES'
  | 'THERMAL_PRINT' | 'BULK_IMPORT' | 'PDF_PRICE_IMPORT' | 'PRICE_CHANGE_DETECTION' | 'STOCK_VALUATION'
  | 'CUSTOMER_CREDIT' | 'PAYMENT_REMINDERS' | 'SUPPLIER_STATEMENT' | 'ADVANCED_REPORTS' | 'STAFF_ROLES'
  | 'PROJECTS' | 'COUPONS'
  | 'SMART_SUBSTITUTE' | 'SMART_INSIGHTS' | 'ADVANCED_ANALYTICS' | 'MULTI_BRANCH' | 'NEARBY_PRODUCT_DISCOVERY'
  | 'ADVANCED_NOTIFICATIONS' | 'AUTO_BACKUP' | 'AI_FEATURES' | 'PRIORITY_SUPPORT';
