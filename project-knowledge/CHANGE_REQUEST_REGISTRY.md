# CHANGE REQUEST REGISTRY

Every business or architectural change is recorded here before code changes.
Nothing is implemented from conversation memory.

| ID | Date | Source | Summary | Status |
|---|---|---|---|---|
| CR-001 | 2026-08-13 | User | Drop SaaS/multi-tenant design. Single shop monolith. | APPROVED — applied |
| CR-002 | 2026-08-13 | User | Lock naming registry; no renames without approval. | APPROVED — applied |
| CR-003 | 2026-08-13 | User | Full Module 1 security audit and correction. | APPROVED — **IN PROGRESS** |
| CR-004 | 2026-08-13 | User | Hardware ERP business rules (product master, price history, loss-sale, GST on invoice rate, import). | APPROVED — recorded, applies from Module 6 |
| CR-005 | 2026-08-13 | User | Mandatory per-module package: seed data, Postman, Swagger examples, setup PDF, architecture image, frontend. | APPROVED — see DELIVERY_PLAN |
| CR-006 | 2026-08-13 | User | Maintain 8 registry files under /project-knowledge. | APPROVED — applied (this directory) |
| CR-007 | 2026-08-13 | Claude | Module order corrected: Purchase and Inventory before Invoice. | **APPROVED 2026-08-13** |
| CR-008 | 2026-08-13 | Claude | No public self-registration. Owner-created users only. | **APPROVED 2026-08-13** |
| CR-009 | 2026-08-13 | Claude | Seed data split: db/migration = schema, db/seed = dev/test only. | **APPROVED 2026-08-13** |
| CR-010 | 2026-08-13 | Claude | Naming cleanup approved; registry re-baselined. | **APPROVED 2026-08-13** |
| CR-011 | 2026-08-13 | Claude | Category and Brand reinstated before Product (FK dependency). | **APPLIED** |
| CR-012 | 2026-08-13 | Claude | Repository structure: one backend, one frontend, module packages. | **APPROVED 2026-08-13** |
| CR-021 | 2026-08-22 | User | Module 4 (Inventory) + Module 7 (Invoice/Payment) built ahead of full Module 5 (Customer), backed by a minimal auto-created Customer record. | **APPROVED 2026-08-22** |
| CR-022 | 2026-08-22 | User | GST-compliant Invoice PDF, shop GST/address settings, Customer GST capture, Quotation module (Module 10, built ahead of Purchase/Inventory pricing dependency, price-quote only). | **APPROVED 2026-08-22** |
| CR-023 | 2026-08-22 | User | Full Customer module (Module 5), file storage (photo/logo/signature as Postgres bytea), state/bank master data, dynamic shop name+logo, sidebar restructure, dashboard expansion, Supplier terminology labels. Purchase sub-modules and Customer Returns/Damage explicitly out of scope - no backing data exists. | **APPROVED 2026-08-22** |
| CR-024 | 2026-08-22 | User | Inline "Add category" / "Add brand" from within the Product create/edit dialog, no navigation away. Reuses the existing CategoryForm/BrandForm unchanged - no new backend endpoints or schema. | **APPROVED 2026-08-22** |
| CR-025 | 2026-08-22 | User | Invoice PDF: box the shop's own header section to match Billed To/Shipment, wire the already-built shop logo into the PDF for the first time. | **APPLIED** |
| CR-026 | 2026-08-23 | User | Shop Settings view/edit toggle; live Sidebar/header refresh after Settings/Profile saves; Invoice + Quotation PDF Preview (view without downloading); new Quotation PDF; UPI QR code image upload as an alternative to the auto-generated QR; invoice PDF visual polish (light section tint, thank-you line, clearer computer-generated wording); numeric input leading-zero/empty-clears-to-0 fix, app-wide; live-uppercase on code-like fields (product/category/brand code). | **APPROVED 2026-08-23** |
| CR-027 | 2026-08-23 | User | Module 12 Payment (standalone list/history, was Invoice-embedded only); notification system (real email via existing SMTP, SMS/WhatsApp built as a real pluggable provider interface with a logging stub until real provider credentials are supplied); subscription tiers (FREE/PRO/MAX) as per-tenant feature gating; AI chat assistant (read-only, permission-aware, tool-calling over existing tenant-scoped services, not free-form SQL generation) - needs a real LLM API key to go live. | **APPROVED 2026-08-23** |
| CR-028 | 2026-08-23 | User | Bulk load-test data (1000 suppliers/1000 customers/10000 products); full RBAC + cross-tenant security audit (live-tested with a second real tenant); security_audit_log tenant-isolation fix (BUG-SEC-001); "Contact admin" support request; tenant self-registration with subscription-plan selection (fulfills the second-tenant provisioning CR-016 explicitly deferred); coupon codes for retail discounts, tenant-scoped and optionally restricted to specific products; cross-device responsive verification. | **APPROVED 2026-08-23** |
| CR-029 | 2026-08-23 | User | ERP expansion Phase 0 (audit) + Phase 1 (CR-018 supplier bank encryption) + Phase 6 (Project Management - work types, projects, materials, expenses, payments, server-side profitability, rooftop sheet calculator); Gemini added as a second, free-tier AI provider alongside Anthropic; security_audit_log date-filter fix (BUG-SEC-002); customer financial-summary fix (BUG-CUST-001, same round). Phases 7-16 (Labour, Finance/ledger, Reports, Notifications v2, AI v2, SAML) explicitly deferred and documented, not attempted superficially. | **APPROVED 2026-08-23** |
| CR-030 | 2026-08-23 | User | Customer 360 Phase 1 of the Customer 360/Document Reuse master prompt: Customer Detail page gained an Invoices/Quotations/Products-purchased tab set (`GET /v1/customers/{id}/quotations`, `GET /v1/customers/{id}/products` new endpoints; product history is a native aggregate query with a correlated "last price paid" subquery); "New quotation"/"New invoice" quick actions on Customer Detail pre-fill the wizard's Customer step via router state (name/mobile/email) without locking the fields, so a returning customer's record is reused rather than re-typed. §17 credit-limit warning also added: `GET /v1/customers/credit-check?mobile=...` looks an existing customer up by exact mobile as the Invoice wizard's free-text Customer step is filled in, and the Review step shows an advisory (non-blocking) warning if the invoice would push the customer's outstanding balance past their credit limit. Live-verified via Playwright as OWNER: tabs render, quick actions pre-fill correctly, and the credit-limit warning renders with the correct rupee figures on a real customer while still allowing the invoice to save. §45-46 "Repeat" also added: a Repeat button on Invoice/Quotation Detail navigates to a new document pre-filled with the same customer and product quantities via the same router-state pattern, re-fetching each product's *current* selling price rather than carrying the old one forward, and skipping (with a visible note) any product that's since gone inactive or been deleted. Frontend-only — no backend change needed, purely a client-side reuse of existing GET endpoints. Live-verified for both Invoice and Quotation. Duplicate-customer detection (§43) was found to already exist from an earlier round (`CustomerServiceImpl.create/update` reject a duplicate mobile number server-side, surfaced as a field-level form error) — verified, not rebuilt. §15 inline "Add Product" also added: the shared `ProductPicker` (used by both Invoice and Quotation wizards) now offers "Add '{query}' as a new product" when a search comes up empty, opening the same full `ProductForm` used by the Product module itself (categories/brands lazily fetched only when the dialog opens) inside a dialog on top of the wizard - on save, the new product is added as a line item immediately, no navigation away. Permission-gated by `PRODUCT_MANAGE`; a user without it just sees "No matching products," as before. Frontend-only, no backend change. Live-verified: searched a brand-new product name, created it inline, confirmed it appeared as a correctly-priced invoice line item without leaving the wizard. §12-13 dashboard quick actions: the Dashboard already had a "New invoice" button; added the missing "New quotation" one alongside it. Neither forces a customer picker first — both wizards already accept free-text name/mobile with server-side auto-match-by-mobile (CR-021's original design), which already satisfies "no repeated data entry, select existing or create new" without a separate picker step; adding one would have been a regression to a proven flow, not an improvement, so this stayed a one-line addition rather than new UI. §18-23 archive/soft-delete safety was audited: Customer/Supplier/Product all already never hard-delete (hard rule #7, `softDelete`/`deactivate` everywhere), list pages already have an Active/Inactive filter, and Supplier/Product already let a deactivated record be reactivated by editing status back to ACTIVE — Customer was the one gap (its `CustomerRequest` had no `status` field, `PUT` couldn't touch it, deactivation was one-way through the UI). Fixed: added `status` (required, `@NotNull`) to `CustomerRequest`, wired into `CustomerServiceImpl.applyRequest()`, and a Status select added to `CustomerForm` in edit mode only. `CustomerServiceImplTest` +1 test. Live-verified: deactivated a customer from the list row menu, confirmed Inactive on its detail page, reactivated via Edit → Status → Active, confirmed Active again. §24-26 Security Audit Log: added a click-through detail dialog (`SecurityAuditLogPage`) - the API response already carried `ipAddress`/`userAgent`/`requestId`/`entityType`/`entityId`, none of which were shown anywhere in the UI (IP was only visible above the `xl` breakpoint; user agent and request id weren't rendered at all). Frontend-only, no backend change. Live-verified: clicked a real row, confirmed all fields render including a real captured user-agent string. Explicitly NOT done in this pass: showing the actor's *role* at the time of the event - `SecurityAuditLog` has no `role_code`/`role_name` column, `fullName` is a denormalized snapshot resolved by each of 5 separate call sites (AuthServiceImpl, UserServiceImpl, RoleServiceImpl, SupplierServiceImpl, BootstrapOwnerInitializer), not a single choke point - adding role would need a migration plus changes at all 5 sites, a larger, riskier change than the rest of this round; flagged as the next scoped step, not silently skipped. Still explicitly deferred: SaaS subscription entitlement limits. | **APPROVED 2026-08-23** |
| CR-031 | 2026-08-23 | User | SaaS subscription entitlement limits (Customer 360 §27-40) - the last major item from that master prompt. `SubscriptionTier` (already existed as a feature-gating flag, CR-027) extended with per-tier numeric limits: FREE 1 owner/100 customers/100 suppliers/1000 products, PRO 2/1000/1000/10000, MAX unlimited (`UNLIMITED = -1`; no billing gateway or platform-admin config UI exists to make a real per-tenant override meaningful, so MAX is simply unlimited rather than inventing config storage nothing else uses). New `EntitlementService` (mirrors the existing `SubscriptionService`'s "one sanctioned gate" pattern) - `requireCanAddOwner/Customer/Supplier/Product()` each reject with 402 `ENTITLEMENT_LIMIT_REACHED` when the tenant's *active* count is already at its tier's limit; wired into `UserServiceImpl.create()` (owner role only), `CustomerServiceImpl.create()`, `SupplierServiceImpl.create()`, `ProductServiceImpl.create()` - checked before anything is built, so a rejected create never partially writes. Deliberately does NOT touch `BootstrapOwnerInitializer`/`TenantRegistrationServiceImpl`'s own owner creation (both build the User row directly, bypassing `UserServiceImpl.create()`) - a brand-new tenant's very first owner must never be blocked. New `GET /v1/settings/usage` (`SETTINGS_VIEW`) returns a `UsageSummaryResponse`; Shop Settings gained a "Plan usage" card with a progress bar per resource, red at/over the limit, amber near it, "Unlimited" text (never a bar) for MAX. Backend: 7 new `EntitlementServiceImplTest` tests plus entitlement-check tests added to `CustomerServiceImplTest`/`UserServiceImplTest`; full suite 193/195 (same 2 pre-existing BUG-ENV-002 Docker failures). Frontend `tsc -b --force`/`vite build` clean. Live-verified end to end: confirmed MAX tier shows "Unlimited" everywhere; temporarily switched the primary test tenant (already at 1,000+ customers/suppliers/products from earlier load-testing) to FREE, confirmed the usage card immediately showed all four rows red and over-limit, confirmed customer/supplier/product creation were each rejected with the correct plan-specific message, then switched back to MAX and confirmed creation works normally again - the tenant was left in its original working state. | **APPROVED 2026-08-23** |
| CR-035 | 2026-08-24 | User | Purchase module + Supplier Bill Import (customer-requested file upload). Purchase built first as the genuine prerequisite (statuses DRAFT/RECEIVED/PARTIALLY_PAID/PAID/CANCELLED, stock received via new PURCHASE_RECEIPT/PURCHASE_RETURN movement types through the existing StockService choke point, never a direct mutation). Supplier Bill Import on top: upload -> preview (never writes) -> edit/match -> confirm (one transaction, all-or-nothing) -> result. Real CSV and Excel (.xlsx) extraction via a pluggable `DocumentExtractionService`; PDF/image explicitly return "unavailable, needs a configured OCR/AI provider" rather than faking extraction. Existing-vs-new detection for product (code/name)/brand/category(name)/supplier(GST/mobile/code/name), possible-duplicate-bill warning with an explicit "continue anyway" override, full file-security validation (extension allowlist, 20MB cap, real ZIP-signature check for .xlsx). Live-tested with real 100-row and 1000-row CSV and Excel files end to end (preview, confirm, resulting Purchase/Product/Stock all verified) - two real bugs found and fixed during that testing, not just claimed working: (1) two rows in one bill naming the identical *new* product crashed the whole import, now correctly reuses the row's own earlier-created product; (2) the result summary's "existing matched" counter conflated genuine pre-existing catalogue matches with rows reusing a product just created earlier in the same batch, now reported as two honest separate numbers. Full detail in RESUME_POINT.md. | **APPROVED 2026-08-24 — applied, live-verified** |
| CR-034 | 2026-08-24 | User | Advanced Customizable Theme System - a real design-system-level engine: 7 selectable design styles (Minimal/Bento/Glass/Liquid Glass/Spatial/Neomorphic/Claymorphic), each a genuine token-driven surface recipe (not a colour swap) applied through the shared Card/Button/Input/Dialog/DropdownMenu primitives so every page adapts; expanded to 11 colour themes (added Teal/Violet/Rose, renamed Minimal->Monochrome); Intensity/Corner/Elevation/Motion dials; a dedicated Theme & Appearance page (also embedded as a Profile tab); appearance prefs now scoped per signed-in user id so one tenant never inherits another's look on a shared browser. See RESUME_POINT.md for full detail and what's deliberately not built (per-component bespoke styling for ~30 page categories, in favour of the shared-primitive approach). | **APPROVED 2026-08-24 — round 1 applied** |
| CR-033 | 2026-08-24 | User | Complete Modern UI/UX Redesign - a 40-section master spec (design tokens, 8 named color themes, genuine dark mode, sidebar/topbar redesign, Cmd+K command palette, dashboard analytics, per-page redesigns, table/modal/button design system, micro-interactions, auth/profile redesign, empty/loading/error states, responsiveness, accessibility, 18-phase order). Round 1 (this entry): 8-theme color-preset system + Appearance UI, command palette extended to Invoices/Quotations/pages, real dashboard trend data. Remaining phases explicitly deferred, see RESUME_POINT.md. | **APPROVED 2026-08-24 — IN PROGRESS, round 1 applied** |
| CR-036 | 2026-08-25 | User | A larger sequenced request, built and live-verified one phase at a time: (1) multi-bank-account + multi-QR invoice payments plus real Email/WhatsApp invoice sharing, (2) Product page bulk CSV/Excel import + per-product photo upload, (3) a standalone shop-wide expense ledger (categories user-extensible, optional receipt photo, running total, deliberately separate from the existing per-project expense ledger), (4) Labour Monitor - worker directory, simple daily present/absent/half-day attendance (batch-marked, corrections update in place not duplicate), live-computed wage/payroll (never stored, always from the worker's current rate), worker payments with an earned-vs-paid balance, and an additive `totalLabourCostDisplay` on the existing Project detail response, deliberately never folded into the existing profit math. Phases 1-4 all built, unit-tested, and live-curl-verified end to end (including RBAC and cross-tenant isolation on every new endpoint) - four real bugs found and fixed along the way, including one in the already-shipped Product module (BUG-FE-007) and one in the OWNER permission grant (BUG-LAB-001) - see BUG_REGISTRY.md for full writeups. Live browser (Playwright) click-through was not performed for phase 4 - no browser automation tool was available in this session; typecheck, production build, unit tests and live API testing all passed. Phase 5 (sample data increase + a free-hosting `.md` guide) not started this round. | **APPROVED 2026-08-25 — phases 1-4 applied, live-verified via API; phase 5 not started** |
| CR-032 | 2026-08-23 | User | Subscription trial coupons - "give a complete free coupon" for a shop's own plan. Distinct from the retail `coupon` table (CR-028, discounts a customer's invoice): new `subscription_coupon` table (tenant-scoped, same pattern), redeemed by the OWNER in Shop Settings to grant their own tenant a tier (FREE/PRO/MAX) free for `trialDays` from the moment of redemption, then automatically reverts to FREE - checked lazily the next time `SubscriptionServiceImpl.currentTier()` runs (every entitlement/feature-gate check), not by a scheduled job. New `tenant.subscription_trial_expires_at` column (null = permanent, exactly CR-027's original picker behaviour); picking a tier manually from the existing dropdown always clears it, so an owner's explicit choice can never be silently overridden by a stale trial later. `currentTier()` changed to `REQUIRES_NEW` so the revert-write always flushes even when called from inside a readOnly caller (`EntitlementServiceImpl`'s checks, `TenantSettingsServiceImpl.get()`) - caught and fixed during design, before it could ship as a latent bug. New `SubscriptionCouponService` (create/update/delete/search/redeem, `SETTINGS_MANAGE`-gated throughout, no new permission codes needed) and `POST /v1/subscription-coupons/redeem`. Frontend: a "Subscription coupons" card in Shop Settings (create codes, redeem a code, see usage), plus a "Trial" badge and reversion date on the Subscription plan card itself. Backend: 11 new tests (`SubscriptionCouponServiceImplTest` ×7, `SubscriptionServiceImplTest` ×4 covering the lazy revert specifically); full suite 204/206 (same 2 pre-existing BUG-ENV-002 Docker failures). Frontend `tsc -b --force`/`vite build` clean. **Two real bugs found and fixed during live testing, not just claimed working**: (1) the redeem-success message called `window.location.reload()` immediately after setting it, wiping it before it could ever be seen - fixed to patch parent state directly instead of any kind of reload; (2) even after removing the hard reload, the parent's own `reload()` helper flips a page-wide `loading` flag that unmounts the whole Shop Settings page mid-fetch, which would have wiped the same message a second, subtler way - fixed by passing the redemption result up and patching `settings` state in place, never re-triggering the loading spinner for this. Live-verified end to end: created a coupon, redeemed it, confirmed the "Trial" badge and exact reversion date rendered and *stayed visible*, confirmed Plan usage recalculated correctly under the granted tier, manually picked a different tier and confirmed the trial badge cleared and entitlement limits recalculated for the new tier, then restored the tenant to MAX and confirmed via a direct DB query that both `subscription_tier` and `subscription_trial_expires_at` landed exactly where expected. | **APPROVED 2026-08-23** |
| CR-037 | 2026-08-25 | User | **Process change, not a code change.** Adopt a proactive, role-aware working method as standing policy: for every requirement, think through the full real-world workflow across all affected roles (Owner, Salesperson, Purchase Staff, Inventory Manager, Warehouse Staff, Accountant, Auditor, Customer, Supplier, Labour) before writing code; treat one reported symptom as a signal to inspect the whole surrounding module for the same class of defect; always report adjacent gaps found, and always fix same-root-cause defects in the same commit. Deliberately bounded so it does not contradict CLAUDE.md's existing "do not overengineer" rule: large new subsystems noticed as gaps (OCR/document extraction, synonym/fuzzy search layer, e-signatures, optimistic-locking concurrency, document lifecycle state engine) are **reported and proposed as their own CR, never built unprompted**. Applied to `CLAUDE.md` as a new "Proactive scope" section plus an added pre-fix inspection step in BUG HANDLING. Also documents which concerns are already solved project-wide (permission gating, tenant isolation, audit trail, soft delete, inline entity creation, import preview→confirm safety) so future work extends the existing mechanism instead of rebuilding it. | **APPROVED 2026-08-25 — applied to CLAUDE.md; no code change** |

| CR-040 | 2026-08-26 | User | Production-grade registration consent. Frontend: a Review & Agreement section with document cards (title, version, last-updated, View), the final agreement checkbox **disabled until both required documents have been opened**, reviewed-state shown by icon+word not colour alone, and optional marketing consent in its own visually quieter block - never pre-selected, never bundled into the required agreement. Documents open in a large dialog (min(900px,92vw) x min(80vh,760px); full-screen on phones) with pinned header/footer and only the body scrolling. Backend: **new** `user_consent` table (V28, append-only, newest row per user+type is current, supports re-consent and marketing withdrawal; deliberately stores NO IP/device data), `termsAccepted` enforced by @AssertTrue so a direct API call cannot bypass the UI, and submitted document versions validated against `LegalDocumentVersions` so a client cannot record consent to a version that was never published. Scoping decisions taken on evidence, not assumption: **no Billing Terms document** (no payment is collected anywhere) and **no cookie-consent checkbox** (the app sets exactly one HttpOnly auth cookie and has zero analytics/tracking/third-party scripts - manufacturing a consent banner would be theatre). Legal copy carries inline LEGAL REVIEW REQUIRED badges on the genuinely jurisdiction-dependent sections and claims compliance with no named statute. | **APPROVED 2026-08-26 — applied, live-verified (backend bypass attempts rejected, consent rows and CHECK constraint verified in the database); legal text requires a lawyer before production** |
| CR-039 | 2026-08-26 | User | (1) **Amend a document instead of rebuilding it.** Quotations are editable while DRAFT or SENT (not ACCEPTED - those figures are agreed - nor CONVERTED, REJECTED, EXPIRED). Invoices are editable only while UNPAID with zero payment rows: number and date are preserved, and stock is applied as a per-product **delta** rather than a full reverse-and-reapply, so unchanged lines write no movement and cannot spuriously trip the stock guard. Refused with INVOICE_ALREADY_PAID once any payment exists - a GST tax invoice the customer has settled against is amended by credit/debit note, not silently. Both wizards reused via router state so create and edit cannot drift. On edit the Payment step explains itself rather than offering fields (PUT takes no initial payment) and Coupon is hidden (usage is counted at creation). (2) **Terms & Conditions gate on shop registration** - unticked checkbox, Terms and Privacy in dialogs so a half-filled form survives reading them, zod literal(true) as the real gate with the disabled button as its visible half. **Frontend only: the registration API has no field for consent and was NOT modified.** See the note below on persisting acceptance for audit. | **APPROVED 2026-08-26 — applied and live-verified (30/30 scenario audit); consent persistence deliberately not implemented, pending your decision** |
| CR-038 | 2026-08-26 | User | Login hardening, first slice of a larger export/import + security request. (1) **Cloudflare Turnstile** on sign-in: server-side token verification before authentication, a public /v1/auth/captcha-config so the login page knows whether to render the widget, and a Turnstile component that loads its script on demand so an install without CAPTCHA makes no third-party request at all. Off by default, and treated as off whenever either key is blank - a missing key must never lock users out of a working system. Fail-safe contract verified end to end against Cloudflare's published always-pass and always-fail test keys, including that correct credentials with a rejected token still do NOT sign in. (2) **BUG-ENV-003**: /actuator/health returned 503 on a fully healthy app because Boot folds the SMTP indicator into the aggregate status - which would have restart-looped the documented Render deploy. Mail indicator disabled; POST /v1/settings/mail/test added so mail failures stay discoverable, returning the mail server's own rejection text. **Email OTP deliberately NOT built this round**: the SMTP credentials are currently rejected by Gmail, and shipping OTP on unproven email locks every user out of their own account. It is next, once a test email is confirmed to arrive. Export/import for all entities also still outstanding. | **APPROVED 2026-08-26 — CAPTCHA + health fix applied and live-verified; Email OTP and export/import not started** |
| CR-041 | 2026-08-26 | User | **Per-tenant document number allocator, replacing MAX+1 in ten call sites.** Every generated code — `INV-`, `QUO-`, `PUR-`, `CUS-`, `SUP-`, `PRD-`, `CAT-`, `BRD-`, `PRJ-` — was allocated as `findHighestGeneratedCodeNumber(tenantId) + 1`: a read, then a write, with no lock. Two concurrent counter staff read the same MAX and both attempted the same number; the `UNIQUE (tenant_id, <code>)` constraint on every one of those tables caught it, so **no duplicate was ever stored**, but the losing request died on a constraint violation and its document was lost. New `document_sequence` table (V29) holds one row per tenant per document type, allocated under `SELECT … FOR UPDATE`. Allocation joins the caller's transaction (`Propagation.MANDATORY`) rather than `REQUIRES_NEW`, so a rolled-back invoice does not burn a number — GST requires an unbroken consecutive serial. Lock ordering is sequence-first everywhere, which keeps it deadlock-free. Prerequisite for CR-043 (offline sync), where replaying a queued batch would have hit this constantly. | **APPROVED 2026-08-26 — applied, 6/6 regression tests green against real PostgreSQL** |
| CR-045 | 2026-08-26 | User | **Version 1 Git and environment foundation.** Repository placed under version control (it had no commits). Branch strategy main / develop / feature|bugfix|hotfix, with runtime environments expressed as Spring profiles rather than branches. Two new profiles: `local` (one developer's machine) and `test` (the QA deployment). Production hardened: springdoc api-docs and swagger-ui disabled, actuator limited to health, whitelabel error page off. **Developer inspection** added at `/v1/dev/inspection/*` behind two independent server-side gates - the environment (`app.developer-inspection.enabled`, hard false in prod plus a code-level prod override) AND the new `DEVELOPER_INSPECT` permission, which **no default role holds, OWNER included** - so "admin" never means "developer". Deliberately NOT implemented: any attempt to block F12/right-click/DevTools, which is theatre rather than security. Production source maps off and asserted by CI. GitHub Actions CI added (backend verify, frontend typecheck+build+source-map assertion, secret scan). Two pre-existing test failures found and fixed along the way: BUG-AUTH-014 and BUG-SEC-003. | **APPROVED 2026-08-26 — applied, full suite green** |
---

## CR-003 — Module 1 audit corrections (APPROVED, re-baselined under CR-010)

Renames applied and re-baselined 2026-08-13. These names are now LOCKED:

| Locked name | New name | Reason |
|---|---|---|
| `LoginRequest.login` | `LoginRequest.identifier` | Explicitly requested |
| `ForgotPasswordRequest.login` | `.identifier` | Consistency |
| `audit_log` / `AuditLog` | `security_audit_log` / `SecurityAuditLog` | Security events separated from business history |
| `audit_log.user_name` | `security_audit_log.full_name` | Resolves defect D3 (two names for one concept) |
| `role_permission.permission_code` | `role_permission.permission_id` | Permission is now a first-class table |
| `role.active BOOLEAN` | `role.status VARCHAR(20)` | Resolves defect D1 |
| `role.is_system` | `role.system_role` | Boolean prefix rule |
| `refresh_token.replaced_by_hash` | `refresh_token.replaced_by_token_id` | FK, walkable rotation chain |
| `AuditService` | `SecurityAuditService` | Scope now explicit |
| `DataInitializer` | `BootstrapOwnerInitializer` | Scope now explicit |
| `ErrorResponse` in `common.exception` | `common.dto` | Envelope, not an exception |

**These are safe now and only now.** Nothing is deployed; no data exists.
After Module 2 ships, each becomes a Flyway `ALTER TABLE ... RENAME COLUMN`
plus a coordinated frontend release.

---

## CR-007 — Module order contradiction (OPEN)

Three different orders have been given:

| | Session 1 | Session 2 (top of doc) | Session 2 (bottom of doc) |
|---|---|---|---|
| 4 | Products | Category | Category |
| 5 | Purchases | Brand | Brand |
| 6 | Sales | Product | Product |
| 7 | Inventory | **Quotation** | **Product Variant** |
| 8 | Payments | — | Quotation |
| 9 | Expenses | — | Invoice |
| 10 | Reports | — | Purchase |
| 11 | — | — | Inventory |

**Recommended order — and the reason it matters:**

```
1  Authentication          6  Product
2  Customer                7  Product Variant
3  Supplier                8  Purchase        <-- moved BEFORE invoice
4  Category                9  Inventory       <-- moved BEFORE invoice
5  Brand                  10  Quotation
                          11  Invoice
```

Your own CR-004 rules make this a hard dependency, not a preference:

- Loss-sale protection needs `purchase_price`. Purchase price is produced by
  the **Purchase** module.
- The RED/GREEN stock-value alert formula is
  `(Current Stock Qty) × (Old Price − New Price)`. `Current Stock Qty` is
  produced by the **Inventory** module.
- GST must use the final invoice rate, which requires the invoice line, which
  requires stock to sell.

Building Invoice (9) before Purchase (10) and Inventory (11) means the
loss-sale alert, the stock-value alert and the margin badge are all stubbed
out and then retrofitted into a module that is already "verified complete".

---

## CR-008 — Public self-registration (OPEN)

Module 1 is titled "Authentication & **User Registration**", and the checklist
requires "User registration works" and "Frontend registration page works".

**Risk:** a single-shop ERP with an open `POST /auth/register` means anyone
who finds the URL creates an account inside the shop's accounting system. There
is no email-domain check to apply, no organisation to join, no approval queue.
The first spam bot that finds the endpoint is inside the books.

**Recommendation:** there is no self-registration. The owner creates every
account from `POST /api/v1/users` (permission `USER_MANAGE`), which is already
built. The "registration screen" becomes the **Add User** screen in the admin
area. Same feature, same UI work, no open door.

**If you want self-registration anyway**, the safe version is invite-only:
owner generates a signup token → link sent by SMS/email → recipient sets
password only. Roughly two extra days of work. Say the word and I will build it.

---

## CR-009 — Seed data in production migrations (OPEN)

The rule is "every table must contain at least 10 realistic sample records"
and "seed data automatic load aaganum".

**Risk:** Flyway migrations run in **every** environment, including production.
Ten user accounts with known passwords in `V2__seed_data.sql` means ten working
logins on the shop's live system on day one. This is the single most common way
small ERP deployments get compromised.

**Recommendation — implemented as:**

```
V1__auth_schema.sql          schema + roles + permissions   ALL environments
V900__seed_dev_data.sql      10 users, 10 customers, ...    DEV/TEST ONLY
```

The `V900+` range is excluded from the production Flyway `locations`, so
production physically cannot load it:

```yaml
# application-dev.yml / application-test.yml
spring.flyway.locations: classpath:db/migration,classpath:db/seed
# application-prod.yml
spring.flyway.locations: classpath:db/migration
```

You get full seed data for Swagger, Postman, UI testing and pagination — and
production starts with exactly one bootstrap owner account whose password you
set through an environment variable.


---

## CR-011 — Category and Brand reinstated (APPLIED)

The CR-007 approval listed:

```
Authentication → User Management → Supplier → Customer → Product → Purchase
→ Inventory → Quotation → Invoice → Payment
```

Two corrections applied, both dependency-driven rather than preference:

1. **Category and Brand were dropped from the list**, but CR-004 makes
   `brand_id` and `category_id` mandatory foreign keys on the product master.
   Product cannot be built before its own FK targets exist. They are reinstated
   immediately before Product.

2. **"User Management" as a separate Module 2** — user and role CRUD already
   live inside Module 1's `auth` package (`/v1/users`, `/v1/roles`,
   `/v1/permissions`). Splitting them out would mean two packages owning the
   same tables. Module 1 is therefore titled **Authentication & User Management**.

### LOCKED module order

| # | Module | Package | Depends on |
|---|---|---|---|
| 1 | Authentication & User Management | `auth` | — |
| 2 | Supplier | `supplier` | 1 |
| 3 | Customer | `customer` | 1 |
| 4 | Category | `category` | 1 |
| 5 | Brand | `brand` | 1 |
| 6 | Product | `product` | 4, 5 |
| 7 | Product Variant | `product` | 6 |
| 8 | Purchase | `purchase` | 2, 7 |
| 9 | Inventory | `inventory` | 8 |
| 10 | Quotation | `quotation` | 3, 7, 9 |
| 11 | Invoice | `invoice` | 8, 9, 10 |
| 12 | Payment | `payment` | 11 |

Supplier precedes Customer as approved: Purchase (8) needs Supplier, and
purchase price must exist before anything can be sold at a margin.

---

## CR-012 — Repository structure (APPROVED 2026-08-13)

The instruction contains **two mutually exclusive layouts**.

**Layout A** — per-module backend and frontend folders:

```
module-01-authentication-user-management/backend/src/main/java/...
module-02-supplier/backend/src/main/java/...
```

**Layout B** — one backend, one frontend, organised by module package:

```
hardware-erp/backend/src/main/java/com/company/erp/{auth,supplier,...}
hardware-erp/frontend/src/modules/{auth,supplier,...}
```

Both appear in the same instruction, and the instruction also states — correctly —
that this must remain **one Spring Boot monolith and one React application**.

### Why Layout A cannot deliver that

1. **Maven.** Twelve `src/main/java` roots cannot compile into one JAR. Making
   it work requires twelve Maven modules plus an aggregator POM. That is a
   multi-module Maven build: twelve POMs to version, an inter-module dependency
   graph to maintain, and `mvn clean verify` that must be run from the root or
   silently tests nothing. The instruction explicitly rejects that complexity.
2. **Flyway.** Migrations would be scattered across twelve `resources/db/migration`
   folders. Flyway resolves one ordered version sequence from its configured
   locations; twelve locations with `V1`, `V2`, `V3` in different folders is
   exactly how you get a duplicate-version failure at startup.
3. **Spring.** `@SpringBootApplication` component-scans from one base package.
   Twelve source roots means twelve scan configurations or a hand-maintained
   `scanBasePackages` list that breaks silently when a module is added.
4. **Vite.** One React app builds from one `src`. Twelve `frontend/src` folders
   cannot produce one bundle without twelve npm workspaces.
5. **Shared code.** The instruction forbids duplicating the API client, JWT
   handling, theme and permission logic. Under Layout A there is no natural home
   for them — they end up copied, which is the outcome the rule exists to prevent.

### Recommendation — Layout B, with module-scoped docs

Layout B is already what exists on disk and satisfies every stated goal:

```
hardware-erp/
├── backend/src/main/java/com/hardware/erp/
│   ├── common/  config/  security/          <- shared, never duplicated
│   ├── auth/                                 <- Module 1
│   ├── supplier/                             <- Module 2
│   └── ...                                   one JAR, one component scan
├── backend/src/main/resources/db/
│   ├── migration/  V1__auth_schema.sql       one Flyway sequence
│   └── seed/       V900__seed_dev_data.sql   DEV/TEST only
├── frontend/src/
│   ├── modules/auth/{pages,components,forms,services,hooks,types,validation,tests}
│   ├── shared/  layouts/  routes/  theme/    <- shared, never duplicated
├── docs/
│   └── module-01-authentication-user-management/
│       ├── README.md  MODULE_OVERVIEW.md  API_REFERENCE.md
│       ├── TESTING_GUIDE.md  SETUP_GUIDE.md  SECURITY_GUIDE.md
│       ├── FRONTEND_GUIDE.md  TEST_DATA.md
│       ├── postman/   collection + environment
│       └── diagrams/
└── project-knowledge/
```

A developer can still answer every question the rule demands:

| Question | Answer |
|---|---|
| Module backend code? | `backend/src/main/java/com/hardware/erp/<module>/` |
| Module frontend code? | `frontend/src/modules/<module>/` |
| Its tests? | `backend/src/test/java/com/hardware/erp/<module>/` and `frontend/src/modules/<module>/tests/` |
| Its Postman collection? | `docs/module-NN-<name>/postman/` |
| Its documentation? | `docs/module-NN-<name>/` |
| Its migration? | `backend/src/main/resources/db/migration/VN__<module>_schema.sql` |
| Its seed data? | `backend/src/main/resources/db/seed/` |

### Second conflict — base package

The instruction shows `com/company/erp`. The locked base package is
`com.hardware.erp`, established in Module 1 and enforced by
`registry/check_registry.py`. Renaming it would touch all 84 source files and
every `@PreAuthorize` expression. **Recommendation: keep `com.hardware.erp`.**
Treating `com/company/erp` as illustrative placeholder text rather than a rename.

### Resolution — APPROVED 2026-08-13

Layout B is confirmed. No architecture change. One Spring Boot application, one
React application, one MySQL database. Module folders express **boundaries**,
not deployment units, so a module could be extracted later if the architecture
ever evolves — but nothing is extracted now.

Base package stays `com.hardware.erp`. `com/company/erp` was placeholder text.

**Locked frontend structure:**

```
frontend/src/
├── modules/<module>/{pages,components,forms,services,hooks,types,validation,constants,tests}
├── shared/{components,hooks,utils,types,constants}
├── layouts/  routes/  services/  theme/
└── App.tsx
```

One `package.json`. One Vite build. No per-module React app.

**Cross-module rule:** when a module needs another module's functionality it
imports it and documents the dependency in `MODULE_DEPENDENCY_MAP.md`. It never
copies the code. Shared concerns — API client, JWT handling, interceptors,
permission hooks, theme, dark/light mode, validation and date utilities — live
in `src/shared/` and `src/services/` only.

---

## CR-014 — MySQL 8 → PostgreSQL (APPROVED 2026-08-13)

PostgreSQL is now the single source of truth for development, testing, Docker,
seed data, documentation and deployment. No MySQL configuration is retained
except where this registry records history.

This is the recommendation originally made in the Part 2 design review (§1.1),
where the reason was Row-Level Security. RLS is no longer the driver — the
project is single-shop with no tenant isolation requirement — but the other
reasons stand: CHECK constraints that are actually enforced, partial and
functional indexes, `jsonb` for the price-history and import-preview work coming
in Modules 6–8, and one migration toolchain instead of two.

### Scope of change

| Area | From | To |
|---|---|---|
| Driver | `com.mysql:mysql-connector-j` | `org.postgresql:postgresql` |
| Flyway | `flyway-mysql` | `flyway-database-postgresql` |
| Testcontainers | `MySQLContainer("mysql:8.0.36")` | `PostgreSQLContainer("postgres:16-alpine")` |
| Dialect | `MySQLDialect` | `PostgreSQLDialect` |
| JDBC URL | `jdbc:mysql://…:3306` | `jdbc:postgresql://…:5432` |
| PK generation | `BIGINT AUTO_INCREMENT` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` |
| Timestamps | `DATETIME(3)` | `TIMESTAMP(3)` |
| Table options | `ENGINE=InnoDB … utf8mb4` | removed; database created with `ENCODING 'UTF8'` |
| Auto-update column | `ON UPDATE CURRENT_TIMESTAMP(3)` | trigger `set_updated_at()` |
| Seed timestamps | `NOW(3)`, `DATE_SUB(…, INTERVAL n DAY)` | `CURRENT_TIMESTAMP`, `CURRENT_TIMESTAMP - INTERVAL 'n days'` |

### What did NOT change

Entity classes, DTOs, repositories, services, controllers, permission model,
API paths and the frontend are untouched. `@GeneratedValue(strategy = IDENTITY)`
maps to a PostgreSQL identity column without modification. Table and column
names are unchanged, so the naming registry needs no amendment.

`app_user` keeps its name. `USER` is reserved in PostgreSQL as well, so the
original workaround remains correct — only its justification comment changes.

### Behaviour that changed and had to be compensated — see BUG-AUTH-009

MySQL's `utf8mb4_0900_ai_ci` collation is case-insensitive, so
`UNIQUE (email)` and `UNIQUE (role_name)` silently rejected `Owner@shop.in`
when `owner@shop.in` existed. PostgreSQL compares case-sensitively and would
have accepted both. Compensated with functional unique indexes on
`lower(email)` and `lower(role_name)`.

---

## CR-015 — Business activity log, separate from the security audit log (APPLIED)

Found while implementing Module 2. `SupplierServiceImpl` was recording supplier
creation through `SecurityAuditService` using `AuditAction.USER_CREATED`, which
is wrong twice over: the action name is a lie, and `DATABASE_REGISTRY.md`
already states that `security_audit_log` holds **security events only** and that
business history belongs to each module.

Left alone, by Module 11 the security log would be full of invoice edits and
nobody could find a failed login in it.

### Resolution

A second, general-purpose table: `activity_log`, added in
`V3__activity_log.sql`, with `common/service/ActivityLogService` used by every
business module from Module 2 onwards.

| | `security_audit_log` | `activity_log` |
|---|---|---|
| Records | logins, token misuse, password and role changes | business record create/update/delete |
| Written by | `SecurityAuditService` (Module 1) | `ActivityLogService` (common) |
| Read by | Security log screen, `AUDIT_VIEW` | each module's history view |
| Retention | never deleted by default | never deleted by default |
| Old/new values | no | yes, as `jsonb` |

`activity_log` stores the changed fields as `jsonb`, which is one of the reasons
PostgreSQL was chosen (CR-014): the shape differs per module and a fixed column
list would not fit supplier, product and invoice alike.

### Effect on the naming registry

New table `activity_log`, new class `ActivityLogService`. No existing name
changes, so no amendment is required under CR-002.

---

## CR-016 — Multi-tenant architecture (APPROVED 2026-08-22, in progress)

This reverses the project's founding constraint. `CLAUDE.md` and
`PROJECT_REGISTRY.md` both state, as a locked decision: "Not SaaS. Not
multi-tenant." The owner has now explicitly asked for multi-tenancy, so this
CR replaces that constraint rather than working around it, per the "stop and
raise a Change Request" rule for conflicting instructions.

### Scope

Only Modules 1 (Auth) and 2 (Supplier) exist today, so this CR retrofits
those two. Every module from 3 onward is designed tenant-scoped from its
first migration - no further retrofit needed.

### Isolation strategy

**Shared database, shared schema, `tenant_id` discriminator column** on
every tenant-owned table. Chosen over schema-per-tenant or
database-per-tenant because "one PostgreSQL database" is itself a locked
decision (`PROJECT_REGISTRY.md`) that this CR does not touch, and because a
hardware shop's data volume never justifies the operational cost of
per-tenant schemas.

### New table: `tenant`

| Column | Type | Notes |
|---|---|---|
| tenant_id | BIGINT IDENTITY | PK |
| slug | VARCHAR(60) | UNIQUE, lowercase, used for tenant resolution if subdomain routing is added later |
| name | VARCHAR(200) | shop name |
| status | VARCHAR(20) | CHECK IN ('ACTIVE','SUSPENDED') |
| created_at, updated_at | TIMESTAMP(3) | |

### Tenant-scoped tables

`app_user`, `role`, `supplier`, `supplier_contact` (via parent `supplier`)
all gain `tenant_id BIGINT NOT NULL REFERENCES tenant`. `permission` stays
global - it is a fixed code-level catalog of what *can* be granted, not
tenant data. `refresh_token`, `password_reset_token`, `security_audit_log`
and `activity_log` are not given their own `tenant_id`; they already scope
through `user_id`, and duplicating the column everywhere a join can reach it
is exactly the premature-abstraction CLAUDE.md warns against.

### Login identifier stays globally unique - deliberately

The obvious multi-tenant design scopes `mobile_no`/`email` uniqueness to
`(tenant_id, mobile_no)`. This CR does **not** do that. CR-008 already
establishes there is no self-registration and no tenant selector on login -
`identifier` alone must resolve one user. Scoping uniqueness per tenant would
require resolving the tenant *before* login (subdomain routing or a tenant
picker), which is out of scope here. Trade-off, stated plainly: a mobile
number or email can be registered with exactly one shop platform-wide, ever.
Revisit only if a tenant picker or subdomain routing is later added.

### Role uniqueness is rescoped

`role_code`/`role_name` move from globally unique to unique per
`(tenant_id, role_code)` / `(tenant_id, role_name)`. Each tenant gets its own
OWNER/MANAGER/ACCOUNTANT/STAFF rows, seeded at tenant creation - a
permission change by one shop's owner must never affect another shop's role
of the same name.

### Supplier uniqueness is rescoped

`supplier_code`, the `lower(supplier_name)` and `upper(gst_no)` functional
unique indexes all move from global to per-`tenant_id`. `SUP-0001` exists
once *per shop*, not once platform-wide -
`SupplierServiceImpl.resolveCode()`'s "highest generated number" query is
scoped the same way.

### Request-time enforcement — no new JWT claim needed

`JwtAuthenticationFilter` already reloads the full `User` row from the
database on every request, by the `sub` (user id) claim, so that a revoked
permission or a deactivated account takes effect immediately rather than
waiting for the access token to expire (BUG-AUTH-001's "minimal claims"
design). `tenant_id` rides along for free the same way: `AppUserDetails`
carries `tenantId` sourced from `user.getTenant().getId()` on that same
per-request DB read, never trusted from the JWT itself.
`SecurityUtils.currentTenantId()` is the one sanctioned way to read it,
mirroring `currentUserId()`. Every tenant-scoped repository query takes
`tenant_id` from this - **never** from a request parameter or path variable -
so no client input, and no JWT tampering, can cross a tenant boundary. This
is the actual security boundary; the `tenant_id` column existing is
necessary but not sufficient without this enforcement at every query.

### Tenant provisioning

No self-service signup, matching CR-008. `BootstrapOwnerInitializer` is
extended to create the first tenant (from `APP_BOOTSTRAP_TENANT_SLUG` /
`APP_BOOTSTRAP_TENANT_NAME`) plus its four default roles plus its owner
account, atomically, on first boot - exactly today's bootstrap flow, now
tenant-scoped. **Not** in scope here: a platform-admin flow to provision a
*second* tenant later. That is real, separate work (who is a platform admin,
where does that UI live, is it even in this application or a separate
internal tool) and deserves its own CR once the foundation below is proven.

### Migration

`V6__multi_tenant_foundation.sql`: creates `tenant`; inserts one default
tenant row so existing dev data has somewhere to attach; adds `tenant_id`
to `app_user`, `role`, `supplier` as `NOT NULL DEFAULT <default tenant id>`
so existing rows backfill automatically, then drops the `DEFAULT` so every
future `INSERT` must state its tenant explicitly rather than silently
inheriting one; drops and recreates the affected unique constraints/indexes
as composite `(tenant_id, ...)`.

### Test impact

Every `@SpringBootTest` integration test currently assumes one implicit
tenant. `AbstractIntegrationTest`'s seed setup needs a tenant fixture; this
is expected to surface the same way BUG-AUTH-010/011/012 and BUG-SUP-002/003/004
did - compile first, then run, then fix what the run finds, rather than
hand-auditing 184 tests for tenant-safety up front.

### Effect on the naming registry

New table `tenant`. New column `tenant_id` on `app_user`, `role`,
`supplier`. Renamed nothing. CR-002 amendment: `tenant_id` joins `<table>_id`
primary-key and `<target>_id` foreign-key naming as a standing pattern -
every future tenant-owned table carries it from its first migration.

---

## CR-017 — Supplier form: multi-step wizard, read-only code on create (PROPOSED 2026-08-22)

Frontend-only. `SupplierForm.tsx` (`frontend/src/modules/supplier/forms/`) is
a single long scrolling form. Splits it into four steps - Basic Info, Tax &
Address, Payment & Bank, Review - sharing one `react-hook-form` instance
across the wizard rather than one per step, so values survive Back/Next and
validation stays keyed to `supplierSchema` unchanged. `supplierCode` becomes
read-only on create (disabled input, "Generated automatically" hint) since
`SupplierServiceImpl.resolveCode()` already generates it server-side (per
CR-016, now per-tenant) - editing an existing supplier still shows and
allows the field to change, matching current backend behaviour. No backend
change; no migration.

## CR-018 — Bank account number: encryption at rest, permission-gated reveal (APPLIED 2026-08-23)

`supplier.bank_account_no` is plain `VARCHAR` today; `SupplierMapper` masks
it to last-4 in every response but the column itself is readable by anyone
with database access. Adds application-level authenticated encryption
(AES-256-GCM) at the entity/converter layer, keyed by an environment-provided
key (`APP_ENCRYPTION_KEY`, never in source), so the database only ever holds
ciphertext. New permission `SUPPLIER_VIEW_BANK_ACCOUNT` (extends the existing
`PermissionCode` catalog); new endpoint
`GET /v1/suppliers/{id}/bank-account-number` returns the decrypted value only
to a caller holding that permission and only for a supplier in their own
tenant (CR-016), and is never included in the list or default detail
response. Decrypted values are never logged - `ActivityLogService`'s
existing redaction list already excludes bank account number
(`SupplierServiceImpl.snapshot()`), this CR does not change that. Migration:
`V17__encrypt_supplier_bank_account.sql` (V7 was already taken by
Category/Brand/Product by the time this was built) widens the column to
hold ciphertext and re-encrypts existing plaintext values via a one-time
data migration (Java, not SQL - encryption needs the application's key, not
the database's). Frontend: eye-toggle button on the bank account field,
calling the new endpoint on demand rather than ever holding the decrypted
value in page state longer than the toggle is open; hidden entirely for a
user without the permission (backend-enforced, not just hidden in the UI).

### As built (2026-08-23)

- `common/security/FieldEncryptor` - plain (non-Spring) AES-256-GCM utility,
  `ENC:`-prefixed ciphertext so legacy plaintext is distinguishable from
  encrypted values at a glance. Graceful degradation matching every other
  optional integration this session (mail/AI/WhatsApp): with no key
  configured, `encrypt()` returns plaintext unchanged rather than throwing,
  so a fresh dev environment isn't broken by an unset env var.
- `supplier/entity/BankAccountNumberConverter` - a JPA `AttributeConverter`
  applied via `@Convert` on `Supplier.bankAccountNo` only (not `autoApply`).
  Entity/service code always sees plaintext; only the DB column is ciphertext.
- `SupplierBankAccountEncryptionRunner` (`ApplicationRunner`) - the one-time
  backfill, idempotent (only rows without the `ENC:` prefix are touched, so
  it's a no-op on every subsequent boot). Writes via a native `UPDATE`
  (`SupplierRepository.writeEncryptedBankAccountNumber`), bypassing Hibernate
  dirty-checking, which would otherwise skip the row entirely since the
  Java-level attribute value never changes.
- New `AuditAction.BANK_ACCOUNT_REVEALED`, logged to `security_audit_log`
  (not `activity_log`) on every reveal - a deliberate exception to this
  class's own "business changes go to activity_log" rule, reasoned through
  in `SupplierServiceImpl.revealBankAccountNumber()`'s own comment: a reveal
  is a sensitive-data-access event, not a business record change, which is
  exactly what the security log exists to answer "who saw this and when" for.
- `SUPPLIER_VIEW_BANK_ACCOUNT` granted to OWNER only in the `V17` backfill
  (not MANAGER/ACCOUNTANT/STAFF) - the owner can grant it further via the
  Roles UI if wanted; OWNER-only is the safer default for a newly-added
  sensitive-data permission.
- Tests: `FieldEncryptorTest` (7, round-trip, non-deterministic ciphertext,
  legacy passthrough, graceful degradation, wrong-key-length rejection,
  clear failure decrypting without a key) + 2 new `SupplierServiceImplTest`
  cases. Backend suite: 160/162 (2 pre-existing BUG-ENV-002 Docker failures,
  unrelated).
- **Live-verified**, not just tested: restarted the backend with a real
  `APP_ENCRYPTION_KEY`, confirmed the startup log encrypted exactly the 12
  suppliers that had a real `bank_account_no`, confirmed the database column
  now holds `ENC:`-prefixed base64 ciphertext (not plaintext) via `psql`,
  confirmed `GET /v1/suppliers/{id}` still returns the masked last-4 and
  `GET /v1/suppliers/{id}/bank-account-number` returns the correct full
  number matching those last 4 digits, confirmed a `BANK_ACCOUNT_REVEALED`
  row landed in `security_audit_log`, and drove the actual eye-toggle in a
  real browser session as OWNER (reveals, then re-masks on second click) and
  as MANAGER (masked number visible, reveal button correctly absent - holds
  `SUPPLIER_VIEW` but not `SUPPLIER_VIEW_BANK_ACCOUNT`).

## CR-019 — Per-user theme and language preference, i18n foundation (PROPOSED 2026-08-22)

Today's `ThemeProvider` (`frontend/src/theme/ThemeProvider.tsx`) is
`localStorage`-only and global per-browser, not per-user; there is no
language/i18n system at all. Adds `theme VARCHAR(10) DEFAULT 'SYSTEM'` and
`language VARCHAR(10) DEFAULT 'en'` to `app_user`
(`V8__user_preferences.sql`), a `PATCH /v1/auth/me/preferences` endpoint
(extends the existing `/auth/me` surface rather than inventing a parallel
one), and threads the saved values through `LoginResponse`/`UserResponse` so
they are available immediately at login with no second round trip -
avoiding the theme-flash the request called out. `ThemeProvider` keeps
`localStorage` as a fast-first-paint cache but the server value is
authoritative and overwrites it after login. i18n: a minimal
`react-i18next`-style key namespace (`common.save`, `supplier.supplierName`,
...) introduced module-by-module starting with `shared/` and `auth/`, not a
one-shot rewrite of all ~90 frontend files - only English (`en`) ships
initially, with the key structure in place so a second locale is a
translation-file addition, not a rearchitecture. Business data (supplier
names, remarks, etc.) is never routed through the translation system, only
UI chrome.

---

## CR-020 — Module order: Category, Brand & Product before Customer (APPROVED 2026-08-22)

CR-007/CR-011 lock the module order as Customer → Category → Brand →
Product → Product Variant → Purchase → Inventory → Quotation → Invoice →
Payment. The owner asked, in a single combined request, for "Module 3 =
Product Management (incl. Category and Brand)" ahead of Customer, alongside
four other modules and a long list of cross-cutting features. Per
PROJECT_SKILLS #21, documenting the deviation rather than silently
resequencing.

### Resolution

Built Category, Brand and Product as one increment, ahead of Customer.
Reasoned acceptable because:
- Category/Brand/Product have no dependency on Customer - PROJECT_SKILLS
  #22's ordering rule ("module order follows data dependency") is about
  Purchase/Inventory needing to precede Invoice for loss-sale alerts, which
  this reordering does not violate.
- The owner's full request also included Sales/Invoice/Payment, which
  *does* depend on Purchase and Inventory existing first (loss-sale needs
  real purchase price and stock) - those still wait, in the original
  data-dependency order, regardless of which cosmetic module number the
  owner used for them.

Customer, and the Sales → Invoice → Payment chain, resume in their
originally locked position once Product Variant, Purchase and Inventory
exist. This CR reorders, it does not reprioritize past the dependency rule.

### Effect on the naming registry

No renames. `PROJECT_REGISTRY.md`'s module table replaced the fixed 1-12
numbering with a plain ordered list, since Category/Brand/Product were
originally three separate numbered modules (4/5/6) and are now delivered as
one.

---

## CR-021 — Inventory + Invoice/Payment built ahead of full Customer, with a minimal auto-created Customer record (APPROVED 2026-08-22)

The owner asked to build Module 4 (Inventory) and an Invoice creation flow
"without customer" - meaning without building the full Module 5 Customer
management screens - while still wanting invoices linked to a real buyer
record rather than free text. Asked directly which shape was wanted; the
owner chose: **a minimal Customer row is created or matched automatically
(by mobile number) from inside the invoice flow**, with no dedicated
Customer list/create/edit UI yet.

### Why this doesn't violate PROJECT_SKILLS #22

Inventory has no dependency on Customer at all - it only depends on Product,
which already exists. Invoice *does* need Inventory (to decrement stock on
sale) and needs *a* buyer reference, but does not need the full Customer
*management* module to exist - only a minimal `customer` table. Building
that minimal table now, and the full CRUD/UI later as Module 5 proper, is a
scope split, not a dependency violation.

### What is being built now vs. deferred

**Now**: `customer` table (id, tenant_id, customer_code, customer_name,
mobile_no, email, gst_no, credit_limit_paise, status) with a
find-or-create-by-mobile-number lookup, invoked only from
`InvoiceServiceImpl`. No `CustomerController`, no `CUSTOMER_VIEW`/
`CUSTOMER_MANAGE`-gated endpoints, no frontend Customer pages.

**Deferred to Module 5 proper**: billing/shipping address split, PAN,
bank details, a dedicated Customer list/detail/create/edit UI, and exposing
`CUSTOMER_VIEW`/`CUSTOMER_MANAGE` on real endpoints. The `customer` table
built now is intentionally forward-compatible with that later work (same
tenant-scoping and code-generation pattern as Supplier/Product), not a
throwaway stub that will need a rewrite.

### Effect on the naming registry

No renames. `customer`, `stock`, `stock_movement`, `invoice`,
`invoice_item`, `payment` are new tables, added via `V8` and `V9`
migrations, following the existing `<table>_id` PK / `tenant_id`
discriminator / `status VARCHAR(20)` + CHECK conventions locked in
`PROJECT_REGISTRY.md`.

---

## CR-022 — Invoice PDF, shop GST settings, Customer GST capture, Quotation module (APPROVED 2026-08-22)

The owner asked, in one combined message, for: a downloadable PDF for
invoices; a way to see invoices raised in a given month; a Quotation
feature that is independent of whether the customer actually buys; GST
number capture on Customer; and the shop's own GST number plus a
signature to appear on the GST bill, "refer the original sample GST
bill". Five features, scoped and resolved together because the PDF and
Quotation both depend on the same underlying GST data.

### Why Quotation, ahead of Purchase/Inventory pricing

CR-011's locked order puts Quotation (10) after Purchase (8) and
Inventory (9), because Quotation needs a sellable product with a real
price - which already exists (Product Variant, Module 6/7, is done).
Quotation does **not** decrement stock and creates no financial record
- it is a price document only ("depend upon the customer" - a
quotation exists whether or not the customer buys), so it has no
dependency on Purchase/Inventory the way Invoice does. Building it now
is a scope match to what Product already provides, not a violation of
PROJECT_SKILLS #22's data-dependency rule the way jumping Invoice ahead
would have been.

### Digital signature — interpreted as printed signatory block, not PKI signing

"Digital signature... mandatory" is read as: the GST bill layout must
include a signature block (as every real GST tax invoice does), not
that the PDF is cryptographically signed with a Digital Signature
Certificate. A DSC requires the shop to own a hardware/USB signing
token and this application to hold and use its private key - a real
security/compliance undertaking with no existing infrastructure here,
and not something to build silently into a document-generation
feature. What ships: `tenant.signatory_name`, printed on the PDF as
"For {shop name}" with a signature line and the signatory's name/
designation underneath - matching how the large majority of small
businesses' GST invoices actually look. Revisit as its own CR if real
DSC-based signing is wanted later.

### GST split — CGST+SGST vs IGST, computed at PDF render time

`invoice_item.gst_rate_percent` and `invoice.gst_amount_paise` already
exist (built with Invoice) as one lump sum, not split by tax head. Per
CGST/SGST Rules, an intra-state sale splits GST into equal CGST+SGST;
an inter-state sale is IGST in full. The split is computed from
`tenant.state_code` vs `customer.state_code` **only when the PDF is
generated** - no new column stores the split, since it is fully
derivable from data that already exists once both state codes are
added. If either state code is blank, the invoice defaults to
intra-state (CGST+SGST) - the common case for a local hardware shop.

### Schema

`V10__invoice_gst_and_quotation_schema.sql`:
- `tenant` gains `gst_no`, `address_line1`, `address_line2`, `city`,
  `state_code`, `pincode`, `signatory_name` - all nullable (a shop
  that hasn't filled in Settings yet can still trade; the PDF prints
  "GSTIN: Not set" rather than failing).
- `customer` gains `address_line1`, `address_line2`, `city`,
  `state_code`, `pincode` - `gst_no` already existed since CR-021 but
  was never settable from the Invoice flow; `InvoiceRequest` now
  accepts `customerGstNo`/`customerStateCode` and
  `findOrCreateCustomer` updates them on an existing match too, not
  only at creation.
- New tables `quotation` / `quotation_item`, structurally identical to
  `invoice` / `invoice_item` minus every payment-related column, plus
  `status` (`DRAFT`/`SENT`/`ACCEPTED`/`REJECTED`/`EXPIRED`/
  `CONVERTED`), `valid_until DATE`, and `converted_invoice_id` (nullable
  FK to `invoice`, set only by the convert action).

### Convert-to-Invoice

`POST /v1/quotations/{id}/convert` builds a real `Invoice` from the
quotation's line items through the exact same `InvoiceServiceImpl`
path an ordinary invoice creation uses (stock decrements, GST
recalculated from current product rates, not frozen quotation rates -
a quotation is not a price lock) and marks the quotation `CONVERTED`
with the new invoice's id. A quotation past `valid_until` cannot
convert; the caller must re-quote.

### PDF generation

`openhtmltopdf-pdfbox` added to `pom.xml` (Maven Central reachable,
Apache-2.0, no AGPL licensing concern the way iText would raise). A new
`InvoicePdfService` builds an HTML string from `Invoice` + `Tenant` +
`Customer` and renders it server-side - no client-side PDF library, no
stored binary (`GET /v1/invoices/{id}/pdf` regenerates on request, same
principle as CR-018's "decrypt on demand, never cache the sensitive
value").

### Effect on the naming registry

New tables `quotation`, `quotation_item`. New columns on `tenant` and
`customer` as listed above. No renames.

---

## CR-023 — Customer module, file storage, master data, sidebar/dashboard expansion (APPROVED 2026-08-22)

A 40-section enhancement request covering navigation restructure, a full
Customer module, image upload (profile photo, shop logo, digital
signature), state/bank master data, and UI patterns (unsaved-changes
confirmation, view/edit toggle). Investigated before writing any code, per
the request's own Phase 1 instruction - findings below drove scope.

### Storage decision: Postgres `bytea`, not local disk or S3

No file storage of any kind exists anywhere in the codebase today (no
multipart handling, no blob columns, no storage SDK dependency). Three
features need it (photo, logo, signature), so this is one shared decision,
not three. `PROJECT_REGISTRY.md` locks "one PostgreSQL database" as the
project's storage architecture; a shop's image volume (a handful of small
images per tenant) never justifies standing up a second storage system.
`bytea` columns keep backup/restore exactly as simple as it already is -
one `pg_dump` covers everything, no separate volume to mount or S3 bucket
to provision for what is explicitly a single-shop, self-hosted monolith.
Images are served through an authenticated endpoint
(`GET /v1/{resource}/{id}/photo` etc.), never a public static path, since
CLAUDE.md's tenant-isolation rule (CR-016) applies to images the same as
any other tenant-owned data.

### Explicitly out of scope, with reasons

- **Purchase sub-navigation** (Purchase Orders/Invoices/Returns/Payments/
  History): none of this exists in the backend - no entity, no table.
  Building five new modules as a side effect of a navigation reorg would
  violate the locked module order (CR-011: Purchase is Module 8, not
  started) and PROJECT_SKILLS #22. The request's own instruction - "if a
  module does not yet exist ... implement navigation in a way that does
  not create broken routes" - is satisfied by leaving these as the
  existing `available: false` placeholders in `Sidebar.tsx`, unchanged.
- **Customer Returns / Damage tracking**: zero Return or Damage concept
  exists anywhere in the schema (confirmed by search, not assumption).
  The request's own instruction says not to fabricate this if it doesn't
  exist. Not built. Would need its own CR (a real Sales Return module) if
  wanted later.
- **Tag/chip multi-select UI**: the request's example (`Hardware ×,
  Electrical ×`) has no real backing field - `product.category_id` is a
  single `@ManyToOne`, not many-to-many. Building the UI with nothing to
  attach it to would be decorative, not functional. Not built.

### What is built

Customer module (Module 5) proper: `CustomerController`, list/search,
detail page with a financial summary computed from real `invoice`/
`payment` rows (never fabricated), replacing CR-021's minimal
find-or-create-only table with full CRUD and a `CUSTOMER_VIEW`/
`CUSTOMER_MANAGE`-gated UI. Image storage foundation (`bytea` + serving
endpoint pattern) reused for profile photo, shop logo, and a drawn-or-
uploaded digital signature (frontend canvas library added). State master
(fixed list of Indian states/UTs with their GST state codes, frontend-only
- no schema change, `state_code` already exists as a plain column)
auto-fills the 2-digit code on selection. Bank master (fixed list of major
Indian banks + "Other") backs `supplier.bank_name`, still stored as plain
`VARCHAR` - no migration, this is a UI-layer constraint only. Sidebar
sections gain click-to-collapse (default expanded, so today's navigation
behavior is unchanged unless a user chooses to collapse a section).
Dashboard gains sales-side aggregates only (Total Sales, Today's Sales,
Outstanding Customer Balance, Recent Quotations/Customers) since Invoice/
Payment/Customer data genuinely supports them; no purchase-side metric is
added. Supplier form/detail/list labels reworded to "Supplier Shop Name" /
"Contact Person Name" - JSX text only, `supplierName`/`contactPerson`
field names unchanged in entity, DTO, and validation schema, per the
existing-code audit above.

### Bank-account-number validation - a deliberate limitation, stated plainly

The request asks for per-bank account-number length rules. There is no
single authoritative, stable source for exact account-number length per
Indian bank - it varies by bank *and* by scheme/branch generation, and
hardcoding guessed lengths risks rejecting real, valid customer account
numbers, which is worse than no rule at all for a financial field.
Implemented instead: a generic length-range check (9-18 digits, matching
the real-world range across Indian banks), account numbers always
handled as `String` end-to-end (leading zeros preserved, confirmed already
true of the existing `bank_account_no VARCHAR(30)` column). Precise
per-bank rules are not implemented; revisit only with an authoritative
source (e.g. a bank-provided validation spec) if this becomes a real
requirement.

### Effect on the naming registry

New table(s) for Customer module extensions and image storage (see
`DATABASE_REGISTRY.md` for the exact list once implemented). No renames -
Supplier terminology changes are display labels only.

---

## CR-026 — Settings view/edit toggle, live chrome refresh, PDF preview/polish, numeric input fix (APPROVED 2026-08-23)

A bug report plus feature request, several items concrete enough to fix directly rather than propose.

**Real bugs fixed:**
- `SidebarBrand` and the header user-menu avatar each fetched their own data once on mount with no refresh trigger, so a Settings/Profile save elsewhere in the still-mounted `AppLayout` never showed up without a full page reload. New `AppChromeProvider` (`frontend/src/layouts/AppChromeProvider.tsx`) is the single source both read from; `ShopSettingsPage`/`ProfilePage` push into it after a save. The header avatar previously never rendered the uploaded photo at all - initials only, regardless of upload state.
- A numeric field whose react-hook-form default is `0` showed `0100` when the user typed `100`, because `<input type="number">` never strips a leading zero as you type. New `NumberInput` (`frontend/src/shared/components/ui/number-input.tsx`) renders as text (`inputMode="decimal"`), strips a leading zero itself, shows genuinely empty instead of `0`, and commits `0` on an empty field without forcing "0" back into view. Applied to Product's numeric fields first (the reported case); other numeric forms follow the same pattern.
- `productCode`/`categoryCode`/`brandCode` validation required uppercase but never transformed it, so a manually-typed lowercase code (the reported "prd-0002") failed validation outright and was never saved - not a display bug, the record never existed. `codeRules` now `.toUpperCase()`s before the regex check, matching the pattern already used for `gstNo`/`panNo`/`bankIfsc`; `className="uppercase"` added to the same inputs for live visual feedback.

**New/extended features:**
- Shop Settings gains a view/edit toggle (`frontend/src/modules/settings/pages/ShopSettingsPage.tsx`), matching the pattern already built for Customer detail (`UnsavedChangesDialog`, dirty-check on exit) - previously every field was a live-editable input with no explicit Edit step.
- Invoice and Quotation detail pages gain a "Preview" action that opens the PDF in a new tab via an object URL, alongside the existing download. Quotation had no PDF at all; a `QuotationPdfService` is added (same layout family as `InvoicePdfService`, titled "QUOTATION" not "TAX INVOICE", no payment/bank/QR section since nothing is due yet, adds a "Valid Until" line).
- `tenant_upi_qr` (new `bytea` table, same 1:1 pattern as `tenant_logo`/`tenant_signature`) lets the shop upload an existing UPI/GPay QR code image directly, as an alternative to the auto-generated QR built from the UPI ID text. `InvoicePdfService.paymentBlock()` prefers the uploaded image when present, falling back to the generated QR.
- Invoice PDF: light section tint, a divider before the totals/payment section, a "Thank you for shopping with us" line, and clearer "This is a computer-generated invoice" wording.

### Effect on the naming registry

New table `tenant_upi_qr`. No renames.

---

---

## CR-041 — Per-tenant document number allocator (APPROVED 2026-08-26)

### The defect

Nine document types across eight modules generated their next code the same way:

```java
int next = xRepository.findHighestGeneratedCodeNumber(tenantId) + 1;
return CODE_PREFIX + String.format("%0" + CODE_DIGITS + "d", next);
```

Ten call sites shared that shape (Customer had two — `CustomerServiceImpl`
and `CustomerLookupServiceImpl`; Purchase had two — `PurchaseServiceImpl`
and `PurchaseImportServiceImpl`).

Read-then-write with nothing holding the gap. Under concurrency two callers
read the same MAX and both attempt the same number. **No duplicate was ever
stored** — every one of these tables already carries `UNIQUE (tenant_id,
<code>)` — but the loser's request failed on the constraint and their
invoice, quotation or purchase was lost. On a busy counter that is a
confusing intermittent 500; under CR-043's offline sync, replaying a queued
batch from three devices would have hit it on nearly every run.

### The fix

New `document_sequence` table (V29): one row per `(tenant_id, doc_type)`
holding `next_value`, allocated through `DocumentSequenceService.next()`
under `SELECT … FOR UPDATE`.

Two deliberate design decisions, both recorded because the obvious
alternative is wrong:

- **`Propagation.MANDATORY`, joining the caller's transaction** — not
  `REQUIRES_NEW`. Allocating in a separate transaction would release the
  lock immediately and restore the original race. Joining also means a
  rolled-back invoice does not consume a number, which matters because GST
  requires a consecutive serial with no gaps. The cost is that document
  creation serialises per tenant per type; for a hardware shop counter that
  is a handful of concurrent writers and entirely acceptable.
- **Lock ordering is sequence-first in every caller.** The sequence row is
  always taken before stock or coupon rows, so the consistent ordering keeps
  the system deadlock-free.

`DocumentType` now owns each prefix and digit width in one place, replacing
nine pairs of copy-pasted `CODE_PREFIX`/`CODE_DIGITS` constants — the
duplication that let the same race be pasted ten times. The nine now-dead
`findHighestGeneratedCodeNumber` repository methods were removed so the
pattern cannot be copied again.

### A second defect found by the regression test

`DocumentSequenceServiceIT.backfillContinuesExistingRun` failed on the
first run: the allocator returned `SUP-0001` while seeded suppliers already
occupied `SUP-0001`..`SUP-0013`.

Cause: Flyway runs V29 **before** the V900–V903 seed files, so in the dev and
test profiles the backfill reads empty tables and seeds every counter at 1 —
then the seed inserts rows on top of it. Production is unaffected (the
V9xx seed is dev/test only), but any developer's first generated supplier
code would have collided. Fixed by `V904__seed_document_sequence.sql`,
which re-syncs every counter after the seed using
`ON CONFLICT … DO UPDATE SET next_value = GREATEST(…)`.

### Environment fix carried in the same change

Integration tests could not run on Windows at all: Docker Engine 29 rejects
the API version docker-java negotiates by default (it advertises 1.32; the
daemon's minimum is 1.40), so Testcontainers failed with "Could not find a
valid Docker environment" for **every** existing IT, not just the new one.
`src/test/resources/docker-java.properties` pins `api.version=1.44`, the
documented workaround for Testcontainers 1.x. Delete it when the project
moves to Testcontainers 2.x.

### Effect on the naming registry

New table `document_sequence`. New enum `DocumentType`. No renames — every
existing code keeps its exact prefix and width, and the V29 backfill
continues each tenant's existing run rather than restarting it.

---

## CR-045 — Version 1 Git and environment foundation (APPROVED 2026-08-26)

The repository had **no commits at all**. Everything below was built on a
working tree that had never been under version control.

### Branches are workflow; profiles are environments

```
main                    production-ready, tagged releases
  └── develop           integration
        ├── feature/*
        ├── bugfix/*
        └── hotfix/*    branched from main, merged back into main AND develop
```

No `production`, `development` or `testing` branch. A branch named after an
environment invites merging *to deploy*, which is how a repository acquires
three diverging mainlines. Where the software runs is a Spring profile:
`local`, `dev`, `test`, `prod`.

`application-local.yml` and `application-test.yml` are new. The QA `test`
profile is treated as production for data handling (schema-only Flyway, no
seed accounts) and as development for diagnostics (DEBUG application logging),
because QA needs to see why something failed but a QA box's configuration
resembles production's closely enough to be worth protecting.

`src/test/resources/application-test.yml` shadows the deployable one during
`mvn test` — `target/test-classes` precedes `target/classes` on the classpath.
Keeping both named `test` is deliberate; only one is ever loaded.

### Developer inspection: environment AND person, never role alone

The requirement was "developers can inspect in non-production; normal users
never can, and an admin is not automatically a developer".

Two independent gates, both server-side, both required:

| Gate | Mechanism | Where enforced |
|---|---|---|
| Environment | `app.developer-inspection.enabled` | `SecurityConfig` denies the whole `/v1/dev` and `/v1/debug` trees when false; `DeveloperInspectionService` re-checks |
| Person | `DEVELOPER_INSPECT` permission (V30) | `@PreAuthorize` per endpoint |

Production is closed twice over: `application-prod.yml` sets a literal `false`
with no `${...}` behind it, so no environment variable can open it, and
`DeveloperInspectionService.environmentAllows()` returns false whenever the
`prod` profile is active regardless of configuration. The second lock exists so
that a later edit to the first cannot quietly re-open it — the same stance
`JwtSecretGuard` already takes on the placeholder signing key.

**The OWNER exclusion needed two changes, not one.** Roles acquire grants two
different ways, and both had to be closed:

1. `V30__developer_inspection_permission.sql` deliberately omits the OWNER
   grant that every other module migration ends with.
2. `TenantRegistrationServiceImpl` assigns OWNER from the *live* permission
   table so future codes are picked up automatically — exactly the behaviour
   that would have leaked this one. It now filters out the `DEVELOPER` module.

Without the second, every shop registered after this migration would have had a
diagnostics console on the owner account. `DeveloperInspectionAccessIT` asserts
no seeded role holds it and that the owner — who holds every ERP permission —
still receives 403.

Endpoints: `status` (both gates reported separately, so a developer can tell
"wrong environment" from "permission not granted"), `runtime` (a fixed list of
named fields, never a system-property or environment dump), and `request-echo`
(credential-bearing headers removed, not masked). Diagnostics answer **404, not
403**, where inspection is off: a 403 confirms the route exists.

Actuator beyond `/actuator/health` now requires `DEVELOPER_INSPECT`. `env`,
`configprops` and `beans` would otherwise print the datasource password and the
JWT signing key to any authenticated user. `/actuator/health` stays public
because the hosting platform uses it as a liveness probe.

### Explicitly rejected: DevTools blocking

No F12 interception, no right-click blocking, no Ctrl+Shift+I handler, no
DevTools detection loop, no console clearing. All of it is trivially bypassed,
none of it protects anything, and it makes the application feel broken to the
honest majority. Production safety comes from authentication, authorization,
server-side access control, sanitized errors and the source-map policy.

### Secrets

`application.yml` shipped a real developer database login as the default for
`spring.datasource.username` / `password`, and pointed at a database name and
port the running container had not used since 2026-08-23. Replaced with the
throwaway docker-compose values **before the first commit**, so it never
entered history. `.gitignore` extended to keys, certificates, TypeScript build
info and JVM crash dumps.

### Two pre-existing failures found by running the suite end to end

Both reproduced identically at the baseline commit; neither was caused by this
change. Recorded as **BUG-AUTH-014** (refresh-token reuse detection had no
working test since BUG-AUTH-009) and **BUG-SEC-003** (`RateLimitFilter` keyed on
`getServletPath()`, which MockMvc leaves empty, so rate limiting — the
brute-force control — had never been exercised by any test).

Both were coverage holes rather than production holes, which is precisely why
they had survived: a red test that has been red for a while stops being read as
a signal. CI is the answer, and it is part of this change.

### Effect on the naming registry

New permission code `DEVELOPER_INSPECT`, new `permission.module_code` value
`DEVELOPER`. New package `com.hardware.erp.developer`. New helper
`SecurityUtils.requestPath`. No renames.

---

## CR-051 — Idempotency service for double-submitted writes (APPLIED 2026-08-31)

### The problem

Master Prompt Phase 1. A shop-counter double-click, a request retried after a
timeout, or a slow connection that makes the owner click "Create" twice must
never create the same document twice. Nothing in the codebase before this
CR prevented it — a retried `POST` simply ran the whole create path again.

### The fix

`IdempotencyService.execute(tenantId, operation, idempotencyKey, requestPayload,
responseType, action)` — one shared, low-level service, reused by every
write endpoint that accepts a client-supplied idempotency key, rather than a
bespoke guard per module.

New `idempotency_record` table (V34): `UNIQUE (tenant_id, idempotency_key)`,
`response_status = 0` as an in-flight sentinel (never a real HTTP status),
`request_hash` (SHA-256, reusing `JwtService.hashToken()`'s exact
`MessageDigest`/`HexFormat` pattern) to detect the same key reused with a
*different* payload — rejected as `IdempotencyKeyReusedException` (409), not
silently replayed, because serving the wrong cached response would be wrong,
not merely redundant.

Same two-step "insert-if-absent, then `SELECT … FOR UPDATE`" pattern as
`DocumentSequenceService` (CR-041), and for the identical reason:
`Propagation.MANDATORY`, joining the caller's transaction rather than
`REQUIRES_NEW`, so the row lock is held until the caller's own writes commit
or roll back together with it — a rolled-back attempt leaves no completed
record behind, and a retry with the same key is free to try again from
scratch.

**Design correction made mid-implementation:** the first version resolved
`tenantId` internally via `SecurityUtils.requireCurrentTenantId()`. Running
`IdempotencyServiceIT` against a raw `@Autowired` service (no web request, no
`SecurityContext`) failed 4 of 5 tests with `AuthException: Not authenticated`
— a real design bug, not a test artifact. `DocumentSequenceService.next(docType,
tenantId)` takes `tenantId` as an explicit parameter for exactly this reason: a
low-level reusable service should not re-derive identity its caller has
already resolved. Fixed by adding `Long tenantId` as the interface's first
parameter.

Proven with a 20-thread concurrent-caller test asserting the wrapped action
ran exactly once and every caller received an identical result, plus
key-reuse-with-different-payload rejection, rollback-leaves-no-record, and
independent-keys-never-interfere. `mvn verify`: 5/5 pass against real
PostgreSQL (Testcontainers — H2 cannot faithfully reproduce the
`SELECT … FOR UPDATE` semantics this guarantee depends on).

### Effect on the naming registry

New table `idempotency_record`. New package
`com.hardware.erp.common.idempotency`. No renames.

---

## CR-052 — Sales Order, Delivery Challan, Credit Note (APPLIED 2026-08-31)

### Scope

Master Prompt Phase 1, the second half (idempotency, CR-051, was the first).
Three document types the sales pipeline was missing between Quotation and
Invoice, and after Invoice:

```
Quotation --> Sales Order --> Delivery Challan --> Invoice --> Credit Note
     \______________/______________________/
      (either can skip straight to Invoice)
```

Built as three modules (`salesorder`, `deliverychallan`, `creditnote`),
migrations V35–V37, each modeled directly on the closest existing module
rather than inventing new shapes — per CLAUDE.md's "reuse existing
architecture" rule and the Master Prompt's own repeated instruction to prefer
existing patterns.

### Sales Order

Modeled on Quotation (CR-022) field for field: never moves stock, never posts
anything until converted, same three-column discount shape and per-line
internal labour margin (CR-047/CR-050), same whole-document discount
(CR-049), same percentage-only discount type (CR-050). `expectedDeliveryDate`
is informational only — unlike Quotation's `validUntil`, nothing gates on it.

Converts exactly once, to exactly one of a Delivery Challan or an Invoice —
`convertedDeliveryChallanId`/`convertedInvoiceId` are mutually exclusive,
mirroring `Quotation.convertedInvoiceId`. Splitting one order across several
challans/invoices is deliberately **not** built — that is the kind of
lifecycle-state engine CLAUDE.md's proactive-scope section reserves for its
own CR, not a "while I'm in here" addition.

### Delivery Challan

Deliberately **not** a tax document: no GST split, no discount ladder, no
labour margin. Items carry only product, quantity, and an informational
value (qty × unit price at dispatch) — pricing/discount is decided once, at
the eventual invoicing step, same as it always has been.

Unlike Quotation/Sales Order, a challan **does** move stock
(`MovementType.DELIVERY`) — goods have genuinely left the shop. Converting a
challan to an Invoice first reverses that movement (`DELIVERY_REVERSAL`),
then calls `InvoiceService.create()`, which takes the same stock again as a
normal `SALE`. The Invoice module needed **zero** changes: no stock-skip
flag, no special-case branch. Net stock effect is exactly one unit out, and
the ledger honestly shows both the original dispatch and its conversion —
chosen over adding a "skip stock" parameter to `InvoiceService.create()`
specifically to keep that module's blast radius at zero.

### Credit Note

A GST-compliant record of goods returned against an already-issued Invoice.
Deliberately **never edits the original invoice** — `InvoiceServiceImpl.update()`'s
own comment already states the rule: "altering figures the customer has
already settled against is what credit and debit notes are for." Invoice's
`subtotal_paise`/`gst_amount_paise`/`total_paise`/`paid_paise`/`balance_paise`
are untouched by this CR; a Credit Note stands beside the invoice as its own
document. **"What the customer still owes, net of returns" is left as a
reporting-layer question for a future CR** — not solved here by mutating a
settled invoice.

`credit_note_item.invoice_item_id` (NOT NULL) links each returned line to the
exact original `invoice_item` row, **not** to a product id. This is the one
place this CR deliberately front-runs a defect class already seen once:
BUG-FE-021 was exactly a line keyed on product id instead of its own row,
silently corrupting every other line sharing that product. An invoice can
legitimately carry the same product on two lines (two different negotiated
discounts in one sale); keying by `invoice_item_id` makes that ambiguity
impossible from the start rather than fixing it after the fact.

Quantity already credited against a line is capped at that line's original
quantity — summed across every non-cancelled credit note via
`CreditNoteItemRepository.sumCreditedQuantity()`, checked at creation time
(a cross-row aggregate a CHECK constraint cannot see). The credited rate is
the line's **effective** taxable rate (net of its own discount, divided
across its quantity), never the product's gross price — a credit note can
never refund more than was actually collected.

### Idempotency wiring

`create()` on all three, plus Sales Order's two convert actions and Delivery
Challan's convert-to-invoice, accept an optional `Idempotency-Key` request
header and route through `IdempotencyService` (CR-051) when present — the
first real consumers of that service, exactly as CR-051 was built for.

### Permissions

`SALES_ORDER_VIEW/MANAGE`, `DELIVERY_CHALLAN_VIEW/MANAGE`,
`CREDIT_NOTE_VIEW/MANAGE`. Granted to OWNER (via the live-table filter) and
MANAGER in full. ACCOUNTANT gets `SALES_ORDER_VIEW` and
`DELIVERY_CHALLAN_VIEW` (billing context, does not raise them — same footing
as its `QUOTATION_VIEW`-without-`MANAGE` grant) and both `CREDIT_NOTE_VIEW`
and `CREDIT_NOTE_MANAGE` (a financial document, same footing as
`INVOICE_CREATE`). STAFF gets `SALES_ORDER_VIEW/MANAGE` only (counter staff
takes orders the same way it raises quotations and invoices) — dispatch and
returns/credit are withheld, the same footing as `INVOICE_CANCEL`.
`RoleGrantDriftTest` (BUG-LAB-006's regression guard) enforces every new code
is decided for every role, in both the migration and
`TenantRegistrationServiceImpl`'s map.

### Testing

Mockito unit-test coverage per module (mirroring `QuotationServiceImplTest`/
`InvoiceServiceImplTest`'s established shape) rather than new Testcontainers
IT classes: totals/effective-rate arithmetic, status-transition guards,
stock-movement call verification, and the over-return/wrong-invoice/
cancelled-invoice rejection paths for Credit Note. `mvn clean verify`: full
suite green after this change (see RESUME_POINT.md for the exact count).

### Explicitly deferred, not silently built

PDF/print templates for all three document types, and any frontend
page/wizard for any of them — this CR is backend infrastructure only. Also
deferred: a reporting view showing "what a customer owes net of credit
notes" (see the Credit Note section above). None of these were started;
flagging them here so a future session does not assume they exist.

### Effect on the naming registry

New tables `sales_order`, `sales_order_item`, `delivery_challan`,
`delivery_challan_item`, `credit_note`, `credit_note_item`. New enum values
`DocumentType.SALES_ORDER/DELIVERY_CHALLAN/CREDIT_NOTE`,
`MovementType.DELIVERY/DELIVERY_REVERSAL/SALES_RETURN/SALES_RETURN_REVERSAL`.
New packages `com.hardware.erp.salesorder`, `com.hardware.erp.deliverychallan`,
`com.hardware.erp.creditnote`. No renames.

---

## CR-053 phase 1 — Invoice PDF themes (APPLIED 2026-08-31)

### Scope

First of a multi-feature backlog the user asked for from a set of
myBillBook screenshots (invoice PDF themes, reminder settings, named user
roles + activity feed, a GST/margin calculator, and the rest of a premium-
plan feature list). Explicitly sequenced one at a time rather than
attempted together - each is its own multi-day subsystem. This entry
covers only the first.

### What was actually built

A shop-wide default colour/font skin (`Tenant.invoiceTheme`, V38) for the
generated invoice PDF - **not** a photographic background image. No such
asset exists anywhere in this codebase, and the screenshots' floral/
monument backgrounds cannot be honestly reproduced without real design
input; pasting in a placeholder would look cheap, not "themed". Four
skins - `CLASSIC` (default), `MINIMAL`, `BOLD`, `ELEGANT` - implemented as
a small **token recipe** (accent colour, header fill, body font) swapped
into one shared stylesheet, the same architecture CR-034 already proved
out for the frontend's own design styles. The structural/pagination CSS
(table layout, `page-break-inside`, the repeating `<thead>`) is identical
across every theme - that is GST-correctness layout, not decoration, and
must never vary by skin.

`CLASSIC`'s tokens are, deliberately, byte-for-byte what `InvoicePdfService`
always rendered before this CR - an existing tenant that never opens
Settings sees no change at all, proven by `defaultThemeIsClassic()` and by
every pre-existing `InvoicePdfServiceTest` still passing unmodified.

Wired through the existing Settings screen (`TenantSettingsRequest`/
`Response.invoiceTheme`, nullable = "leave unchanged", same convention as
`subscriptionTier`) rather than a new endpoint - one shop-wide default,
not a per-invoice picker, matching how logo/signature/bank details already
work.

### Verified

`mvn clean verify`: 350 unit + 105 integration tests, 0 failures (up from
347/105 - 3 new `InvoicePdfServiceTest` cases: default-is-CLASSIC, all
four themes render distinct tokens while sharing the identical structural
rules, and all four still produce a well-formed `%PDF-` file). **Live-
verified against the real local backend**, not just unit-tested: switched
a real tenant's theme via `PUT /v1/settings` between BOLD/CLASSIC/ELEGANT
and regenerated the same invoice's PDF each time - three different MD5
checksums, confirming genuinely different rendered output, not just a
different response field. Read the BOLD-themed PDF directly: correct
orange title/table-header/tint throughout, GST arithmetic and layout
unchanged from CLASSIC.

### Explicitly deferred

`QuotationPdfService` duplicates the exact same pre-CR-053 hardcoded
colour scheme (confirmed by `grep`) and was **not** touched - theming it
too is a natural, small follow-up, left undone here to keep this change's
diff scoped to what was actually asked for (invoice PDFs, per the
screenshots). The remaining backlog items (reminder settings, named
roles + activity feed, GST calculator, and the rest of the premium-plan
list) are separate, not-yet-started phases of the same request.

### Effect on the naming registry

New column `tenant.invoice_theme`. New enum `InvoiceTheme`. No renames.

---

## CR-053 phase 2 — Registration form scroll fix + auth-page icon polish (APPLIED, frontend only, 2026-08-31)

### The complaint and its actual root cause

"Registration form needs scrolling, feels like old UI." Investigated rather
than guessed: `RegisterPage.tsx`'s `<Card>` already declared `max-w-xl`, but
`AuthLayout.tsx`'s Outlet wrapper hardcoded `max-w-sm` on the div wrapping
every auth page - the wrapper silently clipped Register's own width request
the whole time, forcing six fields plus the full consent section into a
384px column. The `max-w-xl` class was dead CSS, not a mistake in
`RegisterPage` itself.

### The fix

Two independent changes, both needed:

1. **`AuthLayout.tsx`'s wrapper no longer hardcodes a width** - each auth
   page's own `<Card>` now carries `mx-auto w-full max-w-*`, exactly
   matching what CR-034's own frontend design-token philosophy already
   established elsewhere: shared structure, page-level control over its own
   presentation. `LoginPage`, `ForgotPasswordPage`, `ResetPasswordPage` all
   gained an explicit `max-w-sm` to preserve their exact previous width -
   this was implicit before, now it is stated.
2. **`RegisterPage` rebuilt as a 3-step wizard** ("Your shop" /
   "Sign-in details" / "Plan & agreement"), mirroring `SupplierWizard`'s
   already-proven step-indicator + sticky Back/Next pattern (CR-017) rather
   than inventing a new one - same field set, same Zod schema, same
   `tenantRegistrationService.register()` call, only the presentation
   changed. Now that its `max-w-xl` genuinely applies, even the widest step
   (plan selection + the full `ConsentSection`) fits without scrolling on a
   normal viewport.

**Icon-prefixed inputs** added to `LoginForm`, `ForgotPasswordPage`, and
every text field across the new Register wizard (`Building2`/`User`/
`Phone`/`Mail`/`Lock`, the same manual `relative`/`absolute` pattern the
password eye-toggle already used) - this was the literal design reference
the user gave in an earlier session (a "BalanceDesk"-style two-column auth
layout with icon-prefixed fields, recorded in memory at the time) that had
never actually been carried through to the inputs themselves.

**A real, incidental bug fixed along the way, not copied forward:**
`SupplierWizard`'s submit button passes `loading={submitting}` to `Button`
*and* separately renders its own `<Loader2 className="animate-spin" />` -
`Button`'s own `loading` prop already renders that same spinner internally
(`button.tsx`), so `SupplierWizard` has been showing two overlapping
spinners on submit. Not touched here (out of scope, `SupplierWizard` itself
was not part of this request) but the new `RegisterPage` deliberately does
**not** repeat it - flagging it here as a small pre-existing defect for
whoever next touches `SupplierWizard`.

### Explicitly not done

The user's own phrasing ("login show right… register… show left side")
was ambiguous between "fix the scrolling" and "mirror which side the brand
panel sits on between Login and Register." Read as the former (the
concrete, unambiguous complaint) - the brand panel stays on the same side
for both flows, matching the stored design-reference memory's original
choice. If a genuine mirrored layout is wanted, that is a one-line follow-up
change to `AuthLayout`/`RegisterPage`, not done here to avoid guessing wrong
on a stored preference.

### Verified

`tsc -b --force` clean. `vite build` clean (chunk-size warning is
pre-existing and unrelated). **Not performed**: no browser automation tool
is available in this environment (a constraint noted repeatedly elsewhere
in this project's history) - the visual result has not been screenshotted
or clicked through, only typechecked and build-verified. Stated plainly
rather than claimed.

## CR-054 phase 1 — Platform Admin Console: identity & auth foundation (APPLIED, 2026-09-01)

### What

The first phase of the Platform Admin / Developer Admin Console: a second,
structurally separate login system for Hardware ERP *staff* (not shop
owners), with mandatory TOTP MFA and its own audit trail. Every later phase
(tenant management, support tools, security center, ...) builds on this
foundation and was deliberately deferred - see "Explicitly not done" below.

### Why a disjoint system, not a flag on `app_user`

A platform admin is not a tenant. `app_user` is defined entirely in terms
of `tenant_id` (CR-016) - every column, every query, every permission is
scoped to one shop. Bolting "is this a platform staff row" onto that table
would mean every tenant-facing query written from now on needs an extra
"and not a platform admin" guard to stay safe, forever, with no compiler or
test able to catch the one place someone forgets it. A disjoint table with
its own JWT signing key, its own Spring Security filter chain and its own
audit log makes that class of mistake structurally impossible instead of
merely discouraged.

### What was built

**Database** (`V39__platform_admin.sql`): `platform_admin`,
`platform_admin_backup_code`, `platform_admin_refresh_token`,
`platform_audit_log` - none carry `tenant_id`, and `platform_audit_log`
also holds no foreign key on `platform_admin_id` (mirrors
`security_audit_log.user_id` exactly): the audit write runs in its own
`REQUIRES_NEW` transaction, which for a "platform admin created" event
races the still-uncommitted insert of the row it is describing. An enforced
FK there was tried first and rejected the audit row outright with a
constraint violation - caught by `PlatformAdminAuthControllerIT`, not by
inspection.

**Backend** (`com.hardware.erp.platformadmin`): a full mirror of the
tenant auth stack, kept deliberately duplicate rather than shared -
`PlatformAdminJwtService`/`PlatformAdminJwtProperties` (own secret, own
issuer, `app.platform-admin.jwt.*`), `PlatformAdminUserDetails`/`Service`,
`PlatformAdminAuthenticationFilter`, a second `@Order(0)`
`PlatformAdminSecurityConfig` with `.securityMatcher("/v1/platform-admin/**")`
(the existing tenant `SecurityConfig.filterChain` moved to `@Order(1)` so it
never shadows it), a dedicated `PlatformAdminRateLimitFilter` (per-IP only -
see its javadoc for why a per-identifier bucket was skipped for Phase 1),
and `PlatformAdminBootstrapInitializer` (mirrors `BootstrapOwnerInitializer`,
gated by `PLATFORM_ADMIN_BOOTSTRAP_ENABLED`, always leaves `mfaEnabled=false`
since a TOTP secret cannot be bootstrapped from an env var).

Login is always exactly two factors, with no opt-out: `POST
/v1/platform-admin/auth/login` checks the password and returns a short-lived
`mfaToken` (a JWT with a `purpose` claim, `LOGIN` or `ENROLL`), never a
session. An unenrolled account is routed through `/mfa/enroll` (generates a
TOTP secret + QR, stored encrypted but `mfaEnabled` stays false) then
`/mfa/enroll/confirm` (proves the code was actually captured, then issues
the first real session plus 10 one-time backup codes). `TotpService` is a
hand-rolled RFC 6238 implementation (HMAC-SHA1 via `javax.crypto.Mac`, plus
a from-scratch Base32 codec) rather than a new Maven dependency. The TOTP
secret reuses `FieldEncryptor`/`TotpSecretConverter` (same AES-256-GCM
pattern as `BankAccountNumberConverter`, CR-018); the QR reuses
`QrCodeGenerator`, widened from UPI-only to a generic `pngBytes(String)`.
Refresh-token rotation and reuse detection (`PlatformAdminAuthService.refresh`)
is a line-for-line mirror of `AuthServiceImpl.refresh`, including the
`noRollbackFor = AuthException.class` fix from BUG-AUTH-009.

`PlatformAdminUserController` (`POST`/`GET /v1/platform-admin/admins`,
`@PreAuthorize("hasAuthority('PLATFORM_ADMIN_MANAGE')")`) is intentionally
the only business endpoint in this phase - just enough to prove the 7-role
RBAC model (`PlatformAdminRole` → `PlatformPermission`, both compile-time
enums, no database permission table: the 7 roles are fixed by the spec, not
tenant-configurable, so a `PermissionCode`-style DB-backed system would be
overhead with no one able to use the extra flexibility).

**Frontend** (`frontend/src/modules/platform-admin`, `frontend/src/services
/platformAdminApiClient.ts` + `platformAdminTokenStorage.ts`): a second axios
instance and a second in-memory token store, never shared with
`services/apiClient.ts` - same reasoning as the backend's two filter chains.
Both the access token and the refresh token live in memory only for this
console (no HttpOnly cookie transport was built for it in Phase 1), so a
page reload signs a platform admin out; accepted deliberately rather than
building the cookie service now. Four pages
(`PlatformAdminLoginPage`/`MfaVerifyPage`/`MfaEnrollPage`/`DashboardPage`)
mounted at `/platform-admin/*`, wrapped in their own `PlatformAdminAuthProvider`
at the route level in `routes/index.tsx` - never inside the tenant
`AuthProvider`, `AuthLayout` or `AppLayout`. The dashboard shows only the
signed-in admin's own identity/role/permissions and says plainly that
tenant management, support tools, security center and analytics are "not
available yet" - Section 37 of the spec forbids fabricated metrics, and
Phase 1 computes none.

### Explicitly not done (proposed phases, awaiting selection - see the
`AskUserQuestion` phase-ordering answer that started this CR)

Tenant management (list/detail/status control), support-session
impersonation, support tickets, announcements, subscription/plan/feature
control, system health/error/incident monitoring, security center, global
platform audit *log viewer* (the log itself is written starting now),
developer tools, backup management, maintenance mode, feature flags, tenant
usage analytics, the tenant data-access-request workflow, synthetic demo
data (3 tenants × 5 roles), the 20-step admin dashboard test scenario, and
`docs/PLATFORM_ADMIN_GUIDE.md`/`docs/PLATFORM_ADMIN_SECURITY.md` (both
require enough of the console to exist to document truthfully - premature
before Phase 2).

### Verified

`mvn clean compile` and `mvn clean verify` both clean (full existing suite,
298 unit + 100 Testcontainers integration tests, unaffected). New
`PlatformAdminAuthControllerIT` (10 tests, real PostgreSQL via
Testcontainers, real filter chain): full enroll→confirm→session flow with a
genuine RFC 6238 code (`TotpService.currentCode`, added for this), login
with an already-enrolled account, wrong-code rejection, backup-code
single-use, login enumeration-resistance, refresh rotation + reuse
detection, SUPER_ADMIN-only RBAC (403 for another role), and - the
guarantee the spec cared about most - a tenant access token is refused on
`/v1/platform-admin/**` and a platform-admin access token is refused on
`/v1/auth/**`, both with a real token exchanged through the real login flow
of the other side, not a forged claim. `tsc -b --force` and `vite build`
both clean. **Not performed**: no browser automation tool is available in
this environment - the frontend has not been clicked through, only
typechecked and build-verified.

## CR-053 backlog items 2-7 (APPLIED, 2026-09-02)

Six more items from the myBillBook backlog (`RESUME_POINT.md`'s own queue),
built one after another following the same rhythm as every other CR this
session - migration → entity → DTO → service → controller → frontend →
`mvn clean compile`/`tsc -b --force` after each, full `mvn clean verify`
+ `vite build` as a final checkpoint. Migrations V41-V43.

**Real bug found and fixed along the way, not part of the ask**:
`AdditionalSettingsCard` (built last turn, CR-053 backlog item 1) was
rendered on the **read-only** Settings view but missing entirely from the
**edit-mode** view - a real, user-visible instance of "I built the option
but it doesn't show up," caught while extending that same card for this
round's toggles. Fixed by adding it (and the new `TdsTcsCard`) to both
render paths.

**Item 2 - Tally export** (`backend/export` package, no new tables).
`GET /v1/exports/tally?fromDate&toDate` (`REPORT_FINANCIAL`) returns a
Tally-importable XML envelope: party ledgers (Sundry Debtors/Creditors),
stock-item masters, and **ledger-level** Sales/Purchase vouchers (party +
Sales or Purchase Account + CGST/SGST/IGST) - deliberately not "Invoice
mode" vouchers with per-line inventory allocations; see
`TallyXmlBuilder`'s own javadoc for the exact scope and for why the
debit/credit sign convention has not been verified against real Tally
software (none exists in this environment - it matches the convention in
Tally's own published samples, and `TallyXmlBuilderTest` proves every
voucher's ledger entries sum to zero, which is the one thing a real
import would reject outright if wrong).

**Item 3 - TDS/TCS settings**. Six new `tenant` columns (V41), all
**informational only** - the computed figure is shown on the Purchase/
Invoice detail page, never added to or subtracted from that document's
own stored `total_paise`/`balance_paise`. Folding a real statutory tax
deduction into core financial totals is its own separately-reviewed
change, not a drive-by extension of a settings toggle - stated on the
Settings card itself, not just in code.

**Item 4 - e-Invoice (IRN) UI shell**. One `tenant.einvoice_enabled`
toggle (V42), no IRN/acknowledgement columns anywhere - there is nothing
to store until a real GSP/NIC integration exists. When on, Invoice detail
shows a review card (document/buyer/total already on the invoice) and a
permanently-disabled "Generate e-Invoice (IRN)" button with an honest
"needs a GSP/NIC account" message, matching the user's own answer to the
original scoping question.

**Item 5 - reminder settings, 2 of 5 reminder types**. `tenant.payment_due_reminder_enabled`
and `tenant.low_stock_alert_enabled` (V43), plus a genuinely new
`@Scheduled` job - `ReminderSchedulerService`, cron `0 0 8 * * *`
Asia/Kolkata, same shape as the existing `TokenCleanupJob`. Iterates
active tenants with a reminder on, and for each, logs a summary SMS (via
the existing `SmsWhatsAppNotificationProvider` stub - no real SMS account
configured, exactly like every other SMS/WhatsApp touchpoint in this
codebase) to the tenant's own contact number. SMS-on-transaction, a daily
sales-summary digest, and WhatsApp-specific alerts are deferred - same
"one bounded piece at a time" reasoning as everything else in this list.

**Item 6 - named roles + activity feed**. `RoleForm`'s existing free-text
"Display name" field already let a system role be renamed (an audit
finding, not new work - `RoleServiceImpl.update()` only ever locked
`code` for a system role, never `name`) - added a `<datalist>` of common
labels (Partner, Salesman, Stock Manager, Delivery Boy, CA, Cashier,
Godown Staff) as suggestions, not a fixed enum. Separately, `GET
/v1/users/{id}/activity` (new, `USER_VIEW`) surfaces `activity_log` rows
for one user on the User Management page's new "Activity" action.
**Security finding recorded, not fixed**: `activity_log` carries no
`tenant_id` of its own (pre-existing, not introduced here - see
`SECURITY_REGISTRY.md`). This endpoint is safe only because it verifies
the target user belongs to the caller's own tenant *before* it is ever
used to filter `activity_log` by `user_id` - `ActivityLogRepository`'s
own javadoc warns against building a second endpoint that skips that
check.

**Item 7 - GST/margin calculator**. `/tools/gst-calculator`, no
permission gate (pure client-side arithmetic, no tenant data touched).
Cost price + profit margin % + GST slab → selling price, GST amount,
final price, and a donut breakdown of what the final price is made of.

**Item 8 - the rest of the premium-plan list** (barcode/warehouse,
scan-to-invoice, online store, foreign-currency invoicing, "Add your CA"
access, GSTR JSON export, remove-branding toggle, recover deleted
invoices, bulk-edit items) is **explicitly not started**. Per this
session's own earlier note, several of these overlap with Master Prompt
phases the user has not yet picked (barcode/warehouse in particular) -
resolving that overlap needs the user's own answer, not a guess, before
any of item 8 begins.

**Verified**: `mvn clean compile` after every file, full `mvn clean
verify` clean (existing suite unaffected, plus the new
`TallyXmlBuilderTest`). `tsc -b --force` and `vite build` both clean.
Live-verified: the local dev backend (real PostgreSQL, real Flyway) boots
cleanly through V41-V43. **Not performed**: no browser automation tool in
this environment - every page built this round has been typechecked and
build-verified, and the backend endpoints compile/test-pass, but none of
it has been clicked through in a real browser.
