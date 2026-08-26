# AUDIT GAP REPORT

**Written:** 2026-08-24, in response to a combined "continue the full ERP
roadmap" master prompt (Purchase, Supplier Bill Import/OCR, Finance Ledger,
Labour, Reports, Notifications v2, AI v2, SAML) received the same day as a
customer demo scheduled for this evening.

Verified against the actual repository (migrations, packages, entities,
frontend modules) as of this session, not against what any registry file
*claims*. Where a registry disagreed with the code, the code won.

---

## A. Already completed (verified against code, not docs)

Confirmed present via `ls`/`grep` against the real backend/frontend, not
assumed from `RESUME_POINT.md`:

- **Auth & Users** — JWT + opaque refresh, RBAC, multi-tenancy (`tenant_id`
  on every tenant-owned table), rate limiting. 20 applied migrations
  (`V1`-`V20`), backend `auth` package present and complete.
- **Supplier, Category, Brand, Product, Inventory** (Modules 2-4) — full
  CRUD both sides, `stock`/`stock_movement` architecture in place
  (`inventory` package, `MovementType` enum: `INITIAL`/`ADJUSTMENT`/
  `SALE`/`SALE_REVERSAL` — see gap C1 below).
- **Customer, Quotation, Invoice, Payment, Coupons** — full CRUD, GST/PDF
  generation, coupon-aware invoicing, Customer 360 (tabs, quick-actions,
  Repeat, credit-limit warning, inline product creation).
- **Tenant self-registration, Notification abstraction (email real,
  SMS/WhatsApp logging-stub), Subscription tiers + EntitlementService, AI
  abstraction (`ChatCompletionClient`, Anthropic + Gemini providers, no
  live key configured in this environment)** — all present as packages/
  entities, matching what `RESUME_POINT.md` already claimed.
- **Project Management (CR-029)** — `project` package exists, `V18`/`V19`
  migrations applied, entities for `project`/`project_material`/
  `project_expense`/`project_payment`/`work_type` all present. **This
  resolves the "contradiction" the master prompt flagged**: the older
  status doc's "Start Phase 6" line simply predates CR-029 shipping later
  the same day it was written — the code confirms Project Management is
  real and built, not a rebuild candidate.
- **CR-033/CR-034 theme system** — 8→11 colour themes, 7 design styles,
  command palette, dashboard trend data, per-user-scoped appearance
  preferences. Live-verified this session, most recent work.

## B. Partially completed

- **Project Management ↔ Inventory**: the ledger/entity side is real and
  correct, but `grep -rl "StockService" project/` returns **zero files** -
  confirmed, not assumed. Adding a project material today does not touch
  `stock_movement` at all. This is the one concretely-scoped, already-known
  gap the master prompt itself calls out (§5/§29).
- **AI Assistant**: real code, real provider abstraction, but no
  `ANTHROPIC_API_KEY`/`GEMINI_API_KEY` configured in this environment - it
  correctly refuses rather than faking an answer. Business-data tools
  beyond CR-027's original set (sales/low-stock/customer-balance/invoice
  search) not added.
- **Notifications**: real email, but SMS/WhatsApp is a logging stub only -
  `NotificationProvider` interface exists and is genuinely swappable, no
  real provider wired in.
- **Security Audit Log**: shows IP/user-agent/request-id/resource, but not
  actor *role at the time of the event* (needs a new column + 5 call
  sites, documented and deferred since CR-030).

## C. Missing entirely (verified by absence, not inferred)

1. **Purchase module** - `find src -iname "*purchase*"` in the backend
   returns **nothing**. No package, no entity, no migration, no
   controller. `PURCHASE_VIEW`/`PURCHASE_MANAGE` permission codes exist
   (seeded speculatively in `V1`) but are wired to nothing.
2. **Supplier Bill Import / OCR extraction** - no `DocumentExtractionService`
   or equivalent, no PDF/Excel/CSV/image parsing dependency in `pom.xml`,
   no upload-preview-confirm workflow anywhere in the frontend.
3. **Finance ledger (`financial_transaction`)** - the string appears
   exactly once in the entire codebase, inside a *comment* in
   `V18__project_management.sql` documenting it as deferred future work.
   No table, no entity, no cash/bank/cheque balance anywhere.
