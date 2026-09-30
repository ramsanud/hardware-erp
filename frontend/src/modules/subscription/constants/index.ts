export const SUBSCRIPTION_ROUTES = {
  pricing: '/settings/subscription',
};

/** Mirrors backend/subscription/entity/SubscriptionPlan seed rows (V59) - display order only, prices come from the API. */
export const PLAN_DISPLAY_ORDER = ['BASIC', 'PRO', 'PREMIUM'] as const;
