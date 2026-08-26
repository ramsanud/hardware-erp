# MASTER PROJECT STATUS

**Written:** 2026-08-23, Phase 0 audit, in response to the ERP-expansion
request (Project Management, Labour, Finance, Reports, Notifications-v2,
AI-v2, SAML). This document is the single current-state source of truth
until the stale registries below are brought current — see "Documentation
drift" first, because two of them actively contradict what the codebase
actually does.

---

## 1. Documentation drift found during this audit — read this before trusting the other registry files

`RESUME_POINT.md`, `CHANGE_REQUEST_REGISTRY.md`, `BUG_REGISTRY.md`,
`API_REGISTRY.md` and `DATABASE_REGISTRY.md` were kept current through
CR-028 (today). The other five were **not** and are stale as of roughly
CR-020/021 (2026-08-22, before Customer, Quotation, Payment, Notifications,
Subscription, AI, Coupons, Tenant-registration, or the security fix):

| File | Describes itself as | Actually stale on |
|---|---|---|
| `PROJECT_REGISTRY.md` | Module status table | Missing Modules 5 (Customer), 10 (Quotation), 12 (Payment), and everything from CR-022 onward. **Money row says `DECIMAL(15,2)`; the codebase has used `BIGINT` paise since Module 6/7 and every table since. This is wrong, not just outdated — do not follow it.** |
| `FEATURE_REGISTRY.md` | Feature list | Predates Customer, Quotation, Payment, Notifications, Subscription tiers, AI, Coupons |
| `MODULE_DEPENDENCY_MAP.md` | Dependency diagram | Says "one MySQL database" (CR-014 moved to PostgreSQL) and doesn't show Customer/Quotation/Payment at all |
| `SECURITY_REGISTRY.md` | Auth/security design | Never mentions CR-016 (multi-tenancy) anywhere — reads as single-shop. Doesn't mention the CR-028 security_audit_log tenant-isolation fix (BUG-SEC-001) or the registration rate-limit rule |
| `VERSION.md` | Version history | Last entry is v0.4.0 (PostgreSQL migration, 2026-08-13/22) — nothing from CR-016 through CR-028 |

**Action taken now**: `PROJECT_REGISTRY.md`'s module table and money-type row,
and `SECURITY_REGISTRY.md`'s missing multi-tenancy section, are corrected
below in Phase 1's checklist (small, mechanical fixes). `FEATURE_REGISTRY.md`,
`MODULE_DEPENDENCY_MAP.md` and `VERSION.md` are large enough rewrites that
they're scheduled as their own Phase 1 task rather than done inline here, so
this audit document doesn't balloon into a full registry rewrite. Until then,
treat this file plus `RESUME_POINT.md`/`CHANGE_REQUEST_REGISTRY.md` as
authoritative over the five above.

---

## 2. Current state — verified, not assumed

### Stack (unchanged, confirmed)
Java 21, Spring Boot 3.4.2, PostgreSQL 16, Flyway, React 18 + TypeScript +
Vite 6 + Tailwind + shadcn/ui. One Spring Boot JAR, one React app, one shared
schema, `tenant_id`-discriminated multi-tenancy (CR-016) — **this part of
CLAUDE.md is current and correct.**

### Backend module packages (verified via `ls`, 2026-08-23)
`ai`, `auth`, `coupon`, `customer`, `dashboard`, `inventory`, `invoice`
(contains both Invoice **and** Payment controllers), `notification`,
`product` (contains Category **and** Brand controllers too), `quotation`,
`security`, `supplier`, `tenant`. Frontend mirrors this with its own
`payment` module folder for UI purposes even though Payment has no separate
backend package.

### Database — 30 tables, 20 applied migrations, next free version is **V17**
```
activity_log, app_user, brand, category, coupon, coupon_product, customer,
invoice, invoice_item, notification_log, password_reset_token, payment,
permission, product, quotation, quotation_item, refresh_token, role,
role_permission, security_audit_log, stock, stock_movement, supplier,
supplier_contact, tenant, tenant_logo, tenant_signature, tenant_upi_qr,
user_avatar
```
Applied: V1 auth, V2 supplier, V3 activity_log, V4 token_hash fix, V5
supplier column fix, V6 multi-tenant foundation, V7 category/brand/product,
V8 inventory, V9 customer/invoice/payment, V10 invoice GST + quotation, V11
image storage, V12 invoice GST document fields, V13 tenant UPI QR, V14
notification log, V15 subscription tier, V16 coupons. Seed migrations V900-903
(dev/test only, excluded from prod).