4. **Supplier Payables** - correctly blocked on Purchase (§4.4 of the old
   status doc's own reasoning still holds).
5. **Labour / Team / Attendance** - no package. Only
   `ProjectExpenseCategory.LABOUR` exists, a manual expense line, not a
   real employee/attendance/wage system.
6. **Reports module** - no dedicated `report` package or endpoints beyond
   the existing Dashboard's sales summary + low-stock list.
7. **Payment reminders / "pay quickly" supplier suggestions** - not started.
8. **SAML/SSO** - not started, correctly deferred pending real IdP
   credentials per the master prompt's own instruction.
9. **Bulk supplier-bill import testing (100/1000 records, multiple file
   types)** - not applicable yet; nothing exists to test.

## D. Broken

Nothing found broken in currently-shipped modules during this audit pass
(no new code was written to introduce a regression). The backend test
suite's last full run this session: 203/205, the same 2 pre-existing
`BUG-ENV-002` Testcontainers/Docker-discovery failures documented since
early in the project, unrelated to any application code.

## E. Documentation drift

`MASTER_PROJECT_STATUS.md` (2026-08-23) is now itself one day stale - it
predates CR-030 through CR-034 (Customer 360 completion, entitlement
limits, subscription coupons, and the two theme-system rounds). Its
Section 1 "documentation drift" findings against `PROJECT_REGISTRY.md`/
`FEATURE_REGISTRY.md`/`MODULE_DEPENDENCY_MAP.md`/`SECURITY_REGISTRY.md`/
`VERSION.md` still stand and have not been corrected since - those five
files remain stale from roughly CR-020/021, now three weeks further
behind. Not touched in this pass; flagged, not silently left unexplained.

## F. Security risks

None newly found. Multi-tenancy enforcement (`SecurityUtils
.requireCurrentTenantId()`, never trusting client-supplied tenant data)
was re-confirmed present at every repository method touched this session.
No uploaded-file-handling code exists yet to have a file-security risk in
the first place (see C2) - this becomes a real review item the moment
Supplier Bill Import is actually built, not before.

## G. Data-integrity risks

The one live one: **project material consumption vs. stock** (B1/C1
combined) - a project can record materials used without stock ever
reflecting it, so `stock.quantity_on_hand` can silently overstate what's
actually on the shelf for any tenant using Project Management today. This
is real and already flagged in every prior status doc; not new.

## H. UX gaps

None found beyond what's already tracked (Security Audit Log actor role,
SMS/WhatsApp still a stub).

## I. Missing tests

Matches C exactly - there is no code to test yet for Purchase/OCR/Finance/
Labour/Reports/SAML. `ProjectMaterialServiceImplTest` (if it exists) would
need a regression test the moment stock integration is added.

## J. Missing integrations

Real Gemini/Anthropic API key, real SMS/WhatsApp provider credentials,
real SAML IdP - all correctly gated behind graceful-degradation, all
genuinely blocked on the owner supplying real external credentials, not
an engineering gap.

## K. Features required by another requested feature, not yet flagged elsewhere

- Supplier Bill Import (§5-22 of the master prompt) **cannot be built
  without Purchase existing first** - the prompt's own §23 already says
  this, and the code confirms Purchase doesn't exist. Building import
  before Purchase would mean exactly the "disconnected duplicate
  supplier-bill table" the prompt explicitly forbids in §13/§23.
- Cash/Bank/Cheque dashboard (§25) needs the Finance ledger (§8) to exist
  first - same dependency chain.
- "Pay quickly" supplier suggestion (§26/§34) needs both Purchase (for
  outstanding bills) and real sales-velocity data (which already exists,
  via `invoice_item`) - buildable only after Purchase.

---

## Bottom line, stated plainly for tonight's decision

**Purchase, Supplier Bill Import/OCR, the Finance ledger, Labour/
Attendance, Reports, Notifications v2, AI v2, and SAML are 100% unbuilt -
not partially done, not stubbed, genuinely zero code.** Each is a real,
multi-day-to-multi-week backend+frontend+migration+test+browser-verification
effort on its own; several are hard-dependency-chained (Purchase before
Import before Payables before "pay quickly"; Finance ledger before Cash/
Bank/Cheque). Building all of this to the master prompt's own acceptance
bar (§60: full backend, migration, tenant isolation, RBAC, validation,
frontend, responsive UI, unit + integration tests, browser-verified, audit
logging, docs, zero fake completion) inside the hours remaining before an
evening customer demo is not realistic to do safely - seeing this recommendation and the plan in the next message.
