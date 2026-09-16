# Implementation tasks — SaaS plans, Smart Substitute, Nearby Discovery, business-logic completion

Source brief: `hallo.txt` (four prompts: Nearby Product Discovery, Smart Substitute,
SaaS Subscription Plans, Critical Business Logic Plan). This file is the working
task list; each box is ticked only when the code, migration, tests and registry
entry exist. Branch `feature/cr-088-saas-platform`, worktree `hardware-erp-saas`.

## Phase 0 — audit result (what already exists, reused, not rebuilt)

| Brief item | Already in the codebase | Gap closed by |
|---|---|---|
| Subscription tiers | `SubscriptionTier` FREE/PRO/MAX on `tenant` (CR-027), entitlement counts (CR-031), trial coupons (CR-032), Razorpay checkout (CR-057) | CR-088 layers a plan catalogue + status + feature matrix on top; the persisted enum values are locked and stay |
| Feature flags | `feature_flag` (V48) — platform admin kill-switches, not plan features | CR-088 `feature` / `plan_feature` are plan entitlements; flags untouched |
| Stock movements | `stock_movement` ledger, `StockService.applyMovement`, row lock, negative-stock guard | CR-092 adds STOCK_TRANSFER_IN/OUT for branches |
| Invoice cancellation | `InvoiceStatus.CANCELLED`, SALE_REVERSAL movement, activity log | CR-091 adds reason / cancelled_by / cancelled_at and the ledger entry |
| Sales return | Credit Note (CR-052) with SALES_RETURN movement | CR-091 posts the credit note to the customer ledger |
| Purchase return | Purchase cancel = PURCHASE_RETURN movement | unchanged |
| GST per line | `gst_rate_percent`, `line_gst_paise`, `LineDiscount.price()` | CR-091 splits CGST/SGST/IGST from place of supply, frozen per line |
| Customer outstanding | `customer.outstanding` derived from invoice balances | CR-091 `customer_ledger_entry` is the authoritative ledger |
| Profit | none (product purchase price only) | CR-091 weighted-average cost frozen on each sold line |
| Idempotency | `IdempotencyService` + `Idempotency-Key` header (V34) | CR-091 `sync_transaction` reuses it for offline replays |
| Tenant isolation | `findByIdAndTenantId` everywhere, `SecurityUtils.requireCurrentTenantId()` | CR-088 adds the cross-shop subscription test, CR-090 is the one deliberately cross-tenant read and is consent-gated |
| Audit | `activity_log`, `security_audit_log` | every new consent / subscription / selection writes to `activity_log` |
| pg_trgm | not installed | CR-089 migration installs it (`CREATE EXTENSION IF NOT EXISTS`) |

## CR-088 — Subscription plans & feature gating (V59)

- [x] `subscription_plan`, `feature`, `plan_feature`, `tenant_subscription`, `subscription_usage` tables, idempotent seed (ON CONFLICT DO NOTHING)
- [x] `Feature` enum (single definition), `SubscriptionStatus` enum, `UsageKey` enum
- [x] `FeatureAccessService.hasFeature / requireFeature` → `FeatureNotAvailableException` (403, `FEATURE_NOT_AVAILABLE`, errors map carries feature/currentPlan/requiredPlan)
- [x] Status semantics: TRIAL/ACTIVE/PAST_DUE = full plan; EXPIRED/CANCELLED/SUSPENDED = BASIC features only, data never deleted; trial length from `app.subscription.trial-days`
- [x] `UsageTrackingService` — WhatsApp/SMS/email counted in `NotificationServiceImpl.attempt`, AI in `AiChatService`; over-limit → `USAGE_LIMIT_REACHED` (429) / `QUOTA_EXCEEDED` log row, never a silent send
- [x] Existing gates moved onto features: AI → `AI_FEATURES`; prices for Razorpay checkout come from `subscription_plan`
- [x] `GET /v1/subscriptions/plans|current|features|usage`, `POST /v1/subscriptions/upgrade|cancel`, `GET /v1/features/{key}/access`
- [x] Frontend: `/settings/subscription` pricing page, `useFeatureAccess`, `UpgradeDialog` on 403 `FEATURE_NOT_AVAILABLE`, sidebar lock badge
- [x] Tests: `FeatureAccessServiceImplTest`, `SubscriptionControllerIT` (Basic→Premium API 403, Pro→403, Premium ok, expired denied, cross-shop)

## CR-089 — Smart Substitute (V60)

