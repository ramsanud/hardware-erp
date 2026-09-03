# API REGISTRY

Base: `http://localhost:8080/api` · Swagger: `/api/swagger-ui.html`
Envelope (success): `{ "success": true, "message": ..., "data": ..., "timestamp": ... }`
Envelope (error): `{ "success": false, "message": ..., "code": ..., "timestamp": ..., "errors": {...} }`

## Module 1 — Authentication

| Method | Path | Permission | Status | Notes |
|---|---|---|---|---|
| POST | `/v1/auth/login` | public | LOCKED | body `{identifier, password}` |
| POST | `/v1/auth/refresh` | public | LOCKED | refresh token from cookie or body |
| POST | `/v1/auth/logout` | authenticated | LOCKED | this device only |
| POST | `/v1/auth/logout-all` | authenticated | LOCKED | all devices, bumps token_version |
| GET | `/v1/auth/me` | authenticated | LOCKED | identity from SecurityContext, never from a body field |
| PUT | `/v1/auth/me` | authenticated | LOCKED | own name/email only |
| POST | `/v1/auth/change-password` | authenticated | LOCKED | |
| POST | `/v1/auth/forgot-password` | public | LOCKED | always 200, never reveals existence |
| POST | `/v1/auth/reset-password` | public | LOCKED | single-use token |
| GET | `/v1/auth/sessions` | authenticated | **NEW (CR-003)** | active sessions for the current user |
| DELETE | `/v1/auth/sessions/{id}` | authenticated | **NEW (CR-003)** | revoke one session |

## Module 1 — Users

| Method | Path | Permission | Success | Notes |
|---|---|---|---|---|
| GET | `/v1/users` | USER_VIEW | 200 | search, filter, paginate, whitelisted sort, max size 100 |
| GET | `/v1/users/{id}` | USER_VIEW | 200 | |
| POST | `/v1/users` | USER_MANAGE | 201 | **this is the "registration" endpoint — see CR-008** |
| PUT | `/v1/users/{id}` | USER_MANAGE | 200 | |
| POST | `/v1/users/{id}/reset-password` | USER_MANAGE | 200 | forces must_change_password |
| DELETE | `/v1/users/{id}` | USER_MANAGE | 204 | soft delete; last-owner protected |

## Module 1 — Roles & Permissions

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `/v1/roles` | ROLE_VIEW | 200 |
| GET | `/v1/roles/{id}` | ROLE_VIEW | 200 |
| POST | `/v1/roles` | ROLE_MANAGE | 201 |
| PUT | `/v1/roles/{id}` | ROLE_MANAGE | 200 |
| DELETE | `/v1/roles/{id}` | ROLE_MANAGE | 204 |
| GET | `/v1/permissions` | ROLE_VIEW | 200 | **NEW (CR-003)** grouped by `module_code` for the picker |

`GET /v1/roles/permissions` from the previous baseline is **superseded** by
`GET /v1/permissions`. Permissions are now their own resource, not a sub-resource
of roles.

## Status codes in use

| Code | When |
|---|---|
| 200 | read, update, action succeeded |
| 201 | resource created |
| 204 | delete succeeded, no body |
| 400 | malformed JSON, bad parameter, validation failure |
| 401 | missing/invalid/expired token, invalid credentials |
| 403 | authenticated but lacks the permission |
| 404 | resource or endpoint not found |
| 405 | method not supported |
| 409 | duplicate resource, stale record (optimistic lock) |
| 422 | business rule violated (last owner, weak bootstrap password) |
| 429 | rate limit exceeded (includes `Retry-After`) |
| 500 | bug — generic message only, full detail in the server log |


## Module 2 - Suppliers

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `/v1/suppliers` | SUPPLIER_VIEW | 200 |
| GET | `/v1/suppliers/cities` | SUPPLIER_VIEW | 200 |
| GET | `/v1/suppliers/{id}` | SUPPLIER_VIEW | 200 |
| POST | `/v1/suppliers` | SUPPLIER_MANAGE | 201 |
| PUT | `/v1/suppliers/{id}` | SUPPLIER_MANAGE | 200 |
| DELETE | `/v1/suppliers/{id}` | SUPPLIER_MANAGE | 204 |
| POST | `/v1/suppliers/{id}/contacts` | SUPPLIER_MANAGE | 201 |
| PUT | `/v1/suppliers/{id}/contacts/{contactId}` | SUPPLIER_MANAGE | 200 |
| DELETE | `/v1/suppliers/{id}/contacts/{contactId}` | SUPPLIER_MANAGE | 204 |

Search parameters on `GET /v1/suppliers`: `search` (name, code, mobile,
contact, GST, city), `status`, `city`, `page`, `size` (clamped 100), `sortBy`
(whitelisted), `sortDir`.

Module-specific error: **422** when a supplied GSTIN's first two digits
disagree with the address `state_code`. Getting that wrong means every purchase
is taxed under the wrong head.

