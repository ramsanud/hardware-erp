# Subscription plans & feature gating (CR-088)

## Domain model

```
Tenant
  └── tenant_subscription (1:1)      status, trial/renewal/cancel dates, gateway refs
         └── subscription_plan (N:1) BASIC / PRO / PREMIUM — price, tier, active
                └── plan_feature (N:N) → feature                  the catalogue
                └── plan_usage_limit (N:N) → usage_key             metered channels
  └── subscription_usage              per tenant, usage_key, calendar month, used_count
  └── subscription_history            append-only, every plan/status transition
```

`tenant.subscription_tier` (V15, FREE/PRO/MAX — locked, CR-027) is still the
single value every pre-existing gate reads (`SubscriptionService.requireTier`,
entitlement limits, AI). CR-088 does not replace it or change its values; it
maps one-to-one onto the three plans (`BASIC=FREE`, `PRO=PRO`,
`PREMIUM=MAX`) and layers everything the tier alone cannot express — price,
status, trial/grace/expiry, feature catalogue, metered usage — in the new
tables. `SubscriptionLifecycleServiceImpl` is the **only** writer of
`tenant.subscription_tier`; every caller that used to set it directly
(the Shop Settings picker, CR-032's coupon redemption, CR-057's Razorpay
verification, registration) now calls `applyTier()`/`startForNewTenant()`
instead, so the tier and the richer subscription state can never disagree.

## Feature access

`FeatureAccessService` is the single gate:

```java
featureAccessService.requireFeature(FeatureKey.SMART_SUBSTITUTE);
```

throws `FeatureNotAvailableException` (403, `FEATURE_NOT_AVAILABLE`) naming
the feature, the tenant's current plan and the cheapest plan that carries it.
Never compare a plan code or tier directly in a controller or service —
that duplicates the matrix and drifts (spec's own "BAD: `if (plan.equals
("PREMIUM"))`").

`effectivePlan(tenantId)` is BASIC whenever the subscription's status does
not currently grant paid features (EXPIRED / CANCELLED / SUSPENDED), even
though `tenant_subscription.plan` still reads whatever paid plan the tenant
was on — so the UI can say "your Premium plan expired" while the backend
enforces Basic. Data is never deleted in any state; `DATA_EXPORT` is
deliberately a BASIC feature so an expired shop can still take its records
away (hard rule: subscription expiry must never delete business data).

## Status semantics

| Status | Feature access | Notes |
|---|---|---|
| TRIAL | full plan | `trial_ends_at` set; reverts to Basic ACTIVE automatically on next read past that date |
| ACTIVE | full plan | normal paid or free state |
| PAST_DUE | full plan (grace) | `app.subscription.past-due-grace-days` (default 7) before EXPIRED |
| EXPIRED | Basic only | data kept; owner can self-service downgrade/upgrade or pay to reactivate |
| CANCELLED | Basic only | explicit owner action, reason recorded |
| SUSPENDED | Basic only | platform-operator action (future use) |

Transitions are lazy, checked on `SubscriptionLifecycleServiceImpl.currentFor()`
(every `effectivePlan()` call) exactly like CR-032's trial-coupon revert —
no scheduled job. `currentFor()` runs its lazy-expiry writes in its own
`REQUIRES_NEW` transaction so they commit even when the caller (an
entitlement/feature check) is `readOnly`.

## Usage metering

`UsageTrackingService.tryConsume(tenantId, key)` atomically increments
`subscription_usage` (upsert-and-increment guarded by the plan's
`plan_usage_limit.included_count` in one SQL statement — two concurrent
sends cannot both squeeze under the limit) and returns whether the unit was
granted. `NotificationServiceImpl.attempt()` calls it before every
WhatsApp/SMS/email send; `AiChatService.reply()` calls
`consumeOrThrow()` before every AI request. A refused send never reaches the
provider — it is logged as `NotificationStatus.QUOTA_EXCEEDED`, not
`FAILED` (a provider failure and a plan-limit refusal are different
outcomes) and never silently retried against a paid API.

## API

```
GET  /v1/subscriptions/plans          public - pricing page before login
GET  /v1/subscriptions/current        the caller's plan, status, usage, history
GET  /v1/subscriptions/features       feature keys the caller's effective plan carries
GET  /v1/subscriptions/usage          this month's metered usage
POST /v1/subscriptions/upgrade        { planCode, reason? } - SETTINGS_MANAGE
POST /v1/subscriptions/cancel         { reason } - SETTINGS_MANAGE
GET  /v1/features/{featureKey}/access allowed + required plan for one feature
```

An upgrade to a paid plan is refused with `UPGRADE_REQUIRES_CHECKOUT` once a
Razorpay gateway is configured (same rule the CR-027 Shop Settings picker
already enforced) — self-service only works for a free/downgrade move or
when no gateway exists (self-hosted deployments, CR-059). Real checkout
still goes through `/v1/billing/*` (CR-057); its Razorpay-verify path now
also calls `SubscriptionLifecycleService.applyTier()` so the two subsystems
can never disagree on the tenant's plan.

## Trial policy

`app.subscription.trial-days` (default 14), `trial-tier` (default MAX) and
`past-due-grace-days` (default 7) — `SubscriptionProperties`, never a
literal in code. `trial-days: 0` disables trials outright; a new shop then
registers straight onto BASIC ACTIVE.

## Testing

- `FeatureAccessServiceImplTest`, `UsageTrackingServiceImplTest`,
  `SubscriptionLifecycleServiceImplTest` — unit, the plan-feature matrix
  cache, usage counters, every lifecycle transition.
- `SubscriptionControllerIT` — Basic/Pro → 403 on a Premium-only API
  (`/v1/ai/chat`), Premium → success, `/v1/features/{key}/access` reports
  the required plan, `/v1/subscriptions/current` is tenant-isolated,
  self-service upgrade changes the effective plan immediately, plans is
  public, current requires auth.

## A real bug this caught

`TenantSubscription.plan` is `@ManyToOne(fetch = LAZY)`. The lifecycle
service's `currentFor()` loads it inside its own `REQUIRES_NEW` transaction
(mirroring `SubscriptionServiceImplCR-032`'s pattern so its lazy-expiry
write always flushes) and hands the entity back to `FeatureAccessServiceImpl
.effectivePlan()`, which runs in a *different* transaction/session. Reading
the lazy `plan` proxy there threw `LazyInitializationException` — caught by
`SubscriptionControllerIT`, not by the unit tests (which mock the
repository and never exercise a real Hibernate session). Fixed with a
`JOIN FETCH` in `TenantSubscriptionRepository.findByTenantId`.