### Permission catalog — 33 permissions exist today (verified via live query)
```
AUDIT_VIEW, COUPON_MANAGE, COUPON_VIEW, CUSTOMER_MANAGE, CUSTOMER_VIEW,
EXPENSE_MANAGE, EXPENSE_VIEW, INVENTORY_ADJUST, INVENTORY_VIEW,
INVOICE_CANCEL, INVOICE_CREATE, INVOICE_DISCOUNT_OVERRIDE, INVOICE_VIEW,
PAYMENT_MANAGE, PAYMENT_VIEW, PRODUCT_MANAGE, PRODUCT_VIEW,
PRODUCT_VIEW_COST, PRODUCT_VIEW_STOCK, PURCHASE_MANAGE, PURCHASE_VIEW,
QUOTATION_MANAGE, QUOTATION_VIEW, REPORT_FINANCIAL, REPORT_VIEW, ROLE_MANAGE,
ROLE_VIEW, SETTINGS_MANAGE, SETTINGS_VIEW, SUPPLIER_MANAGE, SUPPLIER_VIEW,
USER_MANAGE, USER_VIEW
```
**Important find**: `EXPENSE_VIEW`/`EXPENSE_MANAGE`, `PURCHASE_VIEW`/
`PURCHASE_MANAGE`, and `REPORT_VIEW`/`REPORT_FINANCIAL` were seeded in `V1`
speculatively for modules that were never built. They already have
`PermissionCode` Java constants (`auth/entity/PermissionCode.java`). Reuse
them for the Finance/Expense module (§16-19 of the request) and the Reports
module (§23) rather than inventing new codes — `PURCHASE_VIEW`/`MANAGE` are
reserved for the actual Purchase module (still not started) and should not
be repurposed for anything else.

### Existing functionality by module — verified, not assumed

| Module | Backend | Frontend | Tests | Notes |
|---|---|---|---|---|
| Auth & Users | Done | Done | 149 written | JWT + opaque refresh, permission RBAC, rate limiting |
| Supplier | Done | Done (wizard) | 35 written | Bank account shown masked; **CR-018 encryption-at-rest not built** |
| Category/Brand/Product | Done | Done | 0 written | Pricing lives on `product` directly (documented deviation from original CR-004 shape) |
| Inventory | Done | Done | 0 written (exercised via Invoice tests) | `stock`/`stock_movement`, pessimistic-lock adjustment |
| Customer | Done (full CRUD) | Done | via CustomerServiceImplTest etc. | CR-023; financial summary just fixed (BUG-CUST-001) |
| Quotation | Done | Done | 7 written | No coupon redemption (deferred, see CR-028) |
| Invoice & Payment | Done | Done (4-step wizard) | 10+ written | Coupon-aware; PDF generation; GST split |
| Coupons | Done | Done | 9 written | Tenant-scoped, percent/flat, product-restricted |
| Tenant self-registration | Done | Done | 5 written | Public, rate-limited; real 2nd-tenant provisioning |
| Notifications | Done (email real, SMS/WhatsApp **stub-only**) | Done (contact-admin dialog; no log viewer) | 7 written | `NotificationProvider` interface already exists — real extension point |
| Subscription tiers | Done (FREE/PRO/MAX, self-declared) | Done | — | No payment gateway — feature-gating flag only |
| AI Assistant | Done, **Anthropic-only, no key configured** | Done (widget) | 4 written | `ChatCompletionClient` interface already exists — **already the clean abstraction the new request asks for in its §56**, just one implementation |
| Purchase | **Not started** | — | — | Locked dependency: needed for real Supplier Payables (see §7 below) |
| Product Variant | **Not started** | — | — | Deferred by design (documented deviation) |

**Not yet built anywhere in the codebase**: Cash/Bank/Cheque ledger,
Borrowed Money, Supplier Payables (blocked on Purchase), Payment
Reminders, scheduled/periodic reports, WhatsApp (real provider — today is
a logging stub), SAML/SSO, per-user theme/language (CR-019, proposed not
started), bank encryption (CR-018, proposed not started).

Labour/Employee/Attendance: **done (CR-036 phase 4, 2026-08-25)** — see
§3 Phase 7 below.

---

## 3. Scope of the new request, mapped against what exists

The request's Phases 1-5 significantly overlap with work already done:

| Their phase | Status |
|---|---|
| Phase 1 (close existing gaps) | **Done except CR-019.** Supplier wizard, login polish, permission grouping: done in earlier rounds. **CR-018 bank encryption: applied 2026-08-23.** Only CR-019 (per-user theme/language) remains open. |
| Phase 2 Inventory | **Done.** |
| Phase 3 Customer | **Done.** |
| Phase 4 Sales/Quotations | **Done.** |
| Phase 5 Invoice/Payment | **Done.** |
| Phase 6 Project Management | **Done (CR-029, 2026-08-23).** Work types, projects, materials, expenses, payments, server-computed profitability, rooftop calculator. Full backend + frontend, 10 tests, live browser-verified end to end (create → add material → add expense → record payment → complete with outcome → profit/margin/balance all correct). **Known gap, stated plainly: adding a project material does not decrement shop stock or write a `stock_movement` row** - the request's own §12 lists `PROJECT_CONSUMPTION` as a stock movement type; this round built the project-material ledger itself but did not wire it into `StockService`. Flagged for the next Project Management pass, not silently claimed as done. |
| Phase 7 Labour/Team/Attendance | **Done (CR-036 phase 4, 2026-08-25).** Worker directory, simple daily present/absent/half-day attendance (batch-marked, corrections update in place), live-computed wage/payroll, worker payments with earned-vs-paid balance, additive labour-cost figure on Project detail. `project_expense`'s LABOUR/EMPLOYEE categories remain as a separate manual entry option, unchanged. Backend unit-tested (255/255) and live-curl-verified including RBAC and cross-tenant isolation; live browser/Playwright click-through not performed (no browser tool available this session). |
| Phase 8 Finance/Cash/Bank/Cheque/Borrowed/Expenses | Not started (permission codes pre-seeded) |
| Phase 9 Reports | Not started (permission codes pre-seeded) - stock total value / daily / weekly / monthly / yearly reports all still to build |
| Phase 10 Notifications v2 | Partially done — channel abstraction exists, needs payment-reminder rule engine + real WhatsApp provider |
| Phase 11 AI v2 | **Provider abstraction extended 2026-08-23** - `GeminiChatCompletionClient` added alongside `AnthropicChatCompletionClient`, selected via `app.ai.provider` (`gemini` is now the default, since Google AI Studio has a genuine free tier). **Still REQUIRES REAL CREDENTIALS** - no `GEMINI_API_KEY` configured, so the assistant still correctly replies "isn't set up yet". New business-data tools beyond CR-027's original set not yet added. |
| Phase 12 SAML | Not started |
| Phase 13-16 (integration/browser/security/production testing) | Ongoing per-phase, not a one-time final step |

### 3.1 Second request: Customer 360 / Document Reuse / SaaS Limits (2026-08-23)

A separate 58-section master prompt arrived after Phase 6 shipped, with its
own "Phase 1" numbering — do not confuse it with the table above. Its
recommended Phase 1 (Customer 360 + document reuse, no re-entry) is
**done (CR-030, 2026-08-23)**: Customer Detail Invoices/Quotations/
Products-purchased tabs, "New quotation"/"New invoice" quick actions that
pre-fill the wizard via router state. Live browser-verified. See CR-030
in `RESUME_POINT.md` for full detail.

§17 credit-limit warning is also **done (2026-08-23)**: an advisory
(non-blocking) warning on the Invoice wizard's Review step when the sale
would push an existing customer's outstanding balance past their credit
limit, backed by a new `GET /v1/customers/credit-check?mobile=...`
exact-mobile lookup. Live browser-verified.