- [x] Product attribute columns (subcategory, size, length, width, material, color_finish, shape, usage, product_type, compatible_product), `pg_trgm` + GIN index on product_name
- [x] `product_relationship` (ALTERNATIVE/COMPATIBLE/UPGRADE/LOWER_COST/SAME_USE/REPLACEMENT)
- [x] `product_request`, `product_request_suggestion`, `substitute_setting` (min score, show above budget, max results)
- [x] `RecommendationStrategy` + `RuleBasedRecommendationStrategy` (weights from `app.substitute.scoring.*`) + `ManualMappingRecommendationStrategy` (always outranks)
- [x] Availability = real `stock.quantity_on_hand` ≥ requested quantity (no reservation mechanism exists - honest deviation, see docs); inactive/deleted never suggested
- [x] Reason text per suggestion; levels EXCELLENT/HIGH/MEDIUM/LOW
- [x] APIs: `POST /v1/product-requests`, `GET …/{id}/alternatives`, `POST …/{id}/select-alternative`, `GET /v1/products/{id}/alternatives`, `POST/DELETE /v1/products/{id}/alternative-mappings`
- [x] Frontend: Product Requests page (unavailable card, top 3 + "show more", compare table, select). Product-form attribute inputs and a mapping card on product detail are a small follow-up - fields are accepted by the API and rendered on the substitute screens
- [x] Tests: `RuleBasedRecommendationStrategyTest` (9), `ProductRequestIT` (8, covers the service orchestration end to end - it is what caught the sort-order bug)

## CR-090 — Nearby Product Discovery (V61)

- [x] `shop_discovery_setting` (5 consent flags, all false; latitude/longitude; radius km), `product_request_discovery_match`
- [x] `ShopDiscoveryService.discover(requestId)` — only opted-in tenants, Haversine within radius, availability bucket only, name/phone/location only when permitted
- [x] Owner notification row (`owner_notification`) + unread count; customer-facing response never contains matches
- [x] Consent changes → `activity_log`; disabling removes the shop from the next search immediately
- [x] APIs: `GET/PUT /v1/discovery/settings`, `POST /v1/product-requests/{id}/discover`, `GET /v1/product-requests/{id}/nearby`, `GET /v1/owner-notifications`, `POST …/{id}/read`
- [x] Frontend: Shop Settings "Product discovery sharing" card with explanation + double confirmation (list of what is shared, then type ENABLE), Nearby availability panel with Call/WhatsApp, Notifications page in the sidebar utility list
- [x] Tests: `ShopDiscoveryIT` (8 - opt-out invisible, radius, per-flag field withholding with a whole-body leak check, reciprocity, no-location refused, owner notified, Basic refused)

## CR-091 — Business logic completion (V62)

- [ ] GST: `supply_type` INTRA/INTER + `place_of_supply_state_code` on invoice; `cgst/sgst/igst_paise` per line and per invoice; `GstCalculator` unit tests; historical invoices untouched (backfilled from their own frozen rate)
- [ ] Invoice cancellation: reason (required), `cancelled_by`, `cancelled_at`; ledger reversal
- [ ] `customer_ledger_entry` + backfill; `CustomerLedgerService`; statement + ageing + balance endpoints; outstanding = Σ debit − Σ credit
- [ ] Cost basis: `stock.average_cost_paise` (weighted average on PURCHASE_RECEIPT), `invoice_item.cost_price_paise` frozen at sale, backfill from product purchase price; `GET /v1/analytics/profit` = revenue, COGS, gross, expenses, net
- [ ] Offline sync: `sync_transaction`, `POST /v1/sync/transactions` (batch, per-tenant client UUID unique, replay returns stored result), conflict detection (stock short, price changed, customer changed) → CONFLICT row, never a silent overwrite; frontend outbox (IndexedDB) + Sync page
- [ ] Tests: `GstCalculatorTest`, `CustomerLedgerIT` (10,000 − 3,000 − 2,000 − 1,000 return = 4,000), `ProfitHistoricalCostIT`, `OfflineSyncIT` (duplicate upload → one invoice, one movement, one ledger row)

## CR-092 — Premium growth pack (V63)

- [ ] `branch` (one MAIN per tenant backfilled), `branch_stock`, `branch_id` on invoice/purchase/stock_movement/app_user; STOCK_TRANSFER_IN/OUT; `/v1/branches`, `/v1/branches/transfers`, branch-wise summary
- [ ] `/v1/insights/*`: slow-moving, overstock, reorder suggestion, demand trend, frequently-bought-together, pricing insight — computed from invoice/stock rows, "—" when the window is empty
- [ ] Daily business summary job (Premium, 20:30 IST) over the tenant's own channel; counted as usage
- [ ] `tenant_backup` history + `POST /v1/backups` snapshot (reuses the CR-057 tenant export) + download; job for Premium nightly
- [ ] Frontend: Branches page, Insights page, Backups card
- [ ] Tests: `BranchStockTransferIT`, `InsightsServiceIT`, `TenantBackupIT`

## Final — verification & docs

- [ ] `docs/BUSINESS_RULES_GST_STOCK_LEDGER_PROFIT.md`, `docs/SUBSCRIPTION_FEATURE_MATRIX.md`, `docs/OFFLINE_SYNC.md`
- [ ] Registries: DATABASE_REGISTRY (V57–V61), API_REGISTRY, SECURITY_REGISTRY, FEATURE_REGISTRY, CHANGE_REQUEST bodies, RESUME_POINT
- [ ] `mvn -o clean verify` (needs Docker) — result quoted in RESUME_POINT
- [ ] `tsc -b --force`, `vite build`, `node tests/run.mjs` — results quoted
