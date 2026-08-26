import type { SubscriptionTier } from '../types';

/** Mirrors backend/tenant/entity/SubscriptionTier.java - keep the two in sync by hand, there's no shared source of truth across languages here. */
export const SUBSCRIPTION_TIERS: Record<SubscriptionTier, { label: string; features: string[] }> = {
  FREE: {
    label: 'Free',
    features: ['Suppliers, Customers, Products', 'Quotations & Invoices', 'Inventory & Payment tracking'],
  },
  PRO: {
    label: 'Pro',
    features: ['Everything in Free', 'Customer/staff notifications by email, SMS and WhatsApp'],
  },
  MAX: {
    label: 'Max',
    features: ['Everything in Pro', 'AI assistant over your own shop data'],
  },
};

export const SUBSCRIPTION_TIER_OPTIONS: SubscriptionTier[] = ['FREE', 'PRO', 'MAX'];