Also found and fixed in this same testing pass, outside either master
prompt's explicit scope: **BUG-INV-001** - `StockServiceImpl
.applyMovement()` never checked whether a movement would drive
`quantity_on_hand` negative, so overselling a product silently produced
an impossible negative stock count. Fixed at the one choke point every
stock mutation passes through. Full detail in `BUG_REGISTRY.md`.

§45-46 "Repeat" is also **done (2026-08-23)**: a button on Invoice/Quotation
Detail that starts a new document with the same customer and product
quantities, always at today's price. Frontend-only, live-verified for
both document types. §43 duplicate-customer detection was checked and
found already built from an earlier round (exact-mobile rejection,
server-side) - verified, not new work.

§15 inline "Add Product" is also **done (2026-08-23)**: the shared
`ProductPicker` (Invoice + Quotation wizards) offers "Add as a new
product" on an empty search, reusing the Product module's own form in a
stacked dialog, no navigation away. Frontend-only, live-verified.

§12-13 dashboard quick actions **done (2026-08-23)**: added the missing
"New quotation" button next to the existing "New invoice" one. No
customer picker added in front of either — the wizards' existing
free-text + auto-match-by-mobile flow already is "select existing or
create new," so a picker would have regressed a proven flow.

§18-23 archive/soft-delete safety is **done (2026-08-23)**, mostly by
audit rather than new code: Customer/Supplier/Product already never
hard-delete and all have an Active/Inactive list filter. The one real gap
- Customer had no way to reactivate a deactivated record through the UI,
unlike Supplier/Product - is fixed (`status` added to `CustomerRequest`,
a Status select added to `CustomerForm` in edit mode). Live-verified full
round trip: deactivate → confirm Inactive → reactivate → confirm Active.

§24-26 Security Audit Log detail view is **done (2026-08-23)** for
everything except actor role: a click-through dialog now shows IP, user
agent, request id, resource, and status - all of which existed in the
API response but were never rendered anywhere in the UI. Actor *role*
capture explicitly deferred - it needs a new `security_audit_log` column
plus changes at 5 separate call sites (no shared choke point for it the
way `write()` is for everything else), a bigger change than justified
alongside this round's other fixes.

§27-40 SaaS subscription entitlement limits are **done (2026-08-23,
CR-031)** - the last major item from this second request. `SubscriptionTier`
(FREE/PRO/MAX) carries real numeric limits now (FREE 1 owner/100
customers/100 suppliers/1000 products, PRO 2/1000/1000/10000, MAX
unlimited), enforced server-side via a new `EntitlementService` at
every create path (owner/customer/supplier/product), plus a "Plan usage"
dashboard in Shop Settings with per-resource progress bars. Live-verified
by temporarily dropping the primary test tenant to FREE (it already
exceeds every FREE limit from earlier load-testing) and confirming all
three create paths were rejected with the correct message, then
restoring MAX and confirming normal operation resumed. Full detail in
`RESUME_POINT.md` / CR-031 in `CHANGE_REQUEST_REGISTRY.md`.

**This closes out everything explicitly scoped from the Customer 360/
Document Reuse master prompt.** Remaining known gaps across both master
prompts this session covered: Labour/Team/Attendance, Finance ledger,
Reports (stock-value/daily/weekly/monthly/yearly), Notifications v2, new
AI tools, SAML (all from the earlier ERP-expansion prompt, Phase 7-16,
deferred since CR-029); Security Audit Log actor-role capture (deferred
since CR-030 §24-26, needs a migration + 5 call sites).

**This changes the honest starting point**: real new work is Phase 6
onward, plus the two Phase-1 leftovers. I'm not going to re-verify or touch
already-shipped Phases 2-5 as part of this effort beyond what's needed when
a new module (e.g. Project) references them (e.g. Project → Customer FK).

---

## 4. Architecture decisions made now, before any code — per the request's own instruction not to invent silently

### 4.1 Project lifecycle state machine (request §5)

Two orthogonal fields, not one flat enum — this is the fix for "do not
duplicate SUCCESS and COMPLETED blindly":

- **`status`** (lifecycle, one value at a time): `UPCOMING` → `IN_PROGRESS` →
  `COMPLETED`, with `ON_HOLD` and `CANCELLED` as exceptional states reachable
  from `UPCOMING`/`IN_PROGRESS`. `VARCHAR(20)` + CHECK, per the locked
  status-column convention.
- **`outcome`** (business result, nullable): `NULL` while `status !=
  COMPLETED`; set to `SUCCESS` or `FAILURE` **only** when a project reaches
  `COMPLETED`, and set explicitly by the project manager/owner at that point
  — never auto-inferred from the profit number, because a project can finish
  on-budget but with an unhappy customer, or over-budget but still count as
  a win for relationship reasons. The system will surface the calculated
  profit as a *strong suggestion* next to the outcome picker, never as a
  silent auto-decision.
- **`OVERDUE` is not a stored value.** Like `Quotation.isExpired()`, it's
  computed at read time: `status IN ('UPCOMING','IN_PROGRESS') AND
  customer_deadline < today`. Storing it as a status would create a second
  source of truth that drifts the moment someone forgets to run a batch job.

### 4.2 Money — BIGINT paise, matching actual practice (not the stale registry)

Every project/finance money field is `BIGINT ... _paise`, exactly like
Invoice/Product/Coupon/Payment. `PROJECT_REGISTRY.md`'s `DECIMAL(15,2)` row
is corrected in Phase 1 cleanup to stop misleading the next reader.

### 4.3 Finance ledger — append-only transactions, derived balances, matching 4 existing precedents

The codebase already has this exact pattern four times: `activity_log`,
`security_audit_log`, `notification_log`, `stock_movement` — all append-only,
all the record of truth, nothing ever computed from a mutable running total.
Finance follows it:

- **`financial_transaction`**: append-only ledger. `account_type`
  (`CASH`/`BANK`/`CHEQUE`), signed `amount_paise` (positive = in, negative =
  out), `transaction_type` (`SALE_RECEIPT`, `SUPPLIER_PAYMENT`,
  `SHOP_EXPENSE`, `PROJECT_EXPENSE`, `BORROWED_MONEY_IN`,
  `BORROWED_MONEY_REPAYMENT`, `TRANSFER`), polymorphic `reference_type`/
  `reference_id` (same pattern as `notification_log`), `description`,
  `tenant_id`, `created_by`, `created_at`. **Never updated once written.**
- **Account balance is `SUM(amount_paise) WHERE account_type = X`**, computed
  on read (a single indexed aggregate query) — not a cached mutable column
  that can drift from the ledger. Revisit only if real transaction volume
  ever makes that query slow, which a single hardware shop will not produce.
- **`borrowed_money`**: separate table (lender, amount, date, due date,
  status). Borrowing writes one `BORROWED_MONEY_IN` ledger row (increases
  available cash/bank); repayment writes `BORROWED_MONEY_REPAYMENT` rows.
  **Profit/P&L calculations only ever sum `SALE_RECEIPT`/`*_EXPENSE`-classed
  transactions** — `BORROWED_MONEY_*` rows move cash but are structurally
  excluded from every profit query, which is how "borrowing isn't profit,
  repaying isn't an expense" is enforced without a parallel bookkeeping
  system.

### 4.4 Supplier Payables genuinely depends on Purchase, which doesn't exist yet

The request's §19 (Supplier Payables: invoice/bill, amount, paid, balance)
needs a real "bill from a supplier" concept. That's the Purchase module —
already locked in the original module order, still not started. Building
"Supplier Payables" without it would mean inventing a parallel, disconnected
bills table that duplicates what Purchase is supposed to own. **Recommendation:
Purchase becomes a prerequisite sub-phase inside Phase 8, not skippable** —
this is PROJECT_SKILLS lesson #22 (module order follows data dependency)
applying again, exactly as it did for Invoice needing Inventory. Customer
Receivables has no such gap — it's already fully derivable from existing
`invoice.balance_paise`.

### 4.5 Daily project profit (request §8) — distinguishing real data from an estimate

Per the request's own instruction not to invent a revenue-allocation rule:
daily project accounting will show, separately and labeled:
- **Actual customer payment received that day** (real, from a payment record
  tied to the project).
- **Project total value / total received to date** (real).
- **Daily cost** (real — sum of that day's material consumption + labour +
  food/stay/petrol + other project expense rows).
- **"Estimated daily operational profit"** — daily cost subtracted from an
  even allocation of the project's total value across its planned duration,
  **explicitly labeled as an estimate**, never presented as a real profit
  figure. This is the honest version of "distinguish real vs. calculated"
  the request asks for.

### 4.6 Material requirement calculators (request §24-29)

Configurable per work type, not hardcoded formulas: a `work_type` has zero or
more `calculation_rule` definitions (simple parametric formulas: input
fields like width/length/overlap/wastage%, output = a formula over them).
Rooftop/SS/Gate/Mica/Glass all become *data* (work types + their fields +
their default wastage%), not distinct Java classes — matching the request's
explicit "do not restrict to only these, allow configurable X" instruction
repeated for every material section. Calculated quantity is always shown next
to a user-overridable quantity, with the difference displayed, exactly as
asked.

### 4.7 AI provider — the abstraction already exists

`ai/ChatCompletionClient` interface + `AnthropicChatCompletionClient` is
already exactly what request §56 asks for. Adding Gemini free-tier: one new
`GeminiChatCompletionClient implements ChatCompletionClient`, selected via
`app.ai.provider: anthropic|gemini` (`@ConditionalOnProperty`), no change to
`AiChatService` or any existing tool. **Needs a real Google AI Studio API key
from the owner before it can answer anything** — same "isn't set up yet"
graceful-degradation pattern already built, extended to whichever provider
is configured.

### 4.8 SAML/SSO — abstraction only until real IdP credentials exist

Per the request's own §42 instruction ("if actual identity-provider
credentials are unavailable, implement provider abstraction/configuration
and tests using mocks"): Spring Security's official
`spring-security-saml2-service-provider` is the correct library (not a
hand-rolled SAML flow). Scoped as an **additive, optional, per-tenant**
authentication path — existing mobile/email+password login is never removed
or altered. This stays firmly Phase 12; no code for it until Phases 6-11 are
real.

---

## 5. New permissions planned (not yet created — listed for review before Phase 6 migration)

Following the existing naming convention (`<MODULE>_VIEW`/`<MODULE>_MANAGE`,
plus narrow verbs like `PRODUCT_VIEW_COST` where a field needs finer control
than the whole resource):

```
PROJECT_VIEW, PROJECT_MANAGE
PROJECT_MATERIAL_VIEW, PROJECT_MATERIAL_MANAGE
LABOUR_VIEW, LABOUR_MANAGE
ATTENDANCE_VIEW, ATTENDANCE_MANAGE
FINANCE_VIEW, FINANCE_MANAGE
NOTIFICATION_MANAGE (NOTIFICATION_VIEW-equivalent already exists as nothing — the existing GET /v1/notifications/log is gated by SETTINGS_VIEW today; revisit whether it deserves its own code when the Notification module is extended in Phase 10)
AI_USE (today's AI endpoint has no dedicated permission at all — "authenticated, any user" gated only by subscription tier; revisit in Phase 11 whether that's still correct once AI gets write-adjacent tools)
BANK_ACCOUNT_VIEW (CR-018, Phase 1 leftover)
```
`INVENTORY_ADJUST` already covers inventory write actions — no new
`INVENTORY_MANAGE` needed. `EXPENSE_VIEW`/`MANAGE`, `PURCHASE_VIEW`/`MANAGE`,
`REPORT_VIEW`/`REPORT_FINANCIAL` are reused as-is (§2 above).

---

## 6. Risks

- **Scope**: this is 10+ new modules, a ledger-grade finance subsystem, and a
  payroll-adjacent labour system, on top of a project that's ~58% through its
  *original* 12-module scope. Treating this as one atomic delivery would mean
  either shipping it superficially (violating the request's own §59 "no fake
  completion") or taking many more sessions than a single response can cover
  honestly. Proceeding phase-by-phase, verified at each step, is the only way
  to keep every claim of "done" true.
- **Purchase gap**: Supplier Payables (§4.4) cannot be built correctly without
  Purchase existing first — this was already true before this request and is
  now load-bearing for Phase 8.
- **Docker/Testcontainers (BUG-ENV-002, still open)**: every new module's
  integration tests will hit the same pre-existing environment gap that's
  blocked 2 test classes all session. Unit tests + live verification remain
  the working substitute, as they have been throughout.
- **AI without a real key**: until a Gemini (or other) key is actually
  provided, Phase 11's new AI tools can be built and unit-tested but not
  live-verified end-to-end — same honest gap as today's Anthropic path.
- **SAML without a real IdP**: Phase 12 can only ever be abstraction +
  mocked tests without real identity-provider credentials, per the request's
  own instruction.

---

## 7. Testing requirements (per module, going forward)

Every new service method: unit tests with a real authenticated
`SecurityContextHolder` principal (tenant-scoped, matching the pattern in
every existing `*ServiceImplTest`). Every financial calculation: an explicit
test matching request §51's mandatory cases (borrow/repay leaves profit
unchanged, project revenue-minus-costs arithmetic, invoice payment status
transitions — the last one is already covered by existing
`InvoiceServiceImplTest`). Every new controller: `@PreAuthorize` present and
tenant-scoped via `SecurityUtils`, checked the same way the CR-028 RBAC audit
checked all 19 existing controllers. Every new module gets a live
browser-verification pass before being called done, matching this session's
established standard (not just compile/test).

---

## 8. Recommended next action

Start **Phase 1 leftovers** (small, bounded, closes real open gaps before
adding new surface area) in parallel with beginning **Phase 6 (Project
Management)** design — migration + entities first, verified by compile and
unit tests, then frontend, then live browser verification, matching the
pattern used for every module this session. Report against the request's own
§60 checklist after each phase.