Total endpoints: Module 1 = 25, Module 2 = 9, **34 overall** (excludes
Modules 3–9, 10, 11 added since — this registry has not been kept current
for every module; see each module's controller for its actual endpoints).

## Module 10 - Quotations (CR-022)

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `/v1/quotations` | QUOTATION_VIEW | 200 |
| GET | `/v1/quotations/{id}` | QUOTATION_VIEW | 200 |
| POST | `/v1/quotations` | QUOTATION_MANAGE | 201 |
| PATCH | `/v1/quotations/{id}/status` | QUOTATION_MANAGE | 200 |
| POST | `/v1/quotations/{id}/convert` | INVOICE_CREATE | 200 |
| GET | `/v1/quotations/{id}/pdf` | QUOTATION_VIEW | 200, `application/pdf` (CR-026) |

Search parameters on `GET /v1/quotations`: `search`, `status`, `fromDate`,
`toDate` (ISO date, inclusive), `page`, `size`, sort fixed to
`quotationDate desc`. `convert` requires `INVOICE_CREATE` rather than
`QUOTATION_MANAGE` because its effect is creating an invoice, through the
exact same `InvoiceService.create()` path a normal invoice uses (stock
decrements, GST recalculated from current product rates). 422
`PAYMENT_EXCEEDS_TOTAL`-style business errors: converting an expired
quotation, or one that is `REJECTED`/`CONVERTED` already.

## Invoice PDF + shop settings (CR-022)

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `/v1/invoices/{id}/pdf` | INVOICE_VIEW | 200, `application/pdf` |
| GET | `/v1/settings` | SETTINGS_VIEW | 200 |
| PUT | `/v1/settings` | SETTINGS_MANAGE | 200 |
| GET | `/v1/settings/usage` | SETTINGS_VIEW | 200 - subscription-tier entitlement usage, CR-031 |
| GET | `/v1/subscription-coupons` | SETTINGS_MANAGE | 200 - CR-032 |
| POST | `/v1/subscription-coupons` | SETTINGS_MANAGE | 201 - CR-032 |
| PUT | `/v1/subscription-coupons/{id}` | SETTINGS_MANAGE | 200 - CR-032 |
| DELETE | `/v1/subscription-coupons/{id}` | SETTINGS_MANAGE | 204 - CR-032 |
| POST | `/v1/subscription-coupons/redeem` | SETTINGS_MANAGE | 200 - grants the tenant's own plan a free trial, CR-032 |

`InvoiceRequest`/`QuotationRequest` also gained `customerGstNo` and
`customerStateCode` (both optional) - previously the only way `customer.gst_no`
got set was never, despite the column existing since CR-021.

## Module 5 - Customers, images, dashboard (CR-023)

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `/v1/customers` | CUSTOMER_VIEW | 200 |
| GET | `/v1/customers/{id}` | CUSTOMER_VIEW | 200 |
| POST | `/v1/customers` | CUSTOMER_MANAGE | 201 |
| PUT | `/v1/customers/{id}` | CUSTOMER_MANAGE | 200 |
| DELETE | `/v1/customers/{id}` | CUSTOMER_MANAGE | 204 (deactivates, not a hard delete) |
| GET | `/v1/customers/{id}/financial-summary` | CUSTOMER_VIEW | 200 |
| GET | `/v1/customers/{id}/invoices` | CUSTOMER_VIEW | 200 |
| GET | `/v1/customers/{id}/quotations` | CUSTOMER_VIEW | 200 (CR-030) |
| GET | `/v1/customers/{id}/products` | CUSTOMER_VIEW | 200 — purchase history, CR-030 |
| GET | `/v1/customers/credit-check?mobile=...` | CUSTOMER_VIEW | 200 if found, 404 if no customer at that mobile — CR-030 §17 |
| GET / PUT / DELETE | `/v1/auth/me/avatar` | authenticated (self only) | 200/204 |
| GET / PUT / DELETE | `/v1/settings/logo` | authenticated to view, SETTINGS_MANAGE to change | 200/204 |
| GET / PUT / DELETE | `/v1/settings/signature` | authenticated to view, SETTINGS_MANAGE to change | 200/204 |
| GET / PUT / DELETE | `/v1/settings/upi-qr` | authenticated to view, SETTINGS_MANAGE to change | 200/204 (CR-026) |
| GET | `/v1/settings/brand` | authenticated (any user) | 200 - `{name, hasLogo, subscriptionTier}` only, unlike `GET /v1/settings` which needs SETTINGS_VIEW (subscriptionTier added CR-027, so any staff member - not only SETTINGS_VIEW holders - can tell whether a tier-gated feature is available) |
| GET | `/v1/dashboard/sales-summary` | INVOICE_VIEW | 200 |

`PUT` image endpoints are `multipart/form-data`, field name `file`, 2MB cap
(`ImageValidation`). `TenantSettingsRequest` gained a required `name` field
(shop name was previously not editable via Settings at all).

## Module 12 - Payments, Notifications, Subscription, AI Assistant (CR-027)

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `/v1/payments` | PAYMENT_VIEW | 200 - cross-invoice payment search/history, read-only; creation stays at `POST /v1/invoices/{id}/payments` |
| GET | `/v1/notifications/log` | SETTINGS_VIEW | 200 - every notification attempt (email/SMS/WhatsApp), sent or logged-only or failed |
| POST | `/v1/ai/chat` | authenticated (any user) | 200 - `{message, history}` in, `{reply}` out; 402 `SUBSCRIPTION_TIER_REQUIRED` if the tenant is below the Max plan |

`GET /v1/payments` search parameters: `search` (invoice number, customer
name, mobile), `paymentMethod`, `fromDate`/`toDate` (inclusive), `page`,
`size`. `TenantSettingsRequest`/`TenantSettingsResponse` both gained
`subscriptionTier` (`FREE`/`PRO`/`MAX`) - self-declared by the shop owner via
`PUT /v1/settings`, since no payment gateway exists to actually bill a plan
change. `POST /v1/ai/chat` never persists conversation history server-side -
the client resends prior turns each call. Every AI answer comes from a small
fixed set of read-only tools (customer balance, low stock, sales summary,
invoice search), each already tenant-scoped through the existing service
layer and only offered to the model if the caller holds that tool's
permission - never a free-form query against the database.

## Project Management (CR-029)

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `/v1/work-types` | PROJECT_VIEW | 200 - every work type this tenant has defined |
| POST | `/v1/work-types` | PROJECT_MANAGE | 201 |
| PUT | `/v1/work-types/{id}` | PROJECT_MANAGE | 200 |
| GET | `/v1/projects` | PROJECT_VIEW | 200 - paged search, `search`, `status`, `customerId` |
| POST | `/v1/projects` | PROJECT_MANAGE | 201 |
| GET | `/v1/projects/{id}` | PROJECT_VIEW | 200 - includes server-computed profitability |
| PUT | `/v1/projects/{id}` | PROJECT_MANAGE | 200 |
| PATCH | `/v1/projects/{id}/status` | PROJECT_MANAGE | 200 - `outcome` required iff `status=COMPLETED`, 422 otherwise |
| GET/POST | `/v1/projects/{id}/materials` | PROJECT_MATERIAL_VIEW / _MANAGE | 200/201 |
| PUT/DELETE | `/v1/projects/{id}/materials/{materialId}` | PROJECT_MATERIAL_MANAGE | 200/204 |
| GET/POST | `/v1/projects/{id}/expenses` | PROJECT_VIEW / PROJECT_MANAGE | 200/201 |
| DELETE | `/v1/projects/{id}/expenses/{expenseId}` | PROJECT_MANAGE | 204 |
| GET/POST | `/v1/projects/{id}/payments` | PROJECT_VIEW / PROJECT_MANAGE | 200/201 |
| POST | `/v1/projects/calculators/rooftop-sheet` | PROJECT_MATERIAL_VIEW | 200 - stateless estimator, nothing persisted |

`ProjectResponse` never trusts a client-supplied profit figure - material
cost, expense cost, net profit/loss, margin %, received and balance
receivable are all computed server-side from `project_material`/
`project_expense`/`project_payment` on every read.

## Supplier bank account reveal (CR-018)

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `/v1/suppliers/{id}/bank-account-number` | SUPPLIER_VIEW_BANK_ACCOUNT | 200 - the decrypted, unmasked account number as a plain string; logs `BANK_ACCOUNT_REVEALED` to security_audit_log |

Every other supplier endpoint keeps returning the last-4-masked value only.
This is the one path that ever returns the full number, and it's audited on
every call.

## Coupons, tenant self-registration, contact admin (CR-028)

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `/v1/coupons` | COUPON_VIEW | 200 - paged search, `search` (code), `status` |
| GET | `/v1/coupons/{id}` | COUPON_VIEW | 200 - full detail incl. restricted product list |
| POST | `/v1/coupons` | COUPON_MANAGE | 201 |
| PUT | `/v1/coupons/{id}` | COUPON_MANAGE | 200 |
| DELETE | `/v1/coupons/{id}` | COUPON_MANAGE | 204 |
| POST | `/v1/tenants/register` | public (`permitAll`, rate-limited 5/hour/IP) | 201 - creates a new tenant + 4 default roles + owner account; body is `TenantRegistrationRequest` |
| GET | `/v1/tenants/register/slug-available` | public | 200 - `?slug=X`, live-checked while the owner types the shop name |
| POST | `/v1/notifications/contact-admin` | authenticated (any user) | 200 - `{subject, message}`, emails `app.support.admin-email` with the reporter's shop/name/mobile prepended |

`POST /v1/invoices` and (once wired) `POST /v1/quotations` gained an
optional `couponCode` field on the request body - validated, priced and
recorded server-side by `CouponService.calculateDiscount()`; an unknown,
expired, exhausted, below-minimum, or product-mismatched code is rejected
with a 422 `BusinessException` rather than silently charging full price or
silently discounting nothing. `InvoiceResponse` gained `couponCode`
(`null` when none applied) and `discountDisplay` (`null`, not `"0.00"`,
when the discount is zero/absent - the frontend uses this to decide whether
to render the discount row at all).

`POST /v1/tenants/register` deliberately has no `tenantId`/`tenantSlug`
parameter to select an existing tenant - it always creates a new one. This
is the real second-tenant provisioning flow CR-016 explicitly deferred;
`BootstrapOwnerInitializer`'s own code comment anticipated exactly this
follow-up. Login remains identifier-only with mobile/email still globally
unique across tenants (CR-016's standing trade-off, unchanged) - a new
registration is rejected with 409 if the mobile or email is already in use
by *any* tenant, not just the new one being created.

## CR-035: Purchase + Supplier Bill Import

| Method | Path | Permission | Notes |
|---|---|---|---|
| POST | `/v1/purchases` | `PURCHASE_MANAGE` | Manual purchase entry - creates the purchase and increases stock (`PURCHASE_RECEIPT`) atomically |
| GET | `/v1/purchases` | `PURCHASE_VIEW` | Paged search - `search`, `status` |
| GET | `/v1/purchases/{id}` | `PURCHASE_VIEW` | |
| POST | `/v1/purchases/{id}/payments` | `PURCHASE_MANAGE` | Same overpayment guard as invoice payments |
| POST | `/v1/purchases/{id}/cancel` | `PURCHASE_MANAGE` | Reverses stock (`PURCHASE_RETURN`) |
| GET | `/v1/purchases/{id}/document` | `PURCHASE_VIEW` | The original uploaded bill file, only present if this purchase came from an import |
| POST | `/v1/purchases/import/preview` | `PURCHASE_MANAGE` | Multipart file upload - parses and matches, writes nothing |
| POST | `/v1/purchases/import/confirm` | `PURCHASE_MANAGE` | Multipart (file + JSON `request` part) - the only import endpoint that persists, one transaction |

`PURCHASE_VIEW`/`PURCHASE_MANAGE` already existed in the permission
catalogue since `V1` (seeded speculatively for a module that didn't exist
yet) and were already granted to OWNER/MANAGER/ACCOUNTANT in the default
role templates - no new permission codes were needed.

## CR-036: multi-bank-account invoice payments, invoice sharing, product image/import

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/v1/settings/bank-accounts` | `SETTINGS_VIEW` | List, masked account numbers |
| POST | `/v1/settings/bank-accounts` | `SETTINGS_MANAGE` | First account created always becomes default regardless of the flag sent |
| PUT | `/v1/settings/bank-accounts/{id}` | `SETTINGS_MANAGE` | |
| DELETE | `/v1/settings/bank-accounts/{id}` | `SETTINGS_MANAGE` | Soft-delete (`status=INACTIVE`); promotes another active account to default if the deleted one was |
| GET | `/v1/settings/bank-accounts/{id}/reveal` | `SETTINGS_MANAGE` | Full account number - logs a `BANK_ACCOUNT_REVEALED` security-audit event, same as Supplier's reveal endpoint |
| POST | `/v1/settings/bank-accounts/{id}/qr` | `SETTINGS_MANAGE` | Multipart (`file` + `label`) |
| DELETE | `/v1/settings/bank-accounts/qr/{qrId}` | `SETTINGS_MANAGE` | |
| GET | `/v1/settings/bank-accounts/qr/{qrId}/image` | authenticated (no specific permission, matches logo/signature/upi-qr GET) | |
| POST | `/v1/invoices/{id}/share/email` | `INVOICE_VIEW` | Real SMTP send with the actual PDF attached; returns `SENT`/`LOGGED_ONLY`/`FAILED`, never a 500 for "not configured" |
| GET | `/v1/products/{id}/image` | authenticated (no specific permission) | |
| PUT | `/v1/products/{id}/image` | `PRODUCT_MANAGE` | |
| DELETE | `/v1/products/{id}/image` | `PRODUCT_MANAGE` | |
| POST | `/v1/products/import/preview` | `PRODUCT_MANAGE` | Multipart file upload - parses and matches against category/brand names and existing product code/name, writes nothing |
| POST | `/v1/products/import/confirm` | `PRODUCT_MANAGE` | The only import endpoint that persists - one transaction, all-or-nothing (unlike Purchase Import, every row here is a brand-new product; a duplicate code/name is a preview-time error, never silently merged) |

`InvoiceRequest`/`InvoiceResponse` gained `bankAccountId`/`bankAccountQrId`
(request) and `bankAccountId`/`bankAccountLabel`/`bankAccountQrId`
(response) - all nullable/optional; a null `bankAccountId` falls back to
the tenant's pre-existing single default bank fields, exactly the
pre-CR-036 behaviour. No new permission codes anywhere in this round -
bank accounts reuse `SETTINGS_VIEW`/`SETTINGS_MANAGE`, product image/import
reuse `PRODUCT_VIEW`/`PRODUCT_MANAGE`, invoice email reuses `INVOICE_VIEW`
(viewing and sharing a document you can already see is not a more
sensitive action than viewing it).

## CR-036 phase 3: standalone expense ledger

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/v1/expense-categories` | `EXPENSE_VIEW` | User-extensible, no pagination |
| POST | `/v1/expense-categories` | `EXPENSE_MANAGE` | |
| PUT | `/v1/expense-categories/{id}` | `EXPENSE_MANAGE` | |
| POST | `/v1/expenses` | `EXPENSE_MANAGE` | |
| GET | `/v1/expenses` | `EXPENSE_VIEW` | Paged search - `search`, `status`, `categoryId`, `fromDate`, `toDate` |
| GET | `/v1/expenses/total` | `EXPENSE_VIEW` | Running total (ACTIVE only) for the same `fromDate`/`toDate` range - see BUG-EXP-001 for a real date-parameter bug found and fixed here |
| GET | `/v1/expenses/{id}` | `EXPENSE_VIEW` | |
| PUT | `/v1/expenses/{id}` | `EXPENSE_MANAGE` | |
| POST | `/v1/expenses/{id}/cancel` | `EXPENSE_MANAGE` | Soft-cancel (`status=CANCELLED`) - a recorded expense is a financial record, never hard-deleted |
| GET | `/v1/expenses/{id}/receipt` | authenticated (no specific permission) | |
| PUT | `/v1/expenses/{id}/receipt` | `EXPENSE_MANAGE` | Multipart |
| DELETE | `/v1/expenses/{id}/receipt` | `EXPENSE_MANAGE` | |

No new permission codes - `EXPENSE_VIEW`/`EXPENSE_MANAGE` already existed
in the catalogue since `V1`, seeded speculatively for this module before
it existed and already granted to OWNER/MANAGER/ACCOUNTANT by default.

## CR-036 phase 4: Labour Monitor

| Method | Path | Permission | Notes |
|---|---|---|---|
| POST | `/v1/workers` | `LABOUR_MANAGE` | |
| GET | `/v1/workers` | `LABOUR_VIEW` | Paged search - `search`, `status` |
| GET | `/v1/workers/active` | `LABOUR_VIEW` | Unpaginated - active workers only, for attendance-marking and payment pickers |
| GET | `/v1/workers/{id}` | `LABOUR_VIEW` | |
| PUT | `/v1/workers/{id}` | `LABOUR_MANAGE` | |
| POST | `/v1/workers/{id}/deactivate` | `LABOUR_MANAGE` | Soft-deactivate - attendance/payment history reference a worker forever |
| POST | `/v1/workers/{id}/activate` | `LABOUR_MANAGE` | |
| POST | `/v1/attendance` | `LABOUR_MANAGE` | Batch: marks one or more workers for a single `attendanceDate` in one call. Re-marking the same worker/date corrects the existing row in place (`UNIQUE (tenant_id, worker_id, attendance_date)`), never duplicates |
| GET | `/v1/attendance?date=` | `LABOUR_VIEW` | All marks for one day, for the marking UI to pre-fill corrections |
| GET | `/v1/attendance/worker/{workerId}?fromDate=&toDate=` | `LABOUR_VIEW` | One worker's attendance history |
| POST | `/v1/worker-payments` | `LABOUR_MANAGE` | |
| POST | `/v1/worker-payments/{id}/cancel` | `LABOUR_MANAGE` | Soft cancel (`status=CANCELLED`) - the row stays in history but stops counting towards the worker's paid total. Added by CR-037; see BUG-LAB-005 (a mistyped payment previously had no in-app correction at all) |
| GET | `/v1/workers/{workerId}/payments` | `LABOUR_VIEW` | Includes CANCELLED rows, flagged by `status` - history is never hidden |
| GET | `/v1/workers/{workerId}/wage-summary?fromDate=&toDate=` | `LABOUR_VIEW` | Earned (live-computed from attendance x current daily rate) vs paid vs balance owed, over an optional date range |

`GET /v1/projects/{id}` (existing endpoint, `PROJECT_VIEW`) gained a new
`totalLabourCostDisplay` field in its response - no new endpoint, since
it is additive read data on an existing resource.

**New permission codes**: `LABOUR_VIEW`, `LABOUR_MANAGE` - unlike every
prior phase of CR-036, these were NOT speculatively pre-seeded in `V1`
(confirmed via grep before building). Granted to OWNER, MANAGER and
ACCOUNTANT by default; deliberately withheld from STAFF (a worker's daily
rate is cost-like data, same reasoning that excludes `PRODUCT_VIEW_COST`
from STAFF). See BUG-LAB-001 for a real bug this surfaced: OWNER's
default grant needed an explicit row in this migration too, since OWNER's
blanket permission grant in `V1` is a one-time snapshot, not a standing
rule that automatically covers permissions added later.


## CR-038: sign-in security check (Cloudflare Turnstile)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/v1/auth/captcha-config` | public (permitAll) | Whether the sign-in page must render a challenge, plus the Turnstile **site** key. The secret never leaves the server. Public because the login page needs this before anyone has signed in. |
| POST | `/v1/auth/login` | public (permitAll) | Gained an optional `captchaToken`. Verified server-side against Cloudflare **before** authentication, so the endpoint cannot be used to probe passwords while failing the challenge. |
| POST | `/v1/settings/mail/test?toEmail=` | `SETTINGS_MANAGE` | Sends one test email and returns SENT / LOGGED_ONLY / FAILED with the mail server's own rejection text. Exists so outgoing email can be proven to work before Email OTP is built on it. |

`captchaToken` is optional in the DTO on purpose: whether it is required is a
runtime decision (`app.captcha.enabled` plus both keys present), not a
compile-time one. A `@NotBlank` there would reject every login on installs
that never configure CAPTCHA. `LoginRequest` also gained a two-argument
convenience constructor so existing callers keep compiling — the same pattern
`InvoiceRequest` already uses.

**Fail-safe contract**, verified against Cloudflare's published test keys:

| Situation | Behaviour |
|---|---|
| `enabled: false` (the default) | No token required, no third-party request made at all |
| `enabled: true` but either key blank | Treated as disabled — a missing key must never lock users out of a working system |
| Enabled, token missing | 400 `CAPTCHA_FAILED` |
| Enabled, token rejected by Cloudflare | 400 `CAPTCHA_FAILED` — correct credentials still do **not** sign in |
| Enabled, Cloudflare unreachable | 503 `CAPTCHA_UNAVAILABLE` — fails closed, but not reported as the user's mistake |

---

## CR-045 — Developer inspection

Not part of the ERP. Nothing here reads or writes shop data.

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/v1/dev/inspection/status` | authenticated | Reports both gates separately (`environmentAllows`, `permissionHeld`, `available`) plus the active profile **names**. Deliberately not permission-gated, so a developer can tell "wrong environment" from "permission not granted"; a bare 403 conflates them. |
| GET | `/v1/dev/inspection/runtime` | `DEVELOPER_INSPECT` | Build version, active profiles, Java/OS, CPU count, heap, uptime, server clock and zone. A **fixed list of named fields** — never a system-property or environment dump, because that is where `DB_PASSWORD`, `JWT_SECRET` and `APP_ENCRYPTION_KEY` would surface. |
| GET | `/v1/dev/inspection/request-echo` | `DEVELOPER_INSPECT` | The request as the server received it: method, path, request id, client IP, resolved user and tenant, and headers. `Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-API-Key` and `X-Auth-Token` are **removed, not masked** — a masked value still confirms presence and length. |

**Both gates apply to every row above**, including `status`:

1. The environment must permit inspection. `SecurityConfig` denies the whole
   `/v1/dev/**` and `/v1/debug/**` trees otherwise, so a future controller
   added under those paths is covered without its author knowing about CR-045.
2. `DEVELOPER_INSPECT` — held by no default role, OWNER included.

Where inspection is off, the diagnostics endpoints answer **404, not 403**. A
403 would confirm the route exists and is worth attacking.

### Actuator

| Path | Access |
|---|---|
| `/actuator/health` | public — the hosting platform's liveness probe |
| `/actuator/**` (everything else) | `DEVELOPER_INSPECT`, and only where the environment permits inspection; `denyAll` otherwise |

Exposure is also narrowed per profile: `prod` publishes `health` only; `test`
adds `info`, `metrics`, `loggers`; `dev` adds `env` and `mappings`; `local`
exposes everything. `env`, `configprops` and `beans` are never reachable in
production.

### Production surface

`springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` are **false**
under the `prod` profile. `/swagger-ui/**` and `/v3/api-docs/**` keep their
`permitAll` matcher, which in production matches nothing because no handler is
registered. A complete map of every endpoint, parameter and DTO is useful to an
attacker and to nobody running a hardware shop.

---

## CR-051 / CR-052 — Sales Order, Delivery Challan, Credit Note, idempotency

CR-051 (idempotency) adds no endpoint of its own — it is a service every
`POST`/convert endpoint below can opt into via an `Idempotency-Key` request
header. Header absent or blank: the endpoint behaves exactly as if CR-051
did not exist. Header present: `IdempotencyService` guarantees the wrapped
write runs exactly once for that key (see the Change Request Registry entry
for the full mechanism); a retried request with the same key and the same
body replays the first response, and the same key with a different body is
rejected `409 IDEMPOTENCY_KEY_REUSED`.

### Sales Order — `/v1/sales-orders`

| Method | Path | Permission | Notes |
|---|---|---|---|
| POST | `/v1/sales-orders` | `SALES_ORDER_MANAGE` | Accepts `Idempotency-Key`. |
| GET | `/v1/sales-orders` | `SALES_ORDER_VIEW` | `search`, `status`, `fromDate`, `toDate`, paged. |
| GET | `/v1/sales-orders/{id}` | `SALES_ORDER_VIEW` | |
| PUT | `/v1/sales-orders/{id}` | `SALES_ORDER_MANAGE` | Only while `DRAFT`/`CONFIRMED`. |
| PATCH | `/v1/sales-orders/{id}/status` | `SALES_ORDER_MANAGE` | `CONVERTED` cannot be set directly — use a convert action. |
| POST | `/v1/sales-orders/{id}/convert-to-invoice` | `INVOICE_CREATE` | Bills the order directly, skipping a challan. Accepts `Idempotency-Key`. |
| POST | `/v1/sales-orders/{id}/convert-to-delivery-challan` | `DELIVERY_CHALLAN_MANAGE` | Dispatches without billing yet. Accepts `Idempotency-Key`. |

No `/pdf` route yet — deferred, see CR-052's Change Request Registry entry.

### Delivery Challan — `/v1/delivery-challans`

| Method | Path | Permission | Notes |
|---|---|---|---|
| POST | `/v1/delivery-challans` | `DELIVERY_CHALLAN_MANAGE` | Accepts `Idempotency-Key`. Items are `productId` + `quantity` only — not a tax document, no discount/GST fields. |
| GET | `/v1/delivery-challans` | `DELIVERY_CHALLAN_VIEW` | `search`, `status`, `fromDate`, `toDate`, paged. |
| GET | `/v1/delivery-challans/{id}` | `DELIVERY_CHALLAN_VIEW` | |
| POST | `/v1/delivery-challans/{id}/cancel` | `DELIVERY_CHALLAN_MANAGE` | Only while `ISSUED`. Restores the stock the challan took. |
| POST | `/v1/delivery-challans/{id}/convert-to-invoice` | `INVOICE_CREATE` | Only while `ISSUED`. Accepts `Idempotency-Key`. |

No `/pdf` route yet — deferred.

### Credit Note — `/v1/credit-notes`

| Method | Path | Permission | Notes |
|---|---|---|---|
| POST | `/v1/credit-notes` | `CREDIT_NOTE_MANAGE` | Accepts `Idempotency-Key`. Body is `invoiceId` + items keyed by `invoiceItemId` (not `productId` — see CR-052) + `reason` (required) + optional `remarks`. No customer fields — the customer is read from the invoice. |
| GET | `/v1/credit-notes` | `CREDIT_NOTE_VIEW` | `search`, `status`, `fromDate`, `toDate`, paged. |
| GET | `/v1/credit-notes/{id}` | `CREDIT_NOTE_VIEW` | |
| POST | `/v1/credit-notes/{id}/cancel` | `CREDIT_NOTE_MANAGE` | Only while `ISSUED`. Reverses the stock the credit note restored. Never touches the original invoice. |

No `/pdf` route yet — deferred.

---

## CR-053 phase 1 — Invoice PDF themes

No new endpoint. `PUT /v1/settings` (`SETTINGS_MANAGE`, unchanged
permission) gained one new optional field, `invoiceTheme` — one of
`CLASSIC` (default) / `MINIMAL` / `BOLD` / `ELEGANT`. Null means leave
unchanged, same convention `subscriptionTier` already uses on this DTO.
`GET /v1/settings` echoes the current value back. `GET /v1/invoices/{id}/pdf`
is unchanged in shape — it now simply renders using whichever theme the
tenant has set.

---

## CR-054 phase 1 — Platform Admin Console: identity & auth

A completely separate base path, `/v1/platform-admin/**`, matched by its
own `@Order(0)` Spring Security filter chain (`PlatformAdminSecurityConfig`)
before the tenant chain ever sees the request. A tenant access token is
refused here and a platform-admin access token is refused on every
`/v1/auth/**` path — both directions live-verified in
`PlatformAdminAuthControllerIT`, not assumed from the code.

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/v1/platform-admin/auth/login` | public | body `{email, password}`. Never returns a session — returns a short-lived `mfaToken` + `enrollmentRequired`. Rate limited per IP (`PLATFORM_ADMIN_LOGIN_PER_IP`, 10/min). Identical response for unknown email, wrong password, and a locked/inactive account. |
| POST | `/v1/platform-admin/auth/mfa/verify` | `mfaToken` in body | 6-digit TOTP code or a 10-digit backup code. Exchanges the challenge for a real session. |
| POST | `/v1/platform-admin/auth/mfa/enroll` | `mfaToken` in body | Only for an account with `enrollmentRequired: true`. Generates a new TOTP secret (stored encrypted, `mfaEnabled` stays false) and returns `{otpAuthUri, qrCodePngBase64, secretBase32}`. Calling again before confirming replaces the pending secret. |
| POST | `/v1/platform-admin/auth/mfa/enroll/confirm` | `mfaToken` in body | Proves the code was captured. On success: `mfaEnabled` flips true, 10 backup codes are issued (shown once, in the response), and a real session is returned immediately. |
| POST | `/v1/platform-admin/auth/refresh` | refresh token in body | No HttpOnly-cookie transport in Phase 1 — the raw refresh token travels in the JSON body both ways. Rotation + reuse detection mirrors `/v1/auth/refresh` exactly. |
| POST | `/v1/platform-admin/auth/logout` | authenticated | This device/token only. |
| POST | `/v1/platform-admin/auth/logout-all` | authenticated | All sessions, bumps `token_version`. |
| GET | `/v1/platform-admin/auth/me` | authenticated | Identity + role + effective permissions from the security context. |
| POST | `/v1/platform-admin/admins` | `PLATFORM_ADMIN_MANAGE` (SUPER_ADMIN only) | Creates another platform admin. Starts with `mfaEnabled: false` — enrolls on its own first login, same as every account. |
| GET | `/v1/platform-admin/admins` | `PLATFORM_ADMIN_MANAGE` (SUPER_ADMIN only) | Lists all platform admin accounts. |

No tenant-facing endpoint changed. No endpoint here accepts or reads a
`tenant_id` in any form.

## CR-056 — Tenant-owned WhatsApp Business API integration

Every endpoint below resolves the tenant from
`SecurityUtils.requireCurrentTenantId()` (JWT-derived) - none accepts a
`tenantId`/`phoneNumberId`/`connectionId` in the request. See
`WhatsAppConnectionSecurityIT` for the cross-tenant proof.

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/v1/settings/whatsapp` | `SETTINGS_VIEW` | Connection status for the caller's own tenant only. Never returns the access token. |
| POST | `/v1/settings/whatsapp/connect` | `SETTINGS_MANAGE` | Body: `businessAccountId`, `phoneNumberId`, `accessToken` (the tenant's own, obtained from their own Meta Business Manager - phase 1 manual entry, not OAuth). Verifies live against Meta's Graph API before saving; 409 if `phoneNumberId` already belongs to another tenant. |
| POST | `/v1/settings/whatsapp/disconnect` | `SETTINGS_MANAGE` | Keeps the row (business name/phone/history), revokes the stored token, marks `DISCONNECTED`. |
| POST | `/v1/settings/whatsapp/test-send` | `SETTINGS_MANAGE` | Body: `toMobileNo`. Throws immediately (never a fake success) if not connected. |
| POST | `/v1/invoices/{id}/share/whatsapp` | `INVOICE_VIEW` | Manual resend of the invoice-created message, distinct from the automatic on-create send. |
| POST | `/v1/invoices/{id}/payments/{paymentId}/share/whatsapp` | `PAYMENT_MANAGE` | Manual only - no auto-send toggle exists, so "do not automatically send" is satisfied by this never firing on its own. |
| POST | `/v1/invoices/{id}/remind` | `INVOICE_VIEW` | Pre-existing (Task 05 / superseded CR-055) - now sends through the tenant's own connection instead of a shared one. |
| POST | `/v1/inventory/low-stock/send-alert` | `INVENTORY_VIEW` | Manual trigger for the same digest `ReminderSchedulerService` already sends daily at 8am - to the shop's own contact number, never a customer. |
| GET | `/v1/notifications/log` | `SETTINGS_VIEW` | Pre-existing, gained an optional `channel` query param for the Message History page. |
| GET / POST | `/v1/webhooks/whatsapp` | public, self-verified | Meta's one callback URL for this whole app. GET is the one-time `hub.verify_token` handshake; POST is inbound delivery-status events, HMAC-verified (`X-Hub-Signature-256` against `WHATSAPP_APP_SECRET`), routed to a tenant by `phone_number_id`, idempotent (only advances a `notification_log` row's status forward). See `WhatsAppWebhookController`'s own javadoc for the real limitation this carries for a tenant connected through a different Meta app than this one's. |

Customer create/update (`POST`/`PUT /v1/customers`) gained an optional
`whatsappOptIn` field, defaulting to `true` when omitted.

## CR-057 phase 9 — Subscriptions & Billing

Tenant-side endpoints resolve the tenant from `SecurityUtils.requireCurrentTenantId()` only - no request accepts a `tenantId`.

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/v1/billing/checkout` | `SETTINGS_MANAGE` | Body: `requestedTier`. Creates a real Razorpay order for the caller's own tenant; `503 BILLING_NOT_CONFIGURED` if no gateway credentials are set. Rejects `FREE` (nothing to buy). |
| POST | `/v1/billing/verify` | `SETTINGS_MANAGE` | Body: `razorpayOrderId`/`razorpayPaymentId`/`razorpaySignature` - Razorpay Checkout's own callback shape. Applies the tier upgrade only on a genuine HMAC match against `key_secret`; `400 PAYMENT_SIGNATURE_INVALID` otherwise. |
| GET | `/v1/billing/history` | `SETTINGS_VIEW` | Current tier + this tenant's own payment history only. |
| POST | `/v1/webhooks/razorpay` | public, self-verified | Inbound Razorpay webhook - authenticity is the `X-Razorpay-Signature` HMAC against the webhook secret inside `SubscriptionBillingService`, not Spring Security (same pattern as `/v1/webhooks/whatsapp`). Idempotent via `UNIQUE(razorpay_payment_id)`. |
| GET | `/v1/platform-admin/billing/overview` | `BILLING_VIEW` | Cross-tenant revenue chart data - last 12 months, aggregated server-side, never raw payment rows. |
| GET | `/v1/platform-admin/billing/tenants/{tenantId}` | `BILLING_VIEW` | One tenant's current plan + payment history, for the Tenant Detail page. |

`PUT /v1/settings` — `subscriptionTier` in the request body now rejects a
self-declared *upgrade* with `422 UPGRADE_REQUIRES_CHECKOUT` once Razorpay
billing is configured (a downgrade, including to `FREE`, still applies
freely in both cases). Unchanged with no gateway configured. See
`CHANGE_REQUEST_REGISTRY.md`'s CR-057 phase 9 entry.

## CR-057 phase 10 — Tenant Analytics

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/v1/platform-admin/analytics/overview` | `ANALYTICS_VIEW` | 12 months of growth (new tenants/users, real distinct-login active-user count) and churn, plus a module-adoption snapshot - all aggregated server-side. |
| GET | `/v1/platform-admin/analytics/export?format=csv\|xlsx\|pdf` | `ANALYTICS_EXPORT` | Same data as `overview()`, rendered as a file - never disagrees with the on-screen charts. |

## CR-057 phase 11 — Backup Center

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/v1/platform-admin/tenants/{id}/backups` | `BACKUP_VIEW` | Export history for one tenant - who exported what, when, success/failure. |
| POST | `/v1/platform-admin/tenants/{id}/backups?format=JSON\|CSV` | `BACKUP_MANAGE` | Generates and downloads a fresh export of the tenant's core data; logs the attempt either way. Never stores the file itself. |

## CR-057 phase 12 — Platform Settings (Razorpay)

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/v1/platform-admin/settings/razorpay` | `BILLING_VIEW` | Current config - `keySecretConfigured`/`webhookSecretConfigured` booleans only, never the secret itself. `source` names whether the database row or the `RAZORPAY_*` env vars are actually in force. |
| PUT | `/v1/platform-admin/settings/razorpay` | `BILLING_MANAGE` | Body: `enabled`, `keyId`, `keySecret`/`webhookSecret` (omitted = leave unchanged, `""` = clear), `proPlanAmountPaise`, `maxPlanAmountPaise`. Audited (`PLATFORM_SETTING_UPDATED`). |
