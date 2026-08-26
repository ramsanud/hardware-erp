# RESUME POINT

**Updated:** 2026-08-26 (CR-041: per-tenant document number allocator, replacing the MAX+1 race in ten call sites. Prerequisite for the CR-042/043/044 device + offline + push work. Two environment blockers fixed along the way: Testcontainers could not reach Docker Engine 29, and PermissionCodeConsistencyTest had been failing since the Labour module shipped.)

---

## Next file to work on

**CR-042, increment 2 — thermal print rendering.** Start with
`backend/src/main/java/com/hardware/erp/device/escpos/EscPosBuilder.java`
and the raster receipt renderer. The plan, decisions and phase breakdown live
in the "Shop Counter Architecture" document produced 2026-08-26.

Approved decisions from that plan, do not re-litigate them:

- Offline stock conflict → **strict reject into CONFLICT**; the server never
  lets stock go invalid. The user resolves on a conflict screen.
- Build order → numbering fix (done) → thermal print → Bluetooth/USB →
  idempotency → PWA offline reads → offline writes → push.
- Offline write scope → invoice, payment, **quotation and customer drafts**,
  all gated behind a new `OFFLINE_TRANSACT` permission.

---

## CR-041: per-tenant document number allocator (DONE 2026-08-26)

Every generated code — `INV-`, `QUO-`, `PUR-`, `CUS-`, `SUP-`, `PRD-`,
`CAT-`, `BRD-`, `PRJ-` — was allocated as
`findHighestGeneratedCodeNumber(tenantId) + 1`. Read-then-write, no lock,
ten call sites across eight modules.

No duplicate was ever stored (every one of those tables already has
`UNIQUE (tenant_id, <code>)`), but the losing concurrent request died on the
constraint and its document was lost. Under CR-043's offline sync, replaying
a queued batch would have hit this on nearly every run — which is why it was
fixed first.

**New:** `document_sequence` (V29), `DocumentType`, `DocumentSequenceService`
(`SELECT ... FOR UPDATE`, `Propagation.MANDATORY` so a rollback does not
burn a number — GST needs a consecutive serial). `DocumentType` now owns each
prefix and width in one place, replacing nine copy-pasted constant pairs. The
nine dead `findHighestGeneratedCodeNumber` repository methods were removed so
the pattern cannot be pasted an eleventh time.

**Second defect, found by the new test rather than by review:** Flyway runs
V29 before the V900–V903 seed, so in dev and test the backfill read empty
tables and seeded every counter at 1 while the seed then inserted
`SUP-0001`..`SUP-0013` on top. Fixed by `V904__seed_document_sequence.sql`.
Production was never affected.

### Two environment blockers fixed in the same pass

1. **Testcontainers could not reach Docker at all on this machine.** Docker
   Engine 29 rejects the API version docker-java negotiates by default
   (advertises 1.32, daemon minimum is 1.40), so *every* existing integration
   test failed with "Could not find a valid Docker environment" — this was
   pre-existing and unrelated to CR-041.
   `backend/src/test/resources/docker-java.properties` pins
   `api.version=1.44`. Remove it when moving to Testcontainers 2.x.
2. **`PermissionCodeConsistencyTest.everyPermissionHasAModule` had been
   failing since V25.** Its hardcoded module whitelist never gained
   `PROJECT` (V18) or `LABOUR` (V25). Also pre-existing; never observed
   because the suite could not run here.

### Still not runnable on this machine

`python3` is not installed, so `registry/static_check.py` and
`registry/check_registry.py` were **not executed**. They are unverified for
this change, not passing.

---

## CR-038: sign-in security check + health-check fix

**Turnstile CAPTCHA on login.** Server-side verification happens *before*
authentication, so the endpoint cannot be used to probe passwords while
failing the challenge. `GET /v1/auth/captcha-config` is public so the login
page can decide whether to render the widget; the widget loads its script on
demand, so an install that never configures CAPTCHA makes no third-party
request at all.

**Off by default**, and treated as off whenever either key is blank — a
CAPTCHA that hard-fails on missing keys locks everyone out of a working
system, which is worse than the automated sign-ins it prevents. The whole
fail-safe contract is unit-tested (5 tests) *and* was verified live against
Cloudflare's published test keys:

| Situation | Verified result |
|---|---|
| Disabled (default) | `enabled:false`, login works with no token — HTTP 200 |
| Enabled, no token | HTTP 400 `CAPTCHA_FAILED` |
| Enabled, always-pass secret | HTTP 200 |
| Enabled, always-fail secret | HTTP 400 — **correct credentials still did not sign in** |
| Cloudflare unreachable | 503 `CAPTCHA_UNAVAILABLE` — fails closed |

**BUG-ENV-003 (HIGH).** `/api/actuator/health` returned 503 on a completely
healthy application because Boot folds the SMTP health indicator into the
aggregate status and the mail password is wrong. `docs/DEPLOYMENT_FREE_HOSTING.md`
tells the operator to point Render's health check at that path, and Render
restarts anything answering non-2xx — so a bad mail password would have
restart-looped a good deploy with nothing in the logs naming mail. Mail
indicator disabled; `POST /v1/settings/mail/test` (`SETTINGS_MANAGE`) added
so mail failures stay discoverable, returning the server's own rejection
text. Verified: 503 → `200 {"status":"UP"}` with the credentials left equally
broken.

**Email OTP deliberately NOT built.** The SMTP credentials are rejected by
Gmail right now. Shipping OTP on unproven email locks every user out of their
own account on the next sign-in, and nothing on screen would say why. The
agreed order is: fix SMTP → confirm a real test email arrives via the new
endpoint → then build OTP on something proven.

**Still outstanding from the same request**: export (CSV/Excel/PDF, row-count
choice, optional password protection) and import for brand/category/customer/
project; reading password-protected digital PDFs (PDFBox 2.0.24 is already on
the classpath). OCR for scanned/photographed bills was scoped out — it needs
its own CR and a provider decision.

**Verified**: backend `mvn -o test` **271/271** (up from 266; +5 captcha
tests; same 2 pre-existing BUG-ENV-002 Docker failures). Frontend
`tsc -b --force` + `vite build` clean. **Not verified**: the Turnstile widget
has not been seen rendering in a browser — no browser automation in this
environment. The server half is fully proven; the widget is build-verified
only.

---

**Updated:** 2026-08-25 (CR-037 continued: a role/permission report request surfaced BUG-LAB-006 - every shop registered after V25 had MANAGER/ACCOUNTANT roles with no labour access, because the default grants live in two places that drifted. Fixed + backfilled + guarded by a drift test. Shared Dialog/mobile-nav UI defects (BUG-UI-001) fixed too.)

---

## CR-037 part 2: permission drift + shared UI fixes

**BUG-LAB-006 (HIGH)** - found while assembling a role→pages report, not
from a bug report. The default role grants exist in **two independent
sources of truth**: the migrations (which UPDATE the `role` rows existing
at migration time) and a hardcoded `ROLE_PERMISSIONS` map in
`TenantRegistrationServiceImpl` (which every newly registered shop is
built from). V25 updated only the first, so tenants 1-6 had
`LABOUR_VIEW`/`LABOUR_MANAGE` on MANAGER/ACCOUNTANT and tenants 7-8 -
registered after V25 - had neither. OWNER hid it completely, because that
service assigns OWNER from the live `permission` table rather than the
map, and OWNER is the account anyone testing a new shop signs in as.

Fixed in three parts: the map updated; `V27` backfills already-created
shops (anti-join, safe to re-run); and **`RoleGrantDriftTest`** reflects
over every constant in `PermissionCode` and fails the build unless each is
either granted to a role or listed in that role's explicit
`WITHHELD_FROM_*` set with a reason - so a future permission code cannot
be added without deciding each default role's access.

**BUG-UI-001 (MEDIUM)** - three shared-UI defects:
- The mobile/tablet sidebar was a centred `<Dialog>` forced to the left
  edge, so it zoom-faded open from the middle instead of sliding in, and
  brand/footer scrolled away with the nav list. Replaced with a new
  `ui/sheet.tsx` drawer primitive (same Radix root, real slide animation,
  `h-dvh`, pinned brand/footer).
- Dialogs had fade-only animation. Restored the canonical fade + zoom +
  the slide pair that cancels the centring transform.
- **A dialog taller than the viewport hid its own Save/Cancel row.**
  `DialogContent` was itself the scroll container, so header, footer and
  the close button all scrolled out of reach. Scroll moved to an inner
  wrapper; header/footer now sticky, painted with a new
  `.surface-sticky-bar` derived from the `--card` token (not a hardcoded
  `bg-card`, which would paste an opaque band across the glass themes).
- Sidebar rows get a 44px touch target below `lg`, scoped by media query
  so the desktop rail keeps its density.

**Verified**: backend `mvn -o test` **266/266** (up from 261; +5 drift
tests; same 2 pre-existing BUG-ENV-002 Docker failures). Frontend
`tsc -b --force` + `vite build` clean. **Live-verified**: after V27 zero
MANAGER/ACCOUNTANT roles lack the grant, and a brand-new registered shop
gets OWNER/MANAGER/ACCOUNTANT with both labour codes and STAFF with
neither. **Not verified**: the UI fixes have not been seen in a browser -
still no browser automation in this environment. That is the main
residual risk outstanding.

---

## CR-037: proactive scope policy + Labour module audit

**Process change first.** `CLAUDE.md` gained a "Proactive scope" section:
think through the full real-world workflow across affected roles before
writing code; treat one reported symptom as a signal to inspect the whole
surrounding module; always report adjacent gaps; always fix same-root-cause
defects in the same commit. Deliberately bounded so it does not contradict
the existing "do not overengineer" rule - large new subsystems noticed as
gaps (OCR/document extraction, synonym/fuzzy search, e-signatures,
optimistic locking, lifecycle state engines) are **proposed as their own CR,
never built unprompted**. The section also lists which concerns are already
solved project-wide (permission gating, tenant isolation, audit trail, soft
delete, inline entity creation, import preview→confirm) so future work
extends the existing mechanism instead of rebuilding it.

**Then applied it to the module just built.** A full audit of Labour
Monitor found 4 real defects, none of which had been reported:

1. **BUG-LAB-002 (HIGH)** - `AttendancePage` defaulted every row to
   PRESENT and submitted every active worker on Save. Opening any date and
   clicking Save fabricated a full day's wage for the entire crew, feeding
   straight into the wage summary and project labour cost. Fixed: unmarked
   is now a real state (`null`), only marked rows are submitted, the Save
   button shows the count and disables at zero, and clicking a selected
   status clears it.
2. **BUG-LAB-003** - attendance and payments accepted **future dates**;
   marking next month booked wages for work nobody had done. Fixed with
   `@PastOrPresent` on both.
3. **BUG-LAB-004** - the same worker twice in one batch returned two
   response elements sharing one id with contradicting statuses (the first
   a stale pre-overwrite snapshot) and logged a spurious create-then-correct
   pair. Fixed by collapsing entries per worker, last wins.
4. **BUG-LAB-005** - a mistyped payment (₹5,000 for ₹500) could never be
   corrected in-app, and duplicate workers were trivially creatable. Fixed
   with `V26__worker_payment_status.sql` + a cancel endpoint mirroring
   Expense's soft-cancel exactly, and duplicate detection keyed on **mobile
   number, not name** (two workers called "Ramesh" on one crew is ordinary
   and must stay allowed).

Also fixed without being bugs: the deactivated-worker row vanishing from
the Attendance page on historical dates, no unsaved-changes warning when
changing the date, project assignment unreachable below 640px (the phone a
site supervisor actually uses), an ad-hoc loading state instead of
`TableSkeleton`, a silently-swallowed projects-load failure, and a
"Balance owed: ₹-500.00" tile that now reads "Paid in advance".

**Verified**: backend `mvn -o test` **261/261** (up from 255; 6 new
regression tests; same 2 pre-existing BUG-ENV-002 Docker failures).
Frontend `tsc -b --force` and `vite build` both clean. **Live-verified via
curl**: future dates rejected 400 on both endpoints; duplicate batch entry
collapses to one row; duplicate mobile rejected 409 while same-name is
allowed 201; the ₹5,000 typo cancelled and the paid total restored ₹5,200 →
₹200 with the row surviving as CANCELLED; double-cancel 422; STAFF 403;
second tenant 404. **Not performed**: live browser click-through of the
corrected Attendance page - still no browser automation tool in this
environment, so the frontend fixes are verified by typecheck, build and
code review only, not by clicking them.

---

## CR-036 phase 4: Labour Monitor

Fourth of five sequenced phases from the same combined request (see the
phase 1-2 entry below for the full original request). User's answer for
Labour Monitor scoping: all three of worker directory + daily attendance,
wage/payroll calculation, and per-project labour-cost assignment. A
follow-up question settled the attendance model: simple daily
present/absent/half-day marking, no clock-in/out, no GPS.

**Built** (V25, new `labour` package - entity/dto/repository/service/
mapper/controller, mirroring Supplier/Expense/Purchase's own established
conventions):
- `Worker` - name, mobile, role_title (plain free text, not a lookup
  table like `work_type` - didn't need the same admin-editable-catalogue
  treatment), daily_rate_paise, status ACTIVE/INACTIVE (soft-deleted,
  same as Supplier/Customer, since attendance/payment history reference a
  worker forever).
- `WorkerAttendance` - one row per worker per day (`UNIQUE (tenant_id,
  worker_id, attendance_date)` - re-marking the same worker/date corrects
  the row in place, never duplicates), PRESENT/ABSENT/HALF_DAY, an
  optional `project_id` for cost attribution. Wage is **never stored** -
  always computed live as `daily_rate_paise x ratio` using the worker's
  *current* rate, so a later rate correction is reflected everywhere
  instead of going stale.
- `WorkerPayment` - mirrors `project_payment`'s shape; a
  `/workers/{id}/wage-summary` endpoint returns earned vs paid vs balance
  owed for an optional date range.
- **Project integration**: `GET /v1/projects/{id}` (existing endpoint)
  gained a new `totalLabourCostDisplay` field, live-summed from
  attendance x rate for that project - deliberately additive-only, never
  folded into the existing `totalCostDisplay`/`netProfitDisplay` math, so
  it never silently changes a profit figure an owner already relies on.
  Same "standalone, not merged" principle as Expense Tracker vs
  `project_expense` (phase 3).
- Frontend: new "Workers" and "Attendance" pages under the existing
  "Projects" sidebar section (`UserCheck`/`CalendarCheck` icons); a
  Worker detail page with a wage-summary card, payment history and
  attendance history; Attendance page marks the whole active-worker crew
  for one day in a single batch call, pre-filling existing marks so
  corrections are visible before saving.
- **New permission codes** `LABOUR_VIEW`/`LABOUR_MANAGE` - unlike every
  prior CR-036 phase, these were NOT pre-seeded in V1 (confirmed via grep
  first), so this is the first phase that genuinely adds new permission
  rows. Granted to OWNER/MANAGER/ACCOUNTANT, withheld from STAFF (same
  reasoning as `PRODUCT_VIEW_COST`).

**Two real bugs found and fixed during live testing** (full detail in
`BUG_REGISTRY.md`):
1. Migration column-name mistake - `V25` first tried to INSERT into
   `permission (category, sort_order, ...)`, but the real columns
   (confirmed by reading V1's own `CREATE TABLE permission`) are
   `module_code`/`display_order`. Caught immediately on first backend
   startup attempt (Flyway failed, transaction rolled back cleanly, no
   partial state) - fixed before the DB was ever touched.
2. **BUG-LAB-001** - after fixing the column names, V25 applied
   successfully but OWNER never received `LABOUR_VIEW`/`LABOUR_MANAGE`
   (login response's permission list didn't include either). Root cause:
   OWNER's blanket grant in `V1` is a one-time `CROSS JOIN`, evaluated
   only against whatever permission rows existed at V1's own seed time -
   it does not retroactively cover codes a later migration adds. Every
   prior CR-036 phase avoided this because its permission codes were
   pre-seeded in V1 itself; Labour Monitor is the first to add genuinely
   new ones. Fixed by adding an explicit OWNER grant to V25, matching the
   precedent already set by V18 (Project). Because V25 had already
   applied once (with the bug) to the local dev database before this was
   caught, fixing it required manually rolling back V25's effects
   locally (drop the 3 new tables, delete the `LABOUR_*` permission and
   role_permission rows, delete the `flyway_schema_history` row) rather
   than editing an already-applied migration in place - safe here only
   because this was solo, uncommitted, local-only work with no other
   environment depending on the old checksum.

**Verified**: backend `mvn -o test` 255/255 (up from 242 - same 2
pre-existing BUG-ENV-002 Testcontainers/Docker-discovery failures,
unrelated; 13 new tests across `WorkerServiceImplTest`,
`AttendanceServiceImplTest`, `WorkerPaymentServiceImplTest`). Frontend
`tsc -b --force`/`vite build` both clean. **Live-verified via curl end to
end**: created a worker, marked attendance PRESENT then corrected it to
HALF_DAY for the same worker/date (confirmed one row, not two, and the
wage halved correctly), recorded a payment, confirmed the wage-summary's
earned/paid/balance math, confirmed a project's `totalLabourCostDisplay`
updated after attendance was tied to it. **RBAC verified**: a real STAFF
user got 403 on `GET`/`POST /v1/workers`. **Cross-tenant isolation
verified**: registered a real disposable second tenant via
`/v1/tenants/register`, confirmed its owner got a 404 (not tenant 1's
data) reading tenant 1's worker, an empty list on `GET /v1/workers`, and
a 404 attempting to mark attendance for tenant 1's worker id.
**Not performed**: live browser (Playwright) click-through of the new
pages - no browser automation tool was available in this session/
environment. Stated here rather than silently skipped, per this
project's "never claim a build passes without running it" rule.

---

## CR-036 phase 3: standalone expense ledger

Third of five sequenced phases from the same combined request (see the
phase 1-2 entry directly below for the full request context and why
Expense/Labour needed scoping questions before building). User's answer
for Expense Tracker: a **new standalone ledger**, Project's own existing
`project_expense` table **untouched**.

**Built** (V24, new `expense` package - entity/dto/repository/service/
mapper/controller mirroring the Purchase/Product modules' own
conventions): `expense_category` (plain user-extensible table, same
pattern as `work_type` from CR-029 - inline "+ Add new category" from the
expense form, no separate admin page needed); `business_expense`
(date, category, amount, payment method - reuses the existing
`PaymentMethod` enum rather than duplicating it, notes, status
ACTIVE/CANCELLED - cancel is a soft-delete, a recorded expense is a
financial record); `expense_receipt` (optional 1:1 photo per expense,
same bytea pattern as `product_image`). Frontend: a new "Expenses" page
under a new "Accounting" sidebar section (the nav link already existed
as `available: false` in `Sidebar.tsx` - CLAUDE.md's own "flip to true in
the same commit that ships the module" convention, done here) - search/
status/category/date-range filters, a running total card for whatever
range is filtered, add/edit dialogs, a cancel confirmation. No new
permission codes needed anywhere: `EXPENSE_VIEW`/`EXPENSE_MANAGE` were
already seeded in `V1` and already granted to OWNER/MANAGER/ACCOUNTANT,
speculatively, before this module existed - exactly the same situation
`PURCHASE_VIEW`/`PURCHASE_MANAGE` was in before CR-035.

**Two real bugs found and fixed during live testing, not just claimed
working** (full detail in `BUG_REGISTRY.md`):
1. **BUG-EXP-001** - the running-total endpoint 500'd on the exact
   request the ledger page fires on first load (no date filter given) -
   the same BUG-SUP-004/BUG-PAY-001/BUG-SEC-002 class of PostgreSQL
   null-parameter-type defect, reintroduced here despite being a
   documented, known lesson. A first fix attempt (casting *both*
   occurrences of the parameter) made it worse with a different error
   (`cannot cast type bytea to date`) - comparing against
   `SecurityAuditLogRepository`'s own already-working fix showed the real
   rule: cast only the `is null` occurrence, never the comparison
   occurrence too.
2. **BUG-FE-007** - inline "Add new category" from the expense form
   created the category correctly server-side but the Select silently
   reverted instead of showing it selected, failing client validation.
   Root-caused via direct instrumentation (temporary console logging
   captured through a Playwright console listener, not guessing) to a
   genuine Radix Select quirk: it fires a spurious second
   `onValueChange('')` one render after `setValue()` sets the real id,
   at the exact moment the Select's own options list (from a
   parent-owned, asynchronously-updated array) hasn't yet caught up to
   include the new option. **Re-tested the identical, already-shipped
   pattern in `ProductForm`'s own inline category/brand creation
   (CR-024) and found the same defect there too** - it had been silently
   sending `categoryId: 0` instead of the new id or `null` the whole
   time, surfacing as a confusing server-side "not found" error on
   submit rather than a visible validation message. Fixed both call
   sites with the same guard (ignore an empty-string `onValueChange`,
   since no real category/brand ever has that value).

**Verified**: backend `mvn -o test` 242/242 (same 2 pre-existing
BUG-ENV-002 Testcontainers/Docker-discovery failures, unrelated - 6 new
tests: 4 `BusinessExpenseServiceImplTest`, 2 `ExpenseCategoryServiceImplTest`).
Frontend `tsc -b --force`/`vite build` both clean. **Live-verified end to
end**: created categories and expenses via the API, confirmed the running
total correctly excludes a cancelled expense (₹51,500 → ₹50,000 after
cancelling the ₹1,500 one) and correctly includes everything when no date
filter is given (the bug's own repro case); uploaded a receipt and
confirmed `hasReceipt` flips correctly; tested RBAC (STAFF, confirmed to
genuinely lack `EXPENSE_VIEW`/`EXPENSE_MANAGE` via their real login
response, correctly 403's on list/create) and cross-tenant isolation
(Tenant B correctly gets 404 - never a leak - on Tenant A's expense,
receipt image, and a create attempt against Tenant A's own category id)
via the same disposable second tenant used throughout this session's
security rounds. Full Playwright browser session for the frontend: sidebar
"Expenses" link visible, running total card and existing rows render
correctly, and - after both bug fixes - the full inline-category-creation
flow verified working end to end in **both** the Expense and Product
forms, confirmed via a direct API check that the saved record references
the real new category id, not a corrupted one.

**Not built this round, stated plainly**: Labour Monitor and the
free-hosting guide/sample-data phases remain next, per the sequencing
agreed before this round began.

---

## CR-036 phases 1-2: multi-bank-account invoice payments, invoice sharing, Product bulk import + photo

User's request bundled five things in one message: (1) a full GST invoice
redesign with multi-bank-account/QR support and Email/WhatsApp sharing,
(2) a Product page file-upload button, (3) a free-hosting deployment
guide, (4) more sample data, (5) an "Expense Tracker" and "Labour Monitor"
page the user believed had been discussed before. Investigated first
rather than guessing: the Invoice module already had a real multi-page
GST PDF (HSN/SAC, CGST/SGST/IGST split, transport fields) - the actual gap
was narrow (multi-account/QR + sharing). Product had zero upload
capability of any kind. **Expense Tracker and Labour Monitor have no spec
anywhere in this repo** - every mention across `RESUME_POINT.md`/
`CHANGE_REQUEST_REGISTRY.md`/`PROJECT_REGISTRY.md` is a bare "Phase 7/8 -
not started" placeholder, never a design. Asked the user to clarify scope
for the two ambiguous pieces (Product upload: bulk import, per-product
photo, or both; Expense Tracker: standalone vs extend Project's existing
expense ledger; Labour Monitor: which of attendance/payroll/project-cost-
assignment) plus sequencing, rather than build the wrong thing across
several days of work. Answers: Product gets **both** upload types;
Expense Tracker is a **new standalone ledger, Project's own stays
untouched**; Labour Monitor needs **all three** (directory+attendance,
payroll, project-cost assignment); sequence **invoice payments first
(smallest, best-specified gap) → Product upload → Expense Tracker →
Labour Monitor → sample data + hosting guide last**, each phase built,
tested and fixed before the next starts. This entry covers the first two
phases, done this round; Expense Tracker and Labour Monitor are next,
each needing a CR of its own once scoped in detail (they are real new
modules, not a checkbox-sized feature).

**Phase 1 - Multi-bank-account invoice payments + real sharing** (V22,
backend `tenant.upload`/`tenant.entity` additions, frontend
`BankAccountsCard`): the shop's pre-existing single set of bank fields on
Shop Settings is untouched and stays the fallback. New `tenant_bank_account`
(label, bank, account holder, encrypted account number reusing CR-018's
converter, IFSC, UPI, one `default_account`) and `tenant_bank_account_qr`
(any number of owner-labelled QR images per account - "SBI QR", "GPay") -
duplicate-account detection runs in the service layer since the account
number is non-deterministic ciphertext at rest and can never be compared
at the database level. `InvoiceWizard`'s Payment step gained an optional
bank-account + QR picker (defaults to the tenant's own default account);
`InvoicePdfService.paymentBlock()` prefers the invoice's selected account
when present, live-resolved at render time exactly like the pre-existing
single-account behaviour (never snapshotted - deleting an account later
just falls the PDF back to the shop default, `ON DELETE SET NULL`).
Real sharing: `POST /v1/invoices/{id}/share/email` sends the actual PDF as
a `MimeMessageHelper` attachment via the existing SMTP config, returning
an honest `SENT`/`LOGGED_ONLY`/`FAILED` status rather than faking success
when no mail server is configured (matches `SmtpMailService`'s established
fallback). WhatsApp uses the browser's own Web Share API to hand the real
PDF to whatever the device offers - there is no WhatsApp Business API
credential anywhere in this environment, so building a fake direct
integration was rejected in favour of the same mechanism most web apps
without one use; falls back to a `wa.me` text link (no file, a URL cannot
carry one) with an honest toast telling the user to attach the file they
just downloaded.

**One real bug found and fixed during live testing**: `POST
/v1/invoices/{id}/share/email` 500'd on every call -
`InvoiceEmailServiceImpl.emailInvoicePdf()` had no `@Transactional` of its
own and called `tenantRepository.getReferenceById(...).getName()` after
two already-transactional calls had each opened and closed their own
session, so the lazy proxy had nothing to attach to
(`LazyInitializationException`). Fixed by switching to `findById()`,
which resolves eagerly within its own transaction. Full writeup:
BUG-INV-002 in `BUG_REGISTRY.md`.

**Phase 2 - Product bulk import + photo upload** (V23, new
`product.extraction`/`product.upload` packages mirroring the Purchase
module's proven Supplier Bill Import architecture, including its
already-learned security lessons applied from the start rather than found
live a second time - control-character sanitization, BUG-PUR-002; the
shared file-validation utility reused directly from `purchase.upload`,
inheriting BUG-PUR-004's content-type-never-trusted fix automatically).
CSV/Excel upload → preview (never writes) → confirm (one transaction,
all-or-nothing - simpler than Purchase Import's merge logic, since every
row here is a brand-new product, not a transaction line that might match
an existing one; a duplicate code or name is a preview-time error, the
row is simply excluded from what gets sent to confirm). Category/brand
resolved by exact name match against the tenant's existing catalogue;
unmatched names are left unlinked, not auto-created (Category/Brand
already have their own dedicated creation endpoints). Per-product photo:
new `product_image` 1:1 table, same established pattern as
`user_avatar`/`tenant_logo` - edit-mode only (no product id exists yet in
create mode, same reasoning Shop Settings' own logo upload already has).

**Verified**: backend `mvn -o test` 236/236 (same 2 pre-existing
BUG-ENV-002 Testcontainers/Docker-discovery failures, unrelated - 8 new
tests this round: 2 `InvoicePdfServiceTest` for the account-selected vs
fallback payment block, 6 `TenantBankAccountServiceImplTest` for default-
account uniqueness, duplicate detection, and delete-promotes-another-
default). Frontend `tsc -b --force` and `vite build` both clean.
**Live-verified end to end, not just unit-tested**: created two real bank
accounts via the API (one default, one with an uploaded QR), confirmed
`GET /v1/settings/bank-accounts` masks correctly and `/reveal` returns the
real number while logging a security-audit event; created an invoice
explicitly selecting the non-default account + its QR and confirmed via
`pdftotext` on the actual generated PDF that it printed the *selected*
account's real details, not the shop default; confirmed a no-selection
invoice still falls back to the shop's own bank fields correctly; tested
RBAC (STAFF correctly 403's on every new bank-account/product-import/
photo-upload endpoint) and cross-tenant isolation (Tenant B correctly
gets 404 - never a leak - reaching Tenant A's QR images, bank account
reveal, and an invoice referencing Tenant A's own bank_account_id) via a
second real disposable tenant, same discipline as every earlier security
round this session. Product import live-tested via a real CSV (2 rows
created, then confirmed the identical file re-uploaded correctly flags
both rows as "already exists" on both code and name). Full Playwright
browser session (not just curl) for both phases: Shop Settings' new "Bank
accounts" card renders both accounts with masked numbers and a working
"QR codes" sub-dialog showing the real uploaded image and its label; the
Invoice wizard's Payment step shows the account/QR picker and correctly
hides the QR sub-picker when the selected account has none uploaded; a
completed invoice's detail page has a working Share dropdown (Email +
WhatsApp/more apps) whose Email dialog actually calls the new endpoint;
the Products page shows a real thumbnail column (confirmed rendering the
actual uploaded test image, not a placeholder, for the one product with a
photo), the Import button opens a working dialog that previews, confirms,
and reports "N product(s) created", and the product Edit form shows a
working Upload/Replace/Remove photo control identical in behaviour to
Shop Settings' own logo uploader. Zero real errors across every test -
the only HTTP 4xx noise observed (401 on first-load `/auth/refresh`, 404
on `/auth/me/avatar`, 404 on `/customers/credit-check` for a brand-new
mobile number) are the same pre-existing, already-documented expected
behaviours from earlier rounds this session, confirmed by exact URL, not
new issues.

**Not built this round, stated plainly**: Expense Tracker, Labour
Monitor, increased sample data, and the free-hosting `.md` guide - all
four are the explicitly next steps, per the sequencing agreed with the
user before this round began. Frontend has no automated test framework
(Jest/Vitest) - verification for both phases followed this session's
established pattern of real Playwright browser sessions against a live
backend rather than component-level tests, consistent with how every
other frontend feature this session has been verified.

---

## Adversarial security-testing pass on CR-035 (Purchase Import module)

User explicitly asked to act as a tester: stand up a second real tenant and
probe for cross-tenant data leaks, probe RBAC with a real under-privileged
role "thinking all scenario," and throw a battery of malformed/malicious
files at the new Supplier Bill Import upload endpoints (missing files,
wrong formats, duplicates, empty rows, "malware files"), then harden the
code. Full detail for all four bugs found is in `BUG_REGISTRY.md`
(BUG-PUR-001 through BUG-PUR-004) - this section is the summary.

**Multi-tenant isolation**: registered a genuine disposable second tenant
via the real `POST /v1/tenants/register` endpoint (not a DB hack), created
real records as that tenant, then attempted cross-tenant access in both
directions using the other tenant's real record IDs against every Purchase/
Import resource, including the new document-download and
security-audit-log/dashboard endpoints (historically leak-prone -
BUG-SEC-001 precedent). **Zero leaks found.**

**RBAC**: created a real STAFF-role user (seed-confirmed to genuinely lack
`PURCHASE_VIEW`/`PURCHASE_MANAGE`/`SUPPLIER_VIEW`), logged in through the
real login flow, and confirmed the block holds at all three independent
layers: API (403 on every Purchase/Supplier call), frontend route guard
(direct navigation to `/purchases`/`/suppliers` renders a clean "you do not
have access" page, never real data), and the sidebar (those links never
render for STAFF at all). Zero bypasses found.

**Four real bugs found and fixed via adversarial file-upload testing**
(full detail + regression tests in `BUG_REGISTRY.md`):
1. **BUG-PUR-001** (MEDIUM) - a missing/malformed multipart upload crashed
   with a raw 500 for any caller, regardless of permissions. Fixed:
   `GlobalExceptionHandler` gained handlers for
   `HttpMediaTypeNotSupportedException`/`MissingServletRequestPartException`/
   `MaxUploadSizeExceededException`/`MultipartException`.
2. **BUG-PUR-002** (MEDIUM) - a null byte in an uploaded field crashed a
   read-only preview query with a PostgreSQL-level error, misleadingly
   surfaced as a 409. Fixed: `RowParsing.sanitize()` strips control
   characters from every extracted field before it reaches a query.
3. **BUG-PUR-003** (LOW) - new products created via import silently
   discarded the bill's own SKU, always getting an auto-generated code.
   Fixed: `newProductSku` threaded through to `ProductRequest.productCode`,
   with the existing within-transaction dedup map extended to check by SKU
   before name.
4. **BUG-PUR-004** (CRITICAL) - a supplier bill could be uploaded with a
   spoofed multipart `Content-Type` (e.g. a real, valid `.csv` containing
   a `<script>` tag, declared as `text/html`) and was later served back
   unchanged with `Content-Disposition: inline` - confirmed live as a real,
   exploitable stored-XSS: any tenant user with `PURCHASE_VIEW` opening
   "Original bill" on that purchase would execute the attacker's script in
   the app's own origin. Separately, the filename was checked for path-
   traversal characters (`/`, `\`) but not header-injection ones (`"`,
   `\r`, `\n`), which flow unescaped into the `Content-Disposition`
   response header. Fixed: the served content type is now derived solely
   from the file's validated extension, never from client input, at
   *serve* time (so already-poisoned rows written before the fix are
   neutralized automatically, no data migration needed - verified live);
   the filename validator now also rejects `"`/`\r`/`\n`.

**Verified**: full backend suite 228/228 pass (same 2 pre-existing
BUG-ENV-002 Testcontainers/Docker-discovery errors, unrelated - +6 new
tests this round: 1 `CsvDocumentExtractionServiceTest`, 3
`DocumentUploadValidationTest`, 2 `PurchaseImportServiceImplTest`, new
file). All four fixes re-verified live after a backend restart, including
re-fetching the actual document created during exploitation testing
(purchase id 6) and confirming it now serves as `text/csv` with no data
migration. `registry/static_check.py` could not be run this round - no
Python interpreter is installed in this environment (a pre-existing
environment gap, unrelated to these changes; only Windows Store
execution-alias stubs are present).

**Not yet tested, explicitly flagged rather than silently skipped**: a
genuine zip-bomb/decompression-bomb resilience check for `.xlsx` uploads;
directly probing the `/confirm` endpoint's RBAC and cross-tenant
protections (only `/preview` and the document-download endpoint were
directly probed this round, though both share the same
`@PreAuthorize`/tenant-scoping pattern by code inspection); the
300-character-long-product-name edge case through the CONFIRM stage
specifically (Bean Validation on `ProductRequest` when called
service-to-service rather than through a controller's own `@Valid`).

---

## CR-035: Purchase module + Supplier Bill Import

The customer explicitly asked for file-upload capability. The earlier
same-day audit (`AUDIT_GAP_REPORT.md`) had confirmed Purchase and Supplier
Bill Import were 100% unbuilt and that Import cannot be built honestly
without Purchase existing first (spec's own §23) - so this round built
both, in that order, then live-tested with real bulk files as explicitly
requested ("create 100 records and 1000 records different type file
type").

**Purchase module** (`backend/purchase` package, `V21__purchase_schema.sql`):
`purchase`/`purchase_item`/`purchase_payment`/`purchase_document` tables,
mirroring Invoice/Payment's existing shape exactly (paidPaise/balancePaise/
status always derived via `recalculate()`, never stored independently).
Statuses DRAFT/RECEIVED/PARTIALLY_PAID/PAID/CANCELLED (DRAFT is defined but
not currently reachable through the UI - every purchase is created as
RECEIVED, stock updates immediately, matching how a small hardware shop
actually works: the bill and the goods arrive together). Receiving stock
goes through the existing `StockService.applyMovement()` choke point with
two new movement types, `PURCHASE_RECEIPT` (increase) and `PURCHASE_RETURN`
(the paired reversal on cancel, mirroring SALE/SALE_REVERSAL) - never a
direct `quantity_on_hand` mutation. `PURCHASE_VIEW`/`PURCHASE_MANAGE`
permissions were already seeded to every role back in V1 (speculatively,
for a module that didn't exist yet) - reused as-is, no new permission
codes needed. Frontend: full list/detail/create pages, matching the
Invoice module's established patterns (reused `SupplierPicker`/
`ProductPicker` components directly rather than rebuilding them).

**Supplier Bill Import** (`purchase/extraction`, `purchase/upload`,
`PurchaseImportService`): a real, working two-step upload - `/preview`
(parses, matches, never writes) and `/confirm` (the only endpoint that
persists, one transaction, all-or-nothing). `DocumentExtractionService`
is a genuine pluggable interface: `CsvDocumentExtractionService` (Apache
Commons CSV) and `ExcelDocumentExtractionService` (Apache POI) are real,
deterministic, fully tested implementations; PDF/image upload is rejected
outright at validation time with a clear "needs a configured OCR/AI
provider, enter manually or export as CSV/Excel" message - not faked, not
silently attempted. Existing-vs-new detection: exact code/name match for
products (case-insensitive), exact name match for brand/category, GST/
mobile/code/name match for supplier (supplier resolution happens via the
existing Supplier module directly in the dialog, not a bespoke matching
UI - CSV/Excel row-files don't carry a shop's own letterhead info the way
a scanned image would, so this was the honest scope rather than
over-building match logic for data the file format doesn't actually
contain). New brand/category creation happens inline via the *existing*
`POST /v1/brands`/`/v1/categories` endpoints (not reinvented) the moment
the owner clicks "+ Add" in the preview table - only Product creation
happens inside confirm()'s own transaction, since that's the one piece
where spec §15's "no orphaned data on a mid-batch failure" genuinely
matters. Possible-duplicate-bill detection (same tenant/supplier/bill
number-or-date+total) throws a specific `DUPLICATE_BILL_SUSPECTED` (409)
the frontend catches and offers "Continue anyway" for, matching spec §14.
File security (spec §20): 20MB cap, extension allowlist (csv/xlsx only),
and a real ZIP-signature check for .xlsx (`PK\x03\x04` magic bytes) so a
mislabeled file never reaches Apache POI's XML parser.

**Two real bugs found and fixed during live bulk testing, not just
claimed working:**
1. Two rows in the *same* bill naming the identical brand-new product
   (a realistic scenario - the same fresh item split across two line
   items) crashed the whole import: the second row's product-creation
   call failed because the first row's creation, moments earlier in the
   same transaction, had already claimed that name. Fixed by tracking
   newly-created products by normalized name within `confirm()`'s own
   scope and reusing the row's own earlier-created product instead of
   attempting a second creation.
2. After that fix, the result summary's "existing matched" counter
   silently conflated two different things: rows matching a genuinely
   pre-existing catalogue product, and rows reusing a product this same
   import batch had just created a few rows earlier. Both were being
   reported as "existing," which would have overstated how much of the
   catalogue already existed before this import. Split into two honest
   counters - `existingProductsMatched` (real pre-existing matches only)
   and `rowsMergedWithEarlierRow` - both surfaced separately on the
   result screen.

**Verified**: full backend suite 220/222 (same 2 pre-existing
BUG-ENV-002 Docker-only failures, unrelated); 17 new backend tests (7
CSV extraction, 3 Excel extraction, 10 PurchaseServiceImplTest covering
arithmetic/stock/payment/cancel), all passing. Frontend `tsc -b --force`
and `vite build` both clean. **Live-tested at the exact scale requested**:
generated real 100-row and 1000-row files in *both* CSV and Excel
(4 file combinations total, via a throwaway Node script using the real
`xlsx` library - not hand-typed fixtures), each deliberately containing
a mix of genuinely-new products, rows re-using already-catalogued
products (to test the existing-match path), and 2 rows with real data
errors (negative quantity, non-numeric price) to test row-level
validation on bulk data, not just a small hand-written unit test.
Confirmed via direct API calls: all 4 preview combinations return
identical, correct results for the same underlying data (CSV and Excel
parse to byte-identical structured output); the unsupported-file-type
path was tested with a real (fake-content) `.pdf` and correctly rejected
with a 422 and the honest message. Confirmed via a full Playwright
browser session (not just curl): upload -> preview -> pick supplier ->
resolve a new brand/category inline -> confirm -> result screen -> "View
purchase" -> real purchase detail page, all live-clicked, screenshotted,
and visually correct (GST math, line items, "Imported from a supplier
bill file on..." traceability line, working "Original bill"
download link). Confirmed downstream effects in the actual UI: the Stock
page shows the real increased quantities, the Products page finds the
newly-created products by search. **Scale test**: a real 998-row Excel
confirm (2 rows excluded for validation errors, matching the file's own
deliberately-injected bad data) completed in ~16 seconds end to end
through the real multipart endpoint, correctly recognized as 998
pre-existing matches (0 new) since the test data's product-name space
overlapped with earlier import batches - itself a live demonstration
that duplicate-prevention works correctly *across* separate import
batches over time, not just within one file.

**Not built this round, stated plainly**: PDF/image/OCR extraction
(needs a real vision-capable AI or OCR provider credential - correctly
gated behind graceful degradation, matching every other "needs a real
external key" feature in this codebase); Supplier Payables (the actual
next dependency this unblocks, not started this round); the "pay
quickly" supplier-payment-priority suggestion (needs Supplier Payables
first); a dedicated `PurchaseDocumentServiceImplTest`/
`PurchaseImportServiceImplTest` unit-test file (the matching/dedup logic
was live-tested thoroughly instead, given the explicit "test with real
bulk files" ask - a good next-session addition, not silently skipped).

**Environment notes worth keeping**: the session's scratchpad directory
got wiped again mid-round (same non-durability behavior documented
earlier this session) - lost an installed `playwright` node_modules
folder, recovered by reinstalling in under 10 seconds; the *parent*
scratchpad directory (holding the generated test files and `xlsx`
package) survived intact, so nothing genuinely important was lost.
`curl -F "field=$(cat bigfile.json)"` fails with "Argument list too
long" for a ~1000-row JSON payload on Windows - fixed with curl's own
file-reference form syntax, `-F "field=<bigfile.json;type=..."`, which
streams the file directly instead of inlining it as a shell argument.

---

## Repository audit + demo hardening (same-day evening demo)

A combined master prompt arrived asking to continue the full remaining
roadmap (Purchase, Supplier Bill Import/OCR, Finance ledger, Labour/
Attendance, Reports, Notifications v2, AI v2, SAML) same-day as a
customer demo. Ran the requested audit first rather than coding blind -
see `AUDIT_GAP_REPORT.md` for the full A-K breakdown. Verified against
the actual repository (migrations, packages, `grep` for real symbols),
not against what any registry file claims:

- **Purchase, Supplier Bill Import/OCR, the Finance ledger, Labour/
  Attendance, Reports, Notifications v2, AI v2, SAML are 100% unbuilt** -
  zero code, confirmed by absence (`find src -iname "*purchase*"` returns
  nothing; `financial_transaction` appears exactly once, inside a comment).
  Each is a real multi-day-to-multi-week effort, several hard-dependency-
  chained (Purchase before Import before Payables).
- **Project Management (CR-029) is genuinely done, not a rebuild
  candidate** - resolves the "contradiction" the master prompt flagged
  between an old status doc's "Start Phase 6" line and CR-029's own
  completion note; the line simply predated CR-029 shipping later the
  same day it was written.
- The one real, already-known gap: `grep -rl "StockService" project/`
  returns zero files - a project's materials don't decrement stock.
  Confirmed, not touched this round (see decision below).

**Given tonight's demo, asked the user directly how to spend the
remaining time** rather than guessing at risk tolerance for something
this high-stakes - a full new-module push risked destabilizing what's
already demo-ready. Chosen: **harden what already works**, not start any
new module.

**Demo-path hardening performed**:
- Full backend suite re-run: 203/205, same 2 pre-existing BUG-ENV-002
  Docker-only failures, no regression.
- Seeded the disposable `UI Verify Shop` test tenant with real records via
  the actual API (not fixtures) - a category, brand, supplier, 2
  customers, 3 products with opening stock, an invoice with a partial
  payment, a quotation - so every list/detail page has real content to
  show instead of empty states.
- Live-crawled all 19 sidebar-reachable pages plus global search and an
  invoice detail view via Playwright.
- **Found and root-caused a real issue, not just noted it**: `Shop
  Settings` and `Appearance` intermittently threw
  `net::ERR_INSUFFICIENT_RESOURCES` after ~17 consecutive client-side
  navigations in one browser tab - reproduced 3 times including after a
  full dev-server restart, so not simple flakiness. Root-caused to Vite
  **dev mode** specifically: a single page navigation fires ~183 separate
  unbundled ES-module requests (confirmed via request-log breakdown),
  each doubled by React 18 StrictMode's dev-only double-effect
  invocation - accumulated across many rapid SPA navigations in one tab,
  this hits a real Chromium per-tab pending-request ceiling. **Confirmed
  the fix**: re-ran the identical 19-page crawl against the production
  build (`vite build` + `vite preview`) and every page loaded completely
  clean, zero resource errors - production bundles collapse to 4 JS + 1
  CSS file, and StrictMode's double-invoke doesn't apply outside dev
  mode. This was a dev-server-only artifact, not an application bug -
  never reproduced in any single-page isolated load, and a human
  clicking through the sidebar at normal pace across 19 pages was never
  going to hit it in dev mode either, but production build removes the
  class of risk entirely, which matters given tonight.
- **Frontend is now served from the production build, not `npm run dev`**
  - killed the dev server, ran `vite build` then `vite preview --port
    5173` so the URL/port the team already knows didn't change. This is
  also just better practice for a live customer demo regardless (faster
  page loads, no dev-mode console noise, no HMR overhead) - not only a
  workaround for the resource issue above. **To go back to active
  development after tonight**: kill the preview process and run `node
  node_modules/vite/bin/vite.js` (dev mode) again from `frontend/`.

**Not done this round, deliberately, per the chosen scope**: no new
module code (Purchase/Finance/Labour/OCR/Reports/Notifications-v2/AI-v2/
SAML all remain exactly as found in the audit); the project-material-
vs-stock gap was confirmed but not fixed (the user chose "harden what
already works" over "fix the Project↔Stock gap" when asked).

---

## CR-034: Advanced Customizable Theme System

User sent a second, focused spec asking for a *design-system-level* theme
engine - not "a theme selector that only changes the primary colour."
7 real visual paradigms (Minimal/Bento/Glass/Liquid Glass/Spatial/
Neomorphic/Claymorphic), 10 named colour palettes, Light/Dark/System,
Intensity/Corner/Elevation/Motion dials, a dedicated Appearance page,
live preview with no page reload, per-tenant isolation, and an explicit
"do not overdesign / usability > decoration" constraint given this is a
daily-use ERP, not a marketing site.

**Architecture decision, stated up front and held to**: rather than
hand-writing bespoke CSS for the ~30 page-level component categories the
spec lists (Sidebar, Topbar, Tables, Forms, Modals, Toasts, ...), the 7
design styles are real, distinct *token recipes* (surface opacity/blur/
border-opacity/shadow, control shadow/inset-shadow - `theme/
designStyles.ts`) applied through the small set of shared primitives
every page is already built from (Layout B, CR-012): `Card`, `Button`,
`Input`, `DialogContent`, `DropdownMenuContent`, plus `GlobalSearch`'s own
panel. Giving *those* real per-style treatment is what makes "the
complete application adapts" true without 30 categories drifting out of
sync with each other within a month. Only background/shadow/blur tokens
ever change per style - text colour, the focus ring (`--ring`) and input
border presence are untouched by any style, so contrast holds by
construction rather than a bolted-on accessibility pass.

**7 design styles** (`theme/designStyles.ts`, `theme/DesignStyleProvider.tsx`):
Minimal (default/recommended, flat + `0.05` alpha shadow), Bento (same
surface as Minimal - it's a *layout* pattern, see below), Glass (`0.72`/
`0.55` bg opacity, 14px blur, soft diffuse shadow), Liquid Glass (`0.66`/
`0.5` opacity, 20px blur, a colour-tinted glow plus an inset white
highlight line for the "sheen"), Spatial (opaque, deliberately deeper
elevation), Neomorphic (dual soft shadow pair, no border, a genuinely
*pressed* inset on inputs via a second `--control-inset-shadow` token),
Claymorphic (chunky inflated shadow + inset top highlight). Each has a
distinct light *and* dark variant - glass reads differently against a
dark background than a light one, not just an inverted opacity number.

**Bento layout** (`index.css`'s `.bento-grid`, `DashboardPage.tsx`): a real
CSS grid-template-areas swap on the dashboard's 3 sales KPI cards only
(Today's Sales promoted to a large hero cell) - not a separate dashboard
implementation, the exact same `SalesSummaryResponse` data and `<Card>`
components, just reordered and resized. Collapses to one column below
640px; a 4-cell asymmetric grid at phone width is noise, not density.

**Intensity/Corner/Elevation/Motion**: computed in JS (same pattern
`ColorThemeProvider` already used successfully) rather than CSS `calc()`
chains across shadow/blur/opacity, which don't compose reliably.
Intensity scales blur radius (calm 0.55x / balanced 1x / expressive 1.5x).
Elevation scales shadow alpha per layer (flat 0x / subtle 0.55x /
standard 1x / elevated 1.7x) via `scaleShadowAlpha()`. Corner writes
straight to the pre-existing `--radius` variable - `tailwind.config.js`
already maps every `rounded-{sm,md,lg}` utility to it, so this reached
every rounded corner in the app for free, zero component changes needed.
Motion sets `--motion-duration` and is force-overridden to Reduced
whenever `prefers-reduced-motion: reduce` matches the OS, regardless of
the stored choice - the actual accessibility enforcement, not just a UI
option (`index.css`'s `@media (prefers-reduced-motion: reduce)` block,
the standard near-zero-duration pattern). A parallel `@media
(prefers-contrast: more)` block force-flattens blur/opacity back to fully
opaque for every style, since translucency is the one effect in this
system that can genuinely hurt contrast.

**11 colour themes** (`theme/colorThemes.ts`): added Teal/Violet/Rose,
renamed the existing near-grayscale "Minimal" preset to "Monochrome" (same
tokens, name only - avoids colliding with the new "Minimal" *design
style*, a different axis entirely). Royal Blue/Indigo/Ocean/Emerald/Teal/
Amber/Violet/Slate/Rose/Monochrome match the spec's 10 by name; Aurora
(with its hero-gradient) stays as an 11th, unrequested but already built
and harmless to keep.

**Per-tenant/per-user isolation, real gap closed** (`theme/themeScope.ts`,
`useThemeScope.ts`): every appearance preference used to live in one flat
localStorage key shared by *any* tenant that ever signed in on that
browser - shop A's owner picking Liquid Glass + Ocean + Dark would leak
straight into shop B's session on the same machine. Fixed with zero
backend change: `mobile_no`/`email` are globally unique across tenants
(CR-016), so the authenticated user's own `id` is already an unambiguous
per-tenant scope. `AuthProvider` calls `setThemeScope(user.id)` on
login/refresh and `setThemeScope(null)` on logout; every theme provider
(`ThemeProvider`, `ColorThemeProvider`, `DesignStyleProvider`) reads/writes
a `key:{scope}` variant and re-reads on scope change via a small
module-level pub-sub (not React context, since providers need the value
synchronously on first render, before `AuthProvider` has resolved anyone).
A one-time legacy-key fallback means a pre-CR-034 install's saved
mode/colour choice is still honoured the first time under the new scoped
key, rather than silently reset to defaults.

**Dedicated Appearance page, one implementation two entry points**
(`modules/settings/components/AppearanceSettings.tsx`): the actual
controls live in one component, used both as the standalone route
`/profile/appearance` (`AppearancePage.tsx`, linked from Shop Settings'
now-slimmed Appearance card) and as a new "Appearance" tab on `ProfilePage`
- satisfies the spec's "My Profile -> Appearance OR Shop Settings ->
Appearance" without maintaining the controls twice. The old
`ThemeSelector.tsx` (colour + mode only) is fully superseded and deleted,
not left as a parallel/dead implementation. The page's own "Preview"
panel renders the app's *real* `Card`/`Button`/`Input`/`Badge` components,
not a mock image - what's shown is exactly what every page looks like,
since those components already read the same tokens this page writes.

**A real bug found and fixed during live testing, not just claimed
working**: the Elevation dial's "Flat" setting visibly did nothing under
the Minimal style (the default). Root cause: `scaleShadowAlpha()`'s
"don't flatten the Liquid Glass highlight line" exception was keyed off a
raw alpha-value threshold (`<= 0.08`), and Minimal's own base shadow
alpha (`0.05`) happened to fall under that same threshold by coincidence
- so it was silently exempted from elevation scaling too. Fixed by
detecting the highlight layer *structurally* (its literal `inset 0 1px 0
0 rgb(255 255 255...)` shape) instead of guessing from alpha magnitude.
Caught only because the live-verification script read the actual computed
`--surface-shadow` value after clicking Flat and it hadn't changed - a
`tsc`/`build` pass alone would never have caught this.

**A second issue found in the *verification script itself*, not the
app**: an early Playwright pass using `page.locator('button', { hasText:
'Flat' })` silently clicked the wrong element three separate times,
because "Flat" is a substring of "in**flat**ed" (Claymorphic's own
description) and "stay **flat**" (Spatial's). Fixed by scoping every
segmented-control assertion to `[role="group"][aria-label="..."]` first.
Worth remembering for the next round of live verification - `hasText`
substring-matches the *entire* text content of an element, including any
prose sitting near the real target, not just a visible label.

**Verified**: `node node_modules/typescript/bin/tsc -b --force` and `node
node_modules/vite/bin/vite.js build` both clean (bundle grew ~17KB JS /
~2.6KB CSS gzipped for the whole engine - no new dependency added, pure
CSS/token work). No backend change this round, so the backend suite is
unaffected (last run: 203/205, same 2 pre-existing BUG-ENV-002 failures).
**Live-verified** end to end via Playwright against the same disposable
test tenant from the CR-033 round: all 7 style cards render and are each
independently selectable; Glass vs. Neomorphic produce genuinely different
`--surface-blur`/`--surface-bg-opacity`/`--surface-shadow` values (not
just different colours); Corner=Sharp changes `--radius` live; Elevation=
Flat genuinely zeroes `--surface-shadow` to `none` (post-fix); Bento
promotes Today's Sales to a large hero cell on the real Dashboard; the
11-colour palette (Teal tested) still applies correctly; both entry points
(dedicated page and the Profile tab) render the same live controls.
Visually confirmed via screenshot: Liquid Glass produces a genuinely
softer/translucent look on the real Dashboard KPI cards, not merely a
different accent colour.

**Not built this round, stated plainly**: bespoke per-style visual
treatment for anything *outside* the shared primitive layer - Tables,
Tabs, Toasts, Tooltips, date pickers, the sidebar's own shape, empty/
loading/error-state illustrations, and every per-module page (Customer/
Product/Quotation/Invoice detail) still look identical across all 7
styles apart from whatever they already build from Card/Button/Input/
Dialog/Dropdown. This was the explicit trade-off stated before writing
any code, not a silently dropped scope item. A custom accent-colour picker
(spec §13, a colour-wheel/hex-input beyond the 11 presets) was not built -
the 11 presets already give full control over every token an accent
would touch (buttons/links/focus/charts/badges), and a genuinely free-form
picker risks landing on a hue with real contrast problems against
`--primary-foreground` with no built-in guard - flagged as a real gap
rather than quietly working around it.

---

## CR-033 round 1: 8-theme color system, command palette extension, dashboard trend data

User sent a 40-section "Complete Modern UI/UX Redesign" master spec (design
tokens, 8 named color themes, genuine dark mode, sidebar/topbar redesign,
Cmd+K command palette, dashboard analytics, per-page redesigns, a table/
modal/button design system, micro-interactions, auth/profile redesign,
empty/loading/error states, responsiveness, accessibility, an 18-phase
implementation order) with explicit "do not overdesign"/"speed and
usability > decoration" constraints for an ERP used hundreds of times daily,
and a request to audit the codebase and explain the architecture before
implementing. Audited first: the frontend already had a working
`ThemeProvider` (light/dark/system, localStorage-persisted, CSS-variable
token system in `tailwind.config.js`/`index.css`) and a working `GlobalSearch`
command palette (Cmd/Ctrl+K, debounced, `Promise.allSettled`) covering
Products/Suppliers/Customers only. Scoped this round to the
highest-leverage, lowest-risk slice rather than attempting all 18 phases
superficially: (1) a second, independent color-theme axis layered on the
existing light/dark system, (2) extending the existing command palette to
cover Invoices/Quotations/page-navigation, (3) real (non-fabricated)
dashboard trend data. Remaining phases explicitly deferred - see below.

**8-theme color system** (`frontend/src/theme/colorThemes.ts`,
`ColorThemeProvider.tsx`, `ThemeSelector.tsx`, new): Royal Blue/Indigo/
Emerald/Slate/Amber/Ocean/Minimal/Aurora, each with distinct light/dark HSL
token sets for primary/secondary/accent/ring/sidebar family/chart-1..5,
applied via `document.documentElement.style.setProperty()` (inline styles
cleanly override the stylesheet, no new CSS mechanism needed), persisted to
`localStorage` (`hardware-erp-color-theme`) independently of the existing
light/dark preference. Only Aurora defines a `heroGradient` - every other
theme is a flat primary-color tint, since the spec explicitly restricts
gradients to select hero surfaces, never behind data tables (not built onto
any surface yet this round - the token exists for a future login/dashboard
hero pass). Reachable from Shop Settings → new "Appearance" card.

**Command palette extension** (`GlobalSearch.tsx`): now also searches
Invoices, Quotations, and page navigation (`navigableItems()` from
`Sidebar.tsx`, which was already exported "for the command palette to
search" but sat unused). A search failing for one module still degrades to
an empty section for that module only (`Promise.allSettled`), unchanged
from the existing pattern.

**Dashboard trend data** (`SalesSummaryResponse`, `DashboardServiceImpl`):
added `todaySalesPaise`/`yesterdaySalesPaise` (raw longs, not display
strings) so the frontend computes the real "+X.X% vs yesterday" itself,
reusing the existing `invoiceRepository.todaySales(tenantId, date)` with
yesterday's date - no new repository method needed. `DashboardPage.tsx`
renders the trend only when `yesterdaySalesPaise > 0` (a zero-yesterday
tenant would divide by zero / show a meaningless "+∞%" - suppressed
entirely rather than shown wrong, same "never fabricate a number" ethic as
every other dashboard figure this session). New test
`reportsDistinctTodayAndYesterdayFigures` in `DashboardServiceImplTest`.

**Verified**: full backend `mvn -o test` - 203/205 pass, the same 2
pre-existing BUG-ENV-002 Testcontainers/Docker-discovery errors (unrelated,
documented every round). Frontend `node node_modules/typescript/bin/tsc -b
--force` and `node node_modules/vite/bin/vite.js build` both clean (run via
the binaries directly - `npm run typecheck`/`npx tsc` hit the same
unrelated shell-wrapper quirk noted in earlier CR-030 rounds,
`'"node"' is not recognized`, in this session's git-bash; not a code issue).

**Live-verified in a real headless-Chrome session** (Playwright, installed
fresh into the scratchpad this round and driven against the actually-
installed Chrome via `channel: 'chrome'` rather than downloading a browser -
no `playwright` dependency was added to the repo itself). Registered a
disposable throwaway tenant via the existing public
`POST /v1/tenants/register` endpoint specifically so this round's testing
never touched the real owner account or its real credentials. Confirmed:
login works end to end; the dashboard renders correctly with the trend
indicator correctly absent for a brand-new tenant with `yesterdaySalesPaise
== 0` (the guard firing exactly as designed, not a bug); the Appearance
card's theme picker is genuinely functional, not decorative - clicking
Emerald then Royal Blue was confirmed via `getComputedStyle` to actually
change the `--primary` CSS variable both times; dark mode is genuinely
dark throughout (sidebar, cards, inputs), not an inverted filter; the
command palette's new "Pages" section renders and finds "Dashboard" for a
"dashboard" query. Zero real console/network errors - the only network
noise (`401` on `/auth/refresh` on first load, `404` on `/auth/me/avatar`
for a user with no photo) is already-documented expected behavior
(`useAuthenticatedImage.ts`'s own comment: "A 404 (no image set) is not an
error - it just means null"), not new bugs.

**Not yet built this round** (explicitly deferred, matching the 40-section
spec's own priority order - the highest-leverage slice was built first,
not attempted superficially across all of it): topbar redesign beyond the
search extension; dashboard KPI card visual redesign + business-analytics
charts with time filters; customer/product/quotation/invoice detail page
redesigns; forms redesign; a reusable table design system; modal/drawer
redesign; a formalized button system; the micro-interactions/animation
layer (still planned as Tailwind's existing `tailwindcss-animate` + native
CSS transitions, not GSAP, per the spec's own performance constraint);
login/auth screen redesign; profile page redesign; a consistent empty/
loading/error-state pass across every page; responsive/accessibility
testing pass; the sidebar's own visual redesign (grouping/tooltips/active
indicators) - only its existing collapsible sections and the new theme
tokens were touched.

**Leftover from this round**: a disposable test tenant ("UI Verify Shop",
mobile 9812345670) now exists in the local dev database from live-testing
login/dashboard/theme-picker without touching the real owner account -
harmless dev-only data, left in place; delete via the Users/tenant admin
path if it's ever in the way.

---

## Safe app updates: docker-compose.yml drift fixed, backup/restore scripts added

User asked to make sure updating the app version never loses data. What
already protects the *schema*: Flyway migrations are additive-only and
never edited once applied (hard rule), Hibernate runs `ddl-auto: validate`
(refuses to start on any schema drift), seed data is isolated to the `dev`
profile only. None of that is a backup, though - it protects against a
migration mistake, not a bad deploy, wrong command, or disk failure.

**Found a real, unrelated drift while checking this**: `docker-compose.yml`
no longer matched the database actually running all session -
`POSTGRES_DB`/`POSTGRES_USER` said `hardware_shop`/`<a personal login>` on port 5432,
but the live container (and every `DB_USER`/`DB_PASSWORD`/`DB_NAME` the
app has actually been started with, all session) is `hardware_erp` /
`hardware_erp` / `hardware_erp` on port 5433. `POSTGRES_DB`/`USER`/
`PASSWORD` only take effect on an empty volume, so this was harmless
against the already-initialized volume in use, but would have created a
*mismatched, empty* database on a genuinely fresh setup - a real trap for
a future clean install. Fixed to match reality (with the real values as
env-var-overridable defaults, not hardcoded).

**New**: `scripts/backup-db.sh` (plain `pg_dump`, timestamped, to a local
`backups/` folder) and `scripts/restore-db.sh` (drops and reloads schema
`public` from a backup file, requires typing `yes` to confirm - genuinely
destructive, never run without a fresh backup first). Run
`backup-db.sh` before any update that touches the backend or the
database.

**Live-verified**: ran `backup-db.sh` for real against the live dev
database - produced a real 2.3MB SQL dump (16,653 lines, 36 tables,
ends with PostgreSQL's own "dump complete" marker, not truncated).
`restore-db.sh` was **not** executed - it is destructive (drops the
schema) and running it would have wiped the live database this entire
session's work depends on; its logic mirrors the verified backup
script's own container/user conventions, but the actual restore
round-trip has not been exercised. Say so plainly rather than claiming
full round-trip verification that wasn't done.

---

---

## CR-032: subscription trial coupons ("give a complete free coupon")

User asked to "add coupon for subscription for give complete free coupon."
Clarified via 3 quick questions before building (design was genuinely
ambiguous, not a case for guessing): redeeming grants a **free trial with
an expiry** (not permanent), coupon codes are **per-tenant** (created and
redeemed by the OWNER for their own shop, same tenant-scoping every
coupon-shaped table already uses - CR-016), matching the existing retail
`coupon` table's shape but for the shop's *own* plan, not a customer
discount.

New `subscription_coupon` table (V20) + `tenant.subscription_trial_expires_at`
(nullable - null means the tier is permanent, exactly CR-027's original
picker behaviour). `SubscriptionCouponService.redeem(code)` sets the
granted tier and `now() + trialDays`; `SubscriptionServiceImpl
.currentTier()` reverts it to FREE lazily, the next time it's called
after expiry (every entitlement/feature-gate check already calls this -
no scheduled job needed). Picking a tier manually from the existing
Shop Settings dropdown always clears the trial, so an explicit choice
can never be silently overridden later.

**A real transactional bug was caught and fixed during design, before
shipping**: `currentTier()`'s revert-write needs `Propagation
.REQUIRES_NEW`, not just a non-readOnly annotation - it's called from
inside `readOnly = true` callers (`EntitlementServiceImpl`'s checks,
`TenantSettingsServiceImpl.get()`), and joining an outer readOnly
transaction would have made the FREE-revert silently never flush.

**Two more real bugs found live-testing the redeem flow** (BUG-FE-006,
full writeup in `BUG_REGISTRY.md`): the success message was destroyed
first by a `window.location.reload()`, then - after removing that - by
the parent page's own `loading` flag unmounting the whole card mid-fetch.
Fixed by passing the redemption result up and patching state directly,
no reload or remount either way.

Frontend: a "Subscription coupons" card in Shop Settings (create codes,
redeem a code, see usage/status), a "Trial" badge + reversion date on the
Subscription plan card itself. Backend: 11 new tests. Full suite:
204/206 (same 2 pre-existing BUG-ENV-002 Docker failures). Frontend
`tsc -b --force`/`vite build` clean.

**Live-verified end to end, including the fix**: created a coupon,
redeemed it, confirmed the success message rendered *and stayed visible*
this time, confirmed Plan usage recalculated under the granted tier,
manually picked a different tier and confirmed the trial cleared and
limits recalculated for that tier, then restored the tenant to MAX and
confirmed via a direct DB query that both `subscription_tier` and
`subscription_trial_expires_at` landed exactly where expected - not
assumed, checked.

---

## Password-reset email: diagnosed, not a code bug

User reported not receiving a password-reset email after using "Forgot
password." Reproduced directly against the running backend
(`POST /v1/auth/forgot-password` with the user's own real email, which
does exist in this DB as a real seeded user). Root cause: no real SMTP
account is configured in this dev environment (`spring.mail.username` is
blank) - `SmtpMailService` correctly falls back to its documented dev-mode
behaviour, logging the reset link to the backend console instead of
sending mail (`app.mail.log-links-when-unconfigured: true` in the `dev`
profile, which is active). Confirmed in the live log:
`Mail not configured. Reset link for <email>: http://localhost:5173/reset-password?token=...`.
This is by design, not a bug - the same escape hatch this codebase already
documents for SMS/WhatsApp providers before real credentials exist.

User chose (of 3 options offered): hand them the link from the log
directly for now, rather than configuring real Gmail/other SMTP
credentials. No code change made. If real email delivery is wanted
later, `MAIL_HOST`/`MAIL_PORT`/`spring.mail.username`/password need to be
supplied as real environment variables at backend startup - see
`application.yml`'s `spring.mail` block.

---

## CR-031: SaaS subscription entitlement limits (Customer 360 §27-40)

The last major item from the Customer 360/Document Reuse master prompt.
`SubscriptionTier` (FREE/PRO/MAX, already existed since CR-027 as a pure
feature-gating flag - `SubscriptionService.requireTier()`, used only by
AI) now also carries per-tier numeric entitlement limits:

| Tier | Owners | Customers | Suppliers | Products |
|---|---|---|---|---|
| FREE | 1 | 100 | 100 | 1,000 |
| PRO | 2 | 1,000 | 1,000 | 10,000 |
| MAX | Unlimited | Unlimited | Unlimited | Unlimited |

New `EntitlementService` (`requireCanAddOwner/Customer/Supplier/Product()`,
`usageSummary()`) mirrors `SubscriptionService`'s own "one sanctioned
gate" pattern - each throws a 402 `ENTITLEMENT_LIMIT_REACHED`
`BusinessException` when the tenant's *active* count is already at its
tier's limit. Wired into `UserServiceImpl.create()` (only when the role
being assigned is OWNER), `CustomerServiceImpl.create()`,
`SupplierServiceImpl.create()`, `ProductServiceImpl.create()` - checked
first, before anything is built, so a rejected create never partially
writes. Deliberately untouched: `BootstrapOwnerInitializer` and
`TenantRegistrationServiceImpl` both build their very first owner row
directly (bypass `UserServiceImpl.create()` entirely) - a brand-new
tenant's first owner must never be blocked by its own tier's limit.

New `GET /v1/settings/usage` (`SETTINGS_VIEW`) returns counts vs. limits.
Shop Settings gained a "Plan usage" card: a progress bar per resource,
red at/over the limit, amber near it (>=80%), "Unlimited" text (never a
0%/negative bar) for MAX, plus an upgrade nudge when anything is close.

Backend: new `EntitlementServiceImplTest` (7 tests - FREE/PRO limits,
MAX-is-truly-unlimited, `usageSummary()` reports real counts), plus
entitlement-check tests added to `CustomerServiceImplTest` and
`UserServiceImplTest`. Full suite: 193/195 (same 2 pre-existing
BUG-ENV-002 Docker failures). Frontend `tsc -b --force`/`vite build`
clean.

**Live-verified end to end**, not just unit-tested: confirmed MAX tier
shows "Unlimited" on every row. Temporarily switched the primary test
tenant (already at 1,000+ customers/suppliers/products from earlier
load-testing, so guaranteed over any FREE limit with zero data seeding
needed) to FREE - confirmed the usage card immediately showed all four
rows red and over-limit, confirmed customer/supplier/product creation
were each rejected with the correct plan-specific message ("Your Free
plan allows up to 100 suppliers..."), then switched back to MAX and
confirmed creation works normally again. The tenant was left in exactly
its original working state - verified via a direct DB query after the
run, not just assumed.

This closes out both master prompts from this session's Customer 360
round - the only items left unbuilt anywhere in either spec are: Labour/
Team/Attendance, Finance/Cash-Bank-Cheque ledger, Reports (stock-value/
daily/weekly/monthly/yearly), Notifications v2, new AI tools, SAML
(all from the earlier ERP-expansion prompt, explicitly deferred since
CR-029), and Security Audit Log actor-role capture (deferred since the
§24-26 round, needs a migration + 5 call sites).

---

## CR-030 §24-26: Security Audit Log detail view

`SecurityAuditLogPage` gained a click-through detail dialog. The API
response (`SecurityAuditLogResponse`) already carried `ipAddress`,
`userAgent`, `requestId`, `entityType`, `entityId` - none of it was
actually shown anywhere in the UI: IP only appeared above the `xl`
breakpoint, and user agent/request id weren't rendered at all, on any
breakpoint. The detail dialog shows all of it plainly: When, Event,
Actor, Resource (entityType + entityId), Status, IP address, User agent,
Request ID. Frontend-only, no backend change - the data already existed.

`tsc -b --force`/`vite build` clean. Live-verified: clicked a real row,
confirmed the dialog renders with a real captured user-agent string and
request id.

**Explicitly not done this round**: showing the actor's *role* at the
time of the event. `SecurityAuditLog` has no `role_code`/`role_name`
column - `fullName` is a denormalized snapshot resolved separately by
5 different call sites (`AuthServiceImpl`, `UserServiceImpl`,
`RoleServiceImpl`, `SupplierServiceImpl`, `BootstrapOwnerInitializer`),
not one shared choke point, so adding role needs a new migration plus a
change at all 5 sites - a bigger, riskier change than anything else in
this round. Flagged as the next concrete step for this feature, not
silently dropped.

---

## CR-030 §18-23: archive/soft-delete safety audit + Customer reactivation fix

Audited Customer/Supplier/Product against "never hard-delete if
referenced, offer archive instead, allow restore." Found: all three
already never hard-delete (hard rule #7 - `softDelete()`/`deactivate()`
everywhere, confirmed by reading each service), all three list pages
already have an Active/Inactive status filter. Supplier and Product both
already let a deactivated record come back via editing status to ACTIVE
in their forms - **Customer was the one gap**: `CustomerRequest` had no
`status` field at all, so `PUT /v1/customers/{id}` could never touch it,
and `DELETE` (deactivate) was one-way through the UI with no way back.

Fixed to match the Supplier/Product precedent exactly: `status` added to
`CustomerRequest` (`@NotNull`, mirrors `SupplierRequest.status`),
`CustomerServiceImpl.applyRequest()` now sets it, `CustomerForm` gained a
Status select (Active/Inactive) shown only in edit mode - a brand-new
customer creation stays unchanged (always defaults to ACTIVE, no
clutter). The dedicated `DELETE`/deactivate shortcut still exists
alongside it, same coexistence pattern Supplier already has.

Backend `mvn -o test`: 183/185 (same 2 pre-existing BUG-ENV-002 Docker
failures, +1 new `updateCanReactivateCustomer` test). Frontend `tsc -b
--force`/`vite build` clean. Live-verified: deactivated a throwaway
customer from the list row's actions menu, confirmed "Inactive" on list
and detail pages, edited them and set Status back to Active, confirmed
"Active" again on the detail page with a real "updated" toast.

---

## CR-030 §12-13: Dashboard quick actions

The Dashboard header already had a "New invoice" button; it was missing
the equivalent "New quotation" one, now added next to it
(`frontend/src/modules/dashboard/pages/DashboardPage.tsx`,
`PERMISSIONS.QUOTATION_MANAGE`-gated). Deliberately did *not* build a
customer select-or-create picker in front of either button: both wizards'
Customer step is already free text with server-side auto-match-by-mobile
(CR-021), which is exactly "select existing or create new" without an
extra screen - adding a picker in front of an already-working flow would
have been a regression, not the improvement the spec was asking for.
Frontend-only, one-line change plus the missing button. `tsc -b --force`
and `vite build` clean. Live-verified: both buttons present, "New
quotation" navigates to `/quotations/new`.

---

## CR-030 §15: inline "Add product" from the Invoice/Quotation wizard

`ProductPicker` (`frontend/src/modules/invoice/components/ProductPicker.tsx`,
shared by both `InvoiceWizard` and `QuotationWizard`) now shows "Add
'{query}' as a new product" under a search that comes up empty, for a
`PRODUCT_MANAGE` holder only. Clicking it opens the exact same
`ProductForm` the Product module itself uses (categories/brands fetched
once, lazily, only the first time the dialog opens - not on every wizard
load), in a Dialog stacked on top of the wizard, with the product name
pre-filled from the search query (new `initialProductName` prop on
`ProductForm`, additive - existing callers unaffected). On save, the new
product is added as a line item immediately at its just-set price - no
navigation away from the invoice/quotation being built, matching this
whole master prompt's "no repeated data entry, no context loss" theme.

Frontend-only (reuses existing `POST /v1/products`, `GET
/v1/categories`, `GET /v1/brands`), no backend change. `tsc -b --force`
and `vite build` both clean. Live-verified: searched a brand-new product
name in a fresh invoice, got the "Add as new product" prompt, filled in
unit/selling price/MRP, saved, and confirmed it appeared as a correctly
priced (₹250.00 → ₹295.00 with GST) line item without ever leaving the
invoice wizard.

---

## CR-030 §45-46: "Repeat" action on Invoice/Quotation

---

## CR-030 §45-46: "Repeat" a previous invoice or quotation

A "Repeat" button on Invoice Detail and Quotation Detail navigates to a
new document pre-filled with the same customer (name/mobile, via the same
router-state pattern as §11's "New invoice"/"New quotation" quick
actions) and the same product lines at the same quantities - but never
the old price. `InvoiceWizard`/`QuotationWizard` gained an `initialItems`
prop; on mount, each `productId` is re-fetched via `productService.get()`
in parallel (`Promise.allSettled`, so one bad product can't break the
rest), using *today's* selling price, and any product that's since gone
INACTIVE or been deleted is silently skipped with a visible amber note
("N item(s) from the original document are no longer available and were
not re-added") rather than crashing or silently vanishing.

Frontend-only - reuses the existing `GET /v1/products/{id}` and document
GET endpoints, no backend change. `tsc -b --force` and `vite build` both
clean (run via `node_modules/.bin/tsc`/`vite` directly this round -
`npm run typecheck` and `npx tsc` both hit an unrelated shell-wrapper
quirk, `'"node"' is not recognized`, in this session's shell; the direct
binaries bypass it cleanly and are not a workaround for a real code
issue). Live-verified for both: repeated a real ₹177 invoice (Test
Hammer 500g × 1) and confirmed the Items step arrives with the same
product/quantity at today's price; created a fresh quotation and
confirmed its Repeat button does the same.

While testing this, also confirmed §43 (duplicate-customer detection) is
already fully built from an earlier round - `CustomerServiceImpl
.create()`/`update()` reject a duplicate mobile number server-side
(`DuplicateResourceException`), surfaced as a field-level error on the
Customer form. Verified, not rebuilt.

---

## CR-030 §17: credit-limit warning at invoice creation

## CR-030 §17: credit-limit warning at invoice creation

New `GET /v1/customers/credit-check?mobile=...` (`CUSTOMER_VIEW`, 200 if a
customer exists at that exact mobile number, 404 if not - a brand new
walk-in, not an error). The Invoice wizard's Customer step is free text,
not a picker (CR-021's original design, deliberately unchanged), so this
is how the wizard can still know about an existing customer's credit
limit: a debounced (400ms) lookup fires as the 10-digit mobile is typed,
and the Review step shows an amber, non-blocking warning
("{name}'s outstanding balance would become ₹X, over their ₹Y credit
limit") whenever `outstandingBalancePaise + thisInvoice'sBalanceDue >
creditLimitPaise`. A `creditLimitPaise` of 0 (the column's default,
meaning "no limit configured") never warns - checked explicitly, not
inferred from a falsy value. Advisory only, same as the existing
`paymentTooHigh` check in the same wizard - the OWNER can still save past
it; this is a warning, not a business rule the server enforces.

Backend: `CustomerService.creditCheckByMobile()`, `CustomerServiceImplTest`
(+2 tests). Full suite: 182/184 (same 2 pre-existing BUG-ENV-002 Docker
failures). Frontend `tsc -b --force` and `vite build` both clean (run via
the binaries directly - `npm run typecheck`/`npx tsc` hit an unrelated
shell-wrapper quirk in this session, `'"node"' is not recognized`, that
`node_modules/.bin/tsc`/`vite` bypass entirely; not a code issue).
Live-verified: gave a real customer (Ram Sangar) a ₹1,000 credit limit,
built an invoice that would push their balance to ₹1,062, confirmed the
exact warning text renders on Review, and confirmed the invoice still
saves normally afterward (advisory, not a block).

---

## BUG-INV-001: overselling drove stock negative, no check anywhere

## BUG-INV-001: overselling drove stock negative, no check anywhere

Found during a standing-instruction re-crawl of Dashboard/Stock/Quotations
after CR-030 shipped: the Stock page showed a real product at **-1 ROLL**
on hand - physically impossible. Root cause: `StockServiceImpl
.applyMovement()`, the single method every stock mutation passes through
(sale, sale-reversal, manual adjustment), never checked whether the
resulting balance would go negative. Fixed by rejecting any movement that
would do so, with a clear `INSUFFICIENT_STOCK` `BusinessException`
("Not enough stock of X: N on hand, M requested") thrown before anything
is written - protects every current and future caller through the one
choke point, not just invoice sale. Full detail, including why the
boundary (sell-to-exactly-zero) is allowed and overselling is not, in
`BUG_REGISTRY.md`.

Regression test: `StockServiceImplTest` (new, 2 tests). Full backend
suite: 178/180 (same 2 pre-existing BUG-ENV-002 Docker failures). Live
re-verified after restart: Playwright-driven attempt to sell more of a
product than is on hand now returns a clean 422 with the friendly
message, not a 500 or silent negative balance; a normal in-stock sale
still succeeds unaffected. The pre-existing bad test-data row was
corrected back to a real, non-negative quantity via a manual stock
adjustment (dev/test data only, no real-world consequence).

---

## CR-030: Customer 360 Phase 1 (document reuse, no re-entry)

Phase 1 of the Customer 360/Document Reuse master prompt. See CR-030 in
`CHANGE_REQUEST_REGISTRY.md` and the two new rows in `API_REGISTRY.md`.

**Built**: Customer Detail page gained an Invoices/Quotations/Products-
purchased tab set backed by two new endpoints (`GET
/v1/customers/{id}/quotations`, `GET /v1/customers/{id}/products`) - the
product-history one is a native aggregate query grouping invoice lines by
product with a correlated subquery for "last price paid" (most recent
non-cancelled invoice line for that product). "New quotation"/"New invoice"
quick-action buttons on Customer Detail navigate to the wizard with
`router state` carrying the customer's name/mobile/email, which the wizard
merges into its default values - pre-fills, never locks, so the existing
free-text + auto-match-by-mobile flow is untouched for a walk-in customer.
Deliberately chose this router-state approach over retrofitting a full
customer-picker into InvoiceWizard/QuotationWizard - a bigger, riskier
change to an already-working flow, avoided this late in the session.

**Live-verified** via Playwright as OWNER against customer id 6 (Ram
Sangar, real invoice/quotation history): page loads clean, all three tab
labels present and switchable, zero HTTP 500s/page errors. Clicked "New
invoice" from Customer Detail - landed on `/invoices/new` with Customer
name = "Ram Sangar", mobile = "6374005608", email pre-filled, and the
existing "A returning customer's existing record is reused automatically"
hint still shown underneath (confirmed by screenshot). Same result for
"New quotation".

**Verified**: backend `mvn -o test` 176/178 (same 2 pre-existing
BUG-ENV-002 Docker failures, +3 new `CustomerServiceImplTest` tests for
`recentQuotations`/`productHistory`). Frontend `tsc -b --force` and `vite
build` both clean.

**Not yet built** (rest of the Customer 360/Document Reuse master prompt,
explicitly deferred): inline "Add Product" during quotation/invoice
creation when a product doesn't exist yet; dashboard-level "+ New
Quotation"/"+ New Invoice" with select-existing-or-create-new customer;
"Repeat" action to prefill a new document from a previous one's line
items; customer duplicate-detection warning; credit-limit warning at
invoice creation; archive/soft-delete safety for Customer/Supplier/Product
("referenced -> offer archive, not hard delete") with an Archived filter;
Security Audit Log detail view (actor name+role, IP, sensitive masking);
SaaS subscription entitlement limits (FREE/PRO/MAX enforced server-side).

---

## CR-029: Project Management (Phase 6), Gemini AI provider, BUG-PROJ-001

Full Module 8 (Project Management) built and live-verified end to end, plus
Gemini added as a second AI provider. See CR-029 in
`CHANGE_REQUEST_REGISTRY.md`, the Project Management section of
`DATABASE_REGISTRY.md`/`API_REGISTRY.md`, and BUG-PROJ-001 in
`BUG_REGISTRY.md` for full detail.

**Built**: `work_type` (user-extensible), `project` (two-field lifecycle -
`status` + nullable `outcome`, never one flat enum), `project_material`
(supplier deliberately optional), `project_expense` (manual
LABOUR/EMPLOYEE/FOOD/STAY/PETROL/OTHER ledger - a stand-in until a future
Labour module exists), `project_payment`. Server-computed profitability
(revenue = contract value, never sum of payments received) on every read -
never trusted from the client. A rooftop-sheet material calculator (the one
worked formula the request specified: area + overlap% + wastage%, divided
by sheet area, rounded up). Full frontend: list/detail/create/edit pages,
a Materials/Expenses/Payments tabbed detail view, inline "Add work type",
a Customer/Supplier picker pattern reused across the module. Gemini added
as a second `ChatCompletionClient` implementation (`app.ai.provider`,
`gemini` now default) - still needs a real `GEMINI_API_KEY` to actually
answer anything.

**BUG-PROJ-001 (found + fixed live)**: the first real "Add material" click
in a browser 500'd - `ProjectMaterial`/`ProjectExpense`/`ProjectPayment`
don't extend `BaseEntity`, so their plain `created_at` columns were never
populated, and PostgreSQL's `NOT NULL` constraint rejected every insert.
Also found and fixed during the same restart cycle: `work_type`'s `V18`
migration forgot the `created_by`/`updated_by` columns `BaseEntity`
requires (`V19` fix). Both caught by real startup/live-testing, not by
review - `ddl-auto: validate` refused to start until `V19` was added, and
the `create_at` bug only surfaced once a real browser session tried to
save a material.

**Verified**: `mvn -o test` 173/175 (2 pre-existing BUG-ENV-002 Docker
failures, unrelated; +13 tests this round: 7 `MaterialCalculatorServiceImplTest`,
7 `ProjectServiceImplTest`, 3 `ProjectChildRecordCreatedAtTest` regression
tests for BUG-PROJ-001). Frontend `tsc -b --force` and `vite build` both
clean. **Live-verified**, not just tested: full browser flow - create
project → add material (no supplier, confirming optional) → add expense →
record payment → change status to Completed with a Success outcome → all
financial summary figures (material cost ₹650, expenses ₹5,000, total cost
₹5,650, net profit ₹1,44,350, margin 96.23%, received ₹50,000, balance
receivable ₹1,00,000) verified arithmetically correct in the screenshot →
rooftop calculator (3m×4m room, 1m×2m sheet, 10% overlap, 5% wastage → 7
sheets, matching the hand-computed unit test exactly). Also re-verified
Quotations, Stock (low-stock), and Dashboard (profit/sales figures) still
load clean with zero regressions from this round's changes.

**Known gap, stated plainly**: adding a project material does not
decrement shop stock or write a `stock_movement` row (`PROJECT_CONSUMPTION`
from the request's own §12 spec) - the ledger itself is built and correct,
but it isn't wired into `StockService` yet. Flagged for the next pass, not
silently claimed as done.

**Not yet built** (explicitly deferred, matching the request's own phase
order): Labour/Team/Attendance (Phase 7), Finance/Cash-Bank-Cheque ledger
(Phase 8), Reports including the stock-total-value/daily/weekly/monthly/
yearly reports (Phase 9), Notifications v2 payment reminders (Phase 10),
new AI business-data tools beyond CR-027's original set (Phase 11), SAML
(Phase 12).

---

## Live sidebar crawl as a signed-in tenant user, BUG-SEC-002 found and fixed

Logged in as the tenant OWNER and crawled all 17 sidebar-reachable pages
with console/network-error capture enabled, looking specifically for
anything not yet caught by this session's targeted testing. Found one real
bug: the **Security log page 500'd unconditionally** - `GET
/v1/security-audit-logs` failed with `could not determine data type of
parameter $6` on every single call, for every tenant, since the page has
apparently never been live-clicked before (it's existed since CR-013).

Reported the bug through the application's own **Contact admin chat**
(header user menu) as a live test of that pathway, not just a description
in this file - confirmed it actually landed in `notification_log`
(`LOGGED_ONLY`, since no admin email is configured, exactly as designed)
before fixing anything.

Root cause and fix: the exact BUG-SUP-004/BUG-PAY-001 class of defect
(bare `(:param is null or ...)` on an untyped `LocalDateTime`/enum
parameter, which PostgreSQL's prepared-statement planner can't infer a type
for) - `SecurityAuditLogRepository.search()`'s `:action`/`:from`/`:to`
filters needed the same `cast(:param as ...)` treatment `PaymentRepository`
already has. Full detail and a codebase-wide audit confirming no other
latent instance remains: BUG-SEC-002 in `BUG_REGISTRY.md`.

Verified: `mvn -o test` 160/162 (same pre-existing Docker gap), live
browser re-test of the Security log page at default filters and with an
explicit action filter, both clean.

---

## ERP expansion — Phase 0 (audit) + Phase 1 (CR-018) — 2026-08-23

The owner requested a large expansion (Project Management, Labour, Finance/
Cash/Bank/Cheque, Reports, Notifications v2, AI v2, SAML — 13 new modules on
top of the original 12) via a 60-section spec, with an explicit Phase 0
audit requirement before any code. Full detail: `MASTER_PROJECT_STATUS.md`
(new file, this round).

**Phase 0 (audit)**: read every registry file plus live DB/permission state.
**Major finding**: `PROJECT_REGISTRY.md`, `FEATURE_REGISTRY.md`,
`MODULE_DEPENDENCY_MAP.md`, `SECURITY_REGISTRY.md`, `VERSION.md` had all
drifted stale since roughly CR-020/021 (2026-08-22) - missing Customer,
Quotation, Payment, Coupons, Tenant-registration, Notifications,
Subscription, AI, and the CR-028 security fix entirely. `PROJECT_REGISTRY.md`
also had an outright wrong money-type row (`DECIMAL(15,2)`; the codebase has
used `BIGINT` paise since Module 6/7). Corrected `PROJECT_REGISTRY.md`'s
module table and money row, and added the missing multi-tenancy section to
`SECURITY_REGISTRY.md`, in this round. `FEATURE_REGISTRY.md`/
`MODULE_DEPENDENCY_MAP.md`/`VERSION.md` are still stale - larger rewrites,
scheduled but not done yet.

**Key design decisions recorded** (full reasoning in
`MASTER_PROJECT_STATUS.md` §4): Project lifecycle as two orthogonal fields
(`status` + nullable `outcome`, not a flat enum); all new money fields
`BIGINT` paise; Finance as an append-only `financial_transaction` ledger with
derived (not cached) balances, matching the codebase's existing
activity_log/security_audit_log/notification_log/stock_movement pattern;
Supplier Payables correctly identified as blocked on Purchase (still not
started); AI provider abstraction already exists (`ChatCompletionClient`) -
adding Gemini free-tier is one new implementation, not a redesign.

**Phase 1 (CR-018 — supplier bank account encryption, applied)**: the one
concretely-scoped item from the "close existing gaps" phase that was still
open (Supplier wizard, login polish, permission grouping were already done
in earlier rounds). AES-256-GCM at the entity boundary
(`common/security/FieldEncryptor` + `BankAccountNumberConverter`), new
`SUPPLIER_VIEW_BANK_ACCOUNT` permission (OWNER only by default),
`GET /v1/suppliers/{id}/bank-account-number`, audited to
`security_audit_log` via a new `BANK_ACCOUNT_REVEALED` action, one-time
backfill runner for the 12 suppliers that had a real bank account number,
frontend eye-toggle. Full detail: CR-018 in `CHANGE_REQUEST_REGISTRY.md`'s
"As built" note.

**Verified**: `mvn -o test` 160/162 (2 pre-existing BUG-ENV-002 Docker
failures, unrelated; +9 tests this round: 7 `FieldEncryptorTest`, 2
`SupplierServiceImplTest`). Frontend `tsc -b --force` and `vite build` both
clean. **Live-verified**, not just tested: restarted the backend with a real
`APP_ENCRYPTION_KEY`, confirmed the startup log encrypted exactly the 12
real rows, confirmed via `psql` the DB column now holds ciphertext,
confirmed the reveal endpoint returns the correct number matching the
masked last-4, confirmed the audit row landed, and drove the actual
eye-toggle in a real browser session as OWNER (reveal → re-mask) and as
MANAGER (masked visible, reveal button correctly absent - holds
`SUPPLIER_VIEW` but not the new permission).

**CR-019 (per-user theme/language) remains the last open Phase 1 item.**

**Next**: Phase 6 (Project Management) is the first genuinely new module -
migration + entities first (work_type, project, project_material,
project_expense, per §4.1/4.6 of `MASTER_PROJECT_STATUS.md`), verified by
compile + unit tests, then frontend, then live browser verification, same
pattern as every module this session.

---

**Updated:** 2026-08-23 (CR-028 round)
**Overall project completion: ~58%** (adds coupons, tenant self-registration,
contact-admin support, a critical cross-tenant security_audit_log fix, and
bulk load-test data across the existing modules)

---

## CR-028 round (this session): bulk data, RBAC/cross-tenant audit, security log fix, contact admin, tenant registration, coupons

One dense combined request - see CR-028 in `CHANGE_REQUEST_REGISTRY.md` for
full scope. Executed in priority order: security first (RBAC audit, then the
one real gap it found), then the two new user-facing flows (contact admin,
tenant registration), then the coupon feature end to end, then live
verification (real second tenant, live browser click-through, cross-device
screenshots).

**Bulk load-test data**: 1000 suppliers, 1000 customers, 10000 products
added to the existing tenant via a set-based SQL script (not the API, for
speed) - confirmed live on the Dashboard (Products 10015, Suppliers 1014,
Customers 1006, reflecting the pre-existing seed rows plus the bulk insert).

**Full RBAC audit** (static, all `@PreAuthorize` across 19 controllers
cross-referenced against `API_REGISTRY.md`) **plus live cross-tenant
penetration testing** (a real second tenant, "Rival Hardware Co", created
end to end through the actual API - not fabricated data - then every CRUD
verb attempted against every resource type as the other tenant's user):
found and fixed one real gap.

**BUG-SEC-001 (CRITICAL, fixed)**: `GET /v1/security-audit-logs` had zero
tenant scoping - any tenant's OWNER could read every other tenant's login
attempts, password resets and role changes. Root cause and fix in
`BUG_REGISTRY.md`. Regression test added; re-verified live against the real
second tenant after the fix (each tenant now sees only its own events).

**Contact admin**: `POST /v1/notifications/contact-admin`, any authenticated
user, reuses the existing SMTP/notification-log infrastructure from CR-027.
Frontend: a "Contact admin" item in the header user menu opening a small
subject+message dialog.

**Tenant self-registration**: `POST /v1/tenants/register` (public,
rate-limited 5/hour/IP) - the real second-tenant provisioning flow CR-016
explicitly deferred, now built. Creates a new tenant, its 4 default roles
(permissions mirrored from `V1`'s seed, including the new coupon
permissions), and an owner account, atomically. Shows the 3 subscription
tiers (FREE/PRO/MAX) during signup, self-declared same as Shop Settings -
still no payment gateway. Frontend: `/register` page, linked from Login.
Login stays identifier-only and mobile/email stay globally unique across
tenants - CR-016's standing trade-off, unchanged; a duplicate identifier is
rejected with 409 regardless of which tenant is registering.

**Coupons**: tenant-scoped discount codes (`V16` migration), percent or
flat, optionally capped, optionally minimum-purchase-gated, optionally
restricted to specific products, optionally usage-limited and/or
date-windowed. `POST /v1/invoices` gained an optional `couponCode` field -
`CouponServiceImpl.calculateDiscount()` allocates the discount
proportionally across eligible line items (last line absorbs the rounding
remainder) and re-derives each line's GST from its own rate on the reduced
subtotal, so mixed-GST-rate carts stay correct. `InvoiceResponse` gained
`couponCode`/`discountDisplay`; the invoice PDF gets a "Subtotal (before
discount)" + "Discount (CODE)" pair of rows when a discount applies.
Frontend: a full Coupon admin module (list/create/edit, `/coupons`, gated by
new `COUPON_VIEW`/`COUPON_MANAGE` permissions) plus a "Coupon code
(optional)" field on the Invoice creation wizard's Payment step and a
discount line on the Invoice detail page. **Quotation gained the same two
schema columns (`coupon_id`, `discount_paise`) for symmetry but is not yet
wired to actually redeem a coupon** - deliberately deferred, see
`DATABASE_REGISTRY.md`'s `V16` section for the reasoning; a quotation is a
non-binding price document, not a sale, so this was judged lower priority
than the Invoice path the user's request was actually describing.

**Verified**: backend `mvn -o test` - 151/153 pass, the only 2 failures are
the pre-existing BUG-ENV-002 Docker/Testcontainers gap (unrelated to this
round); one self-inflicted test breakage (`QuotationServiceImplTest`'s
hand-built `InvoiceResponse` mock hadn't been updated for the two new record
components) was caught by this same run and fixed before it could be called
done. Frontend `tsc -b --force` and `vite build` both clean.

**Live-verified in a real headless-Chrome session** (Playwright-core against
the running dev servers, real tenant data) - and this surfaced one more real
issue, caught the same way BUG-PAY-001/DASH-001/AI-001 were in CR-027:

- Created a real coupon through the admin UI, applied it at real invoice
  creation through the wizard, and the resulting invoice's API response came
  back with **no** `couponCode`/`discountDisplay` even though the database
  row was correct (`coupon_id` set, `discount_paise` correctly computed).
  Root cause: not a code defect - the **running backend process predated**
  the `InvoiceResponse`/`InvoiceMapper` changes that added those two fields,
  and had never been restarted since. Restarted it; re-fetched the same
  invoice; the fields appeared correctly, matching the database. Re-ran the
  full browser flow end to end afterward and the Invoice detail page now
  shows "Discount (CODE) -₹X.XX" correctly. Worth remembering: a backend
  code change is not "live" just because `mvn test`/`compile` passed - the
  running process has to actually be restarted, and this session's own
  earlier "live-verified via curl" note for the coupon feature was made
  against a process that was later never restarted, so it had gone stale
  without anyone noticing until this round's live click-through caught it.
- Confirmed restricted-page navigation for a low-privilege user: logged in
  as a seeded STAFF account (no `SETTINGS_VIEW`), navigated directly to
  `/settings/shop` by URL - got "You do not have access to this page. Ask
  the shop owner to grant the required permission on your role." rather
  than the page rendering, confirming the frontend route guard holds even
  on direct navigation (not just hidden nav links).
- Confirmed cross-tenant isolation from the frontend side too, not just via
  curl: the same STAFF user (tenant 1) navigating directly to
  `/invoices/5` - a real invoice id belonging to the "Rival Hardware Co"
  fixture tenant - got a not-found result, not the other tenant's invoice.
- Cross-device responsive check: screenshotted Dashboard and Invoice detail
  at 5 viewports (iPhone 390×844, iPad 768×1024, laptop 1366×768, 1080p
  desktop 1920×1080, MacBook 16" 1728×1117) - sidebar correctly collapses to
  a hamburger menu below tablet width, dashboard stat cards reflow from a
  single column (mobile) through 2-up (tablet) to a 3+2 grid (laptop/
  desktop), no horizontal overflow at any width, invoice detail's summary
  card and action buttons both remain fully usable down to the smallest
  width tested.

**Not yet done**: "fetch restricted data via the AI assistant" specifically
was not live-tested (no `ANTHROPIC_API_KEY` configured in this environment,
same gap noted in CR-027 - the AI widget correctly refuses to attempt a
call) - covered structurally instead, via the RBAC audit confirming every
`AiTool` is only offered to the model if the caller holds that tool's
permission, so even a working AI call could not be tricked into a tool it
has no permission for. A full page-by-page click-through of every existing
module (Suppliers, Products, Categories, Brands, Stock, Quotations, Users,
Roles) was not repeated this round - those were each already live-verified
in their own CR round (see below); this round's click-through focused on
what was newly built (Coupons, Register, Contact admin) plus the responsive
pass. Quotation coupon redemption (see above) remains unbuilt.

---

## CR-027 round: Payment module, notifications, subscription tiers, AI assistant

Four features in one combined request - see CR-027 in
`CHANGE_REQUEST_REGISTRY.md` for full scope. Split across two background
agents (Payment module, Notification system) plus direct work (subscription
tier foundation, AI assistant) to keep the four independent enough not to
conflict on the same files.

**Built:**
- **Payment module** (`invoice/` package additions): `GET /v1/payments` -
  cross-invoice search/filter/history, previously only visible one invoice
  at a time. Read-only; payment creation is unchanged, still on the Invoice
  detail page. Sidebar's "Payments" link flipped from `available: false` to
  `true`.
- **Notifications** (new `notification` package): real email (extends the
  existing SMTP setup beyond password-reset) plus a genuinely pluggable
  SMS/WhatsApp `NotificationProvider` interface - implemented today as a
  logging stub (`LOGGED_ONLY` in `notification_log`) since no real
  SMS/WhatsApp provider account exists; swapping in a real one later is one
  new `@Component`, not a redesign. Triggers: invoice created, payment
  received. `GET /v1/notifications/log` for the audit trail.
- **Subscription tiers** (`FREE`/`PRO`/`MAX`, `tenant.subscription_tier`):
  feature gating only, self-declared by the owner in Shop Settings since
  **no payment gateway is wired in** - stated plainly in the UI so it never
  reads as a working checkout. Every feature that existed before this round
  stays on every tier; only Notifications (Pro+) and the AI Assistant (Max)
  are gated, so no existing tenant lost anything.
- **AI Assistant** (new `ai` package): a read-only, permission-aware chat
  assistant over the tenant's own data - customer balances, low stock,
  sales summary, invoice search. Deliberately NOT a free-form SQL/JPQL
  generator (a real cross-tenant leak risk in a multi-tenant app) - it calls
  a small fixed set of `AiTool`s, each delegating to an already tenant- and
  permission-scoped existing service. Talks to Anthropic's Messages API
  directly over `java.net.http` (no SDK dependency added), handling the
  tool-use round trip internally. **Needs `ANTHROPIC_API_KEY` to actually
  answer** - unset today, so it correctly replies "isn't set up yet" rather
  than attempting a call that would 401. Frontend: a floating chat widget
  (`AiChatWidget`) shown to every tier, with an upgrade prompt in place of
  the chat input below Max.

**Verified:** backend `mvn -o compile` and full `mvn -o test` (137/137 pass
excluding the same pre-existing BUG-ENV-002 Docker gap, 0 unexplained
failures) - including `PaymentServiceImplTest` (3), `NotificationServiceImplTest`
+ `InvoiceServiceImplTest` (17), `AiChatServiceTest` (4), and a new
`DashboardServiceImplTest` (2, added while fixing BUG-DASH-001 below).
Frontend `tsc -b --force` and `vite build` both clean. `V14`/`V15`
migrations applied cleanly against the existing populated database (real
tenant "Siva Hardware shop"), confirming no migration conflict with real
data.

**Live-clicked in a real browser session** (headless Chrome via
Playwright-core, driven against the running dev servers with the real
tenant's data) - Payments list, Shop Settings' new Subscription plan card
(changed FREE -> MAX -> back to FREE), the AI chat widget (asked "what's
low on stock?", got the correct "isn't set up yet" reply), and the
Dashboard. This surfaced three real bugs invisible to compile/test/build,
all found, root-caused and fixed live, with the backend restarted and
re-verified after each fix - see `BUG_REGISTRY.md` BUG-PAY-001 (Payments
500'd whenever a filter was left at its default - same untyped-null-check
class as BUG-SUP-004, missed in the new query), BUG-DASH-001 (Dashboard's
sales-summary cards 500'd with real invoice data - a Spring Data
`Object[]`-vs-`List<Object[]>` unwrapping bug that had zero test coverage
since it shipped in CR-023), and BUG-AI-001 (the AI widget always showed a
generic error despite the API call itself succeeding - `AiChatController`
skipped the `ApiResponse` envelope every other controller uses). All three
are exactly the kind of thing "verify at three levels: API -> database ->
browser" exists to catch - compile and unit tests alone would have shipped
all three.

**Not yet done:** no frontend notification-log viewer (backend-only, by
design - the notification agent judged it lower priority than backend
correctness given the time available); a real SMS/WhatsApp provider and a
real `ANTHROPIC_API_KEY` are both still needed before Notifications/AI
actually do anything beyond logging/refusing.

---

## CR-026 round: Settings view/edit, live chrome refresh, PDF preview/polish, numeric input fix

Dense bug report + feature request, addressed directly - see CR-026 in
`CHANGE_REQUEST_REGISTRY.md` for full scope, and BUG-FE-002/003/004 in
`BUG_REGISTRY.md` for the three real bugs found and fixed.

**Real bugs fixed** (see BUG_REGISTRY.md for root cause detail on each):
- BUG-FE-002: Sidebar shop name/logo and the header avatar never reflected
  a Settings/Profile save without a full page reload. Fixed with a new
  `frontend/src/layouts/AppChromeProvider.tsx` context wrapping `AppLayout`.
- BUG-FE-003: a numeric field defaulting to 0 showed "0100" instead of
  "100" as the user typed (`<input type="number">` never strips a leading
  zero live). Fixed with a new controlled `frontend/src/shared/components
  /ui/number-input.tsx`, applied via `Controller` across Product, Supplier,
  Customer, Invoice and Quotation forms (the Invoice payment-amount and
  Stock adjustment-quantity fields were deliberately left alone - both are
  string-typed with empty-string defaults, not numeric-zero defaults, so
  the bug doesn't reproduce there).
- BUG-FE-004: a manually-typed lowercase product/category/brand code
  failed validation outright (regex required uppercase but never
  transformed it) - the record was silently never created. Fixed by adding
  `.toUpperCase()` to `codeRules` in `product/validation/schemas.ts`,
  matching the pattern already used for `gstNo`/`panNo`/`bankIfsc`.

**Built**: Shop Settings view/edit toggle (`ShopSettingsPage.tsx`, matching
the `UnsavedChangesDialog` pattern already used for Customer detail);
Invoice + Quotation detail pages both gain a "Preview" action (opens the
PDF in a new tab via an object URL, `frontend/src/shared/lib/utils.ts`'s
new `previewBlob()`) alongside the existing Download; a brand-new
`QuotationPdfService` (`backend/.../quotation/pdf/`, `GET
/v1/quotations/{id}/pdf`) since Quotation had no PDF at all before this -
titled "QUOTATION" not "TAX INVOICE", no payment/bank/QR section since
nothing is due yet, adds a "Valid Until" line; `tenant_upi_qr` (new
1:1 `bytea` table, `V13`, same pattern as `tenant_logo`/`tenant_signature`)
lets the shop upload an existing UPI/GPay QR code image directly, which
`InvoicePdfService.paymentBlock()` now prefers over the auto-generated QR
when present; invoice PDF gets a light-tinted payment section, a dashed
divider before it, a "Thank you for shopping with us!" line, and clearer
"computer-generated invoice" wording.

**Verified**: backend `mvn -o compile`/`test-compile` clean; the two new
PDF-rendering test classes (`InvoicePdfServiceTest` 6/6,
`QuotationPdfServiceTest` 4/4 new) pass and were visually inspected via
generated sample PDFs (`target/sample-invoice.pdf`,
`target/sample-quotation.pdf`) - confirmed the light tint/divider/
thank-you line render correctly; full `mvn -o test` runs 121/123, the 2
failures are the pre-existing BUG-ENV-002 Docker/Testcontainers gap,
unrelated to this round; frontend `tsc -b --force` and `vite build` both
clean. `python3 registry/static_check.py` still NOT RUN - no Python
interpreter in this environment (unchanged from prior rounds).

**Not yet done**: no human/browser click-through this session (verified by
compile/test/build + visual PDF inspection only); no automated regression
test for the three frontend bugs (no component/interaction test
infrastructure exists in this codebase yet - same gap noted for
BUG-FE-001).

---

## CR-023 round: Customer module, image storage, master data, sidebar/dashboard/UX

40-section enhancement request. Investigated first (per its own Phase 1
instruction) before writing code - see CR-023 in CHANGE_REQUEST_REGISTRY.md
for the full gap analysis and scope decisions.

**Built**: full Customer module (Module 5 proper - `CustomerController`,
list/detail/create/edit, financial summary from real invoice/payment data,
CUSTOMER_VIEW/CUSTOMER_MANAGE now actually enforced); image storage
foundation (`user_avatar`/`tenant_logo`/`tenant_signature`, Postgres
`bytea`, deliberately NOT columns on `app_user`/`tenant` since both are
reloaded on every request - see DATABASE_REGISTRY.md); profile photo
upload (ProfilePage); shop logo + digital signature (draw via
`react-signature-canvas` or upload) in Shop Settings, both wired into the
invoice PDF; shop name now editable and reflected dynamically in the
sidebar (replacing the static `APP_NAME`); Indian state master (auto-fills
GST state code) and bank master (with "Other") wired into
Supplier/Customer forms - no schema change, both are frontend-only
picklists; sidebar sections are now collapsible (default expanded);
copyright footer ("© {year} U.Ram sangar"); Supplier field labels reworded
to "Supplier Shop Name"/"Contact Person Name" (JSX text only); dialog close
buttons get a red hover state globally (one change in `dialog.tsx`);
reusable `UnsavedChangesDialog` (Continue Editing / Discard / Save),
wired into Product's existing dialog and the new Customer dialog/detail
view-edit toggle; Dashboard gained Total Sales/Today's Sales/Outstanding
Customer Balance (new `/v1/dashboard/sales-summary` aggregate endpoint)
plus Recent Quotations/Recent Customers cards.

**Deliberately not built, with reasons** (see CR-023): Purchase
sub-navigation (module doesn't exist - locked order, CR-011); Customer
Returns/Damage tracking (no such concept exists anywhere in the schema,
spec's own instruction says not to fabricate it); tag/chip multi-select UI
(no field on Product is actually many-to-many); per-bank exact
account-number length rules (no authoritative source, used a generic
9-18-digit range instead); Supplier detail page's inline view/edit toggle
(built for Customer as explicitly requested; Supplier still uses its
existing separate-page wizard - a retrofit is straightforward with the
same `UnsavedChangesDialog` piece if wanted next).

**Verified**: backend compiles clean, `mvn test` (non-Docker) all pass,
V11 migration applied live (confirmed via `psql`), frontend `tsc` and
production build both clean, new routes checked in headless Chrome
(correct login redirect, no crash) - but not manually clicked through by a
human in a real logged-in session.

---

## EXACT NEXT ACTION

This round (CR-022) added: Invoice PDF generation, shop GST/address
settings, Customer GST capture from the Invoice/Quotation flow, and a full
Quotation module (Module 10) - built ahead of Purchase in the locked order
since it only depends on Product (already done), not Inventory/Purchase
pricing. `mvn clean verify` passes 113/118 runnable tests (the same
BUG-ENV-002 Docker/Testcontainers npipe issue blocks the other 5, unrelated
to this round's changes); frontend typecheck and production build both
clean. Next:

1. Resolve BUG-ENV-002 (still open, unchanged): Docker Desktop's npipe
   connectivity blocks Testcontainers-dependent test classes on this
   machine. Host-level decision, left to the owner.
2. Write backend tests for Category/Brand/Product (still 0).
3. Manually click through the Quotation wizard, Convert-to-Invoice, Shop
   Settings, and the Invoice PDF download in a real browser - verified via
   curl/psql, headless-Chrome render checks (no crash, correct redirect),
   and unit tests, but not a human click-through this session.
4. Module 2/3/4/7/10 Postman collections and docs (only Module 1 has them).
5. GST split (CGST+SGST vs IGST) depends on both shop and customer having a
   state code set - until Settings is filled in, every invoice defaults to
   intra-state. Worth a banner nudging the owner to fill in Settings once.
6. Then: full Customer management UI (Module 5), Purchase, Product Variant
   - Purchase before anything that needs a real purchase-price history.

---

## Module status

| Module | Backend | Frontend | Seed | Tests | Postman | Docs | Status |
|---|---|---|---|---|---|---|---|
| 1 Authentication & Users | done | done | done | 149 written, core paths verified live | done | 6/18 PDFs + 4 MD | **IN PROGRESS** |
| 2 Supplier | done | done | done | 35 written, core paths verified live | not started | not started | **IN PROGRESS** |
| 3 Category, Brand & Product | done | done | done (14 products total) | **0 written** - verified live only | not started | not started | **IN PROGRESS** |
| 4 Inventory (stock, stock_movement) | done | done (Stock list + adjust dialog) | done (opening stock for 12 products) | 0 written - exercised via Invoice tests + live curl | not started | not started | **IN PROGRESS** |
| 7 Invoice & Payment (+ minimal Customer, CR-021) | done | done (4-step create wizard, list, detail, record-payment, cancel) | not started | **10 written, all passing** | not started | not started | **IN PROGRESS** |
| Multi-tenancy foundation (CR-016) | done | n/a | n/a | 0 written | n/a | CR-016 in registry | **IN PROGRESS** |
| Customer (full management UI) | - | - | - | - | - | - | not started - CR-021 built only a minimal backing table |
| Product Variant (price history, loss-sale workflow) | - | - | - | - | - | - | deferred, see FEATURE_REGISTRY |
| Purchase | - | - | - | - | - | - | not started |
| Sales / Quotation | - | - | - | - | - | - | not started |

## Completion percentage

| Area | Done | Notes |
|---|---|---|
| Backend source | 155+ Java files | Modules 1, 2, 3, 4, 7 + tenant foundation; compiles clean |
| Backend tests | 194 `@Test` methods | 0 for Module 3; 99/99 non-Docker tests pass; 7 Testcontainers-dependent tests blocked by BUG-ENV-002 |
| Migrations | V1-V9 + V900-V902 | All applied live to PostgreSQL 16; V8/V9 added this session (CR-021) |
| API endpoints | 49 | +6 today: 4 `/v1/stock/**`, 4 `/v1/invoices/**` (some counted once for base path) |
| Frontend | 115+ files, Modules 1-4, 7 | typecheck clean, production build clean |
| Postman | 54 requests, Module 1 only | Modules 2, 3, 4, 7 pending |
| Documentation | 6 PDFs + 4 module MD files | 12 PDFs pending, no Module 2/3/4/7 docs yet |
| **Modules complete** | **0** | every module needs Postman + docs + full test coverage to close out |

---

## Completed since the last resume point

### Invoice PDF, shop GST settings, Quotation module (CR-022, 2026-08-22, later still)

Owner asked for: invoice PDF download, a way to see invoices by month, a
Quotation feature independent of whether the customer buys, GST number on
Customer, and shop GST number + signature on the GST bill. Built as one
connected change:

- **Migration `V10`**: `tenant` gains `gst_no`/`address_line1/2`/`city`/
  `state_code`/`pincode`/`signatory_name` (all nullable); `customer` gains
  the same address/state columns (`gst_no` already existed since CR-021 but
  was never settable); new tables `quotation`/`quotation_item`, structurally
  identical to `invoice`/`invoice_item` minus payment columns.
- **`InvoicePdfService`** (`invoice/pdf/`): builds a GST tax-invoice as an
  HTML string, rendered to PDF via `openhtmltopdf-pdfbox` (new dependency,
  Apache-2.0). CGST+SGST vs IGST is computed at render time by comparing
  `tenant.stateCode` to `customer.stateCode` - never stored. New endpoint
  `GET /v1/invoices/{id}/pdf`.
- **`TenantSettingsController`/`Service`** (`tenant/`): `GET`/`PUT
  /v1/settings`, gated by the already-seeded `SETTINGS_VIEW`/
  `SETTINGS_MANAGE` permissions. Frontend: `ShopSettingsPage` at
  `/settings/shop`.
- **`CustomerLookupService`** extracted from `InvoiceServiceImpl` (was a
  private method) into `customer/service/`, so `QuotationServiceImpl` can
  share the same find-or-create-by-mobile logic rather than duplicating it.
  `InvoiceRequest`/`QuotationRequest` both gained `customerGstNo`/
  `customerStateCode`.
- **Quotation module** (`quotation/`, Module 10, built ahead of Purchase -
  see CR-022 for why this doesn't violate PROJECT_SKILLS #22): full
  entity/repository/DTO/mapper/service/controller. A quotation moves no
  stock and posts nothing financial - `POST /v1/quotations/{id}/convert`
  builds a real `Invoice` through the *same* `InvoiceService.create()` path
  a normal sale uses (current product prices, not the frozen quote price),
  then marks the quotation `CONVERTED`. `EXPIRED` is never written by the
  app - `Quotation.isExpired()` computes it from `validUntil` at read time.
  Frontend: `QuotationWizard` (4-step, same pattern as `InvoiceWizard`/
  `SupplierWizard`), list/detail/create pages, status actions (Sent/
  Accepted/Rejected), Convert-to-Invoice with a confirm dialog.
- Invoice list gained a Period filter (All time/This month/Last month) via
  new `fromDate`/`toDate` query params on `GET /v1/invoices`.
- Sidebar's Quotations and Shop settings links flipped from `available:
  false` to `true` now that both are real.
- **Also fixed while touching `Button`**: `<Button asChild>` (used for
  every "Back" link and the Dashboard's "New invoice" button) crashed the
  entire React tree with "Slot failed to slot onto its children" - the
  component always rendered a `{loading ? <Loader2/> : null}` sibling next
  to `children`, and Radix's `Slot` requires exactly one child. This is
  what caused the "forgot password shows a blank page" complaint
  (BUG-FE-001) and the blank Dashboard - not a stale tab as first
  suspected. Fixed in `shared/components/ui/button.tsx`: `asChild` now
  passes `children` straight through with no injected sibling.

Tests added: `CustomerLookupServiceImplTest` (5 tests, extracted logic),
`QuotationServiceImplTest` (7 tests: totals, expiry, convert guards, status
transitions), `InvoicePdfServiceTest` (4 tests, renders a real PDF and
checks the `%PDF-` header - not mocked, since a malformed HTML string
breaking the parser is exactly the kind of bug a mock would hide).
`InvoiceServiceImplTest` updated for the `CustomerLookupService` extraction.
`mvn clean verify`: 113/118 pass, 5 blocked by pre-existing BUG-ENV-002.

**Not yet done**: no human/browser click-through of the Quotation wizard,
Convert-to-Invoice, or PDF download (verified via curl/psql/unit tests and
headless-Chrome render checks only). No Postman/docs for Module 10. The
"material theme" icon request from the same message was not addressed this
round - deferred, lowest risk if incomplete.

### UI polish round: search, scrollbar, theme, Supplier wizard, Dashboard (2026-08-22, later)

Owner feedback from live use, addressed same session:

- **Category/Brand columns showing "-"**: `V903` seed migration adds 7
  categories and 8 brands and backfills every existing seeded product
  (including the two created by hand during earlier live testing) - never
  edits `V902` (Flyway rule), matches by `product_code`.
- **Header search bar was disabled/non-functional**: replaced with
  `GlobalSearch.tsx` - a real debounced search across Products and
  Suppliers, Cmd/Ctrl+K to focus, results navigate to the record.
- **Scrollbar "not user friendly"**: added a themed thin scrollbar
  globally (`index.css`) instead of the bare OS default.
- **Light mode "irritates my eyes"**: `--background` was pure white
  (`0 0% 100%`) while a softer `--surface-0` token existed but was never
  actually wired to anything - classic case of a design decision recorded
  but not applied. `--background` now matches `--surface-0` (soft
  off-white); `--foreground` softened slightly too. Sidebar intentionally
  stays dark in both themes (a deliberate "chrome, not content" choice,
  documented in the CSS) - not changed, since darkening/lightening it
  further wasn't specified.
- **"Where is the dashboard"**: there wasn't one - `/` redirected straight
  to Profile. Added a real `DashboardPage` (stat cards for
  products/suppliers/low-stock/invoices, recent invoices, low-stock list,
  a New Invoice shortcut), now the actual landing page and login
  redirect target.
- **Supplier Add/Edit "long scroll" dialog** (raised twice now): replaced
  with `SupplierWizard.tsx` - the same full-page, 5-step, sticky-bottom-nav
  pattern used for Invoice creation (Basic Information → Contact
  Information → Address → Bank/Financial → Review & Save). The old
  `SupplierForm.tsx` dialog component had zero remaining references after
  the swap and was deleted, not left as dead code.
- **Icons**: added to sidebar section headers (Overview, Sales, Purchase,
  Inventory, Accounting, Administration) - interpreted from a fairly
  ambiguous request; flag to the owner if this wasn't what was meant.

`mvn clean verify` re-run after all of the above: still 99/99, zero
regressions. Frontend `tsc -b --force` and `vite build` both clean.

**Not yet done**: same theme pass could still extend to dialog/modal/badge
contrast specifically (only background/foreground/scrollbar were touched,
not every component); a human click-through of the new Supplier wizard
specifically (same caveat as the Invoice wizard - verified by compile +
build only, not by a rendered click-through).

### Module 4 (Inventory) + Module 7 (Invoice & Payment) built from scratch (CR-021, 2026-08-22)

Built end to end, backend first then frontend, each layer live-tested
before moving to the next - not written blind. See CR-021 in
`CHANGE_REQUEST_REGISTRY.md` for why this jumped ahead of full Customer
management.

- **Inventory**: `stock` (one row per tenant+product) and `stock_movement`
  (append-only ledger, signed quantity, running balance) - `V8`. Backend:
  `StockService.applyMovement` is the one sanctioned way any module moves
  stock, under a pessimistic row lock so two simultaneous sales of the same
  product cannot silently overwrite each other's decrement. Frontend: a
  Stock list page with search, low-stock filter, and a manual-adjustment
  dialog.
- **Customer (minimal, CR-021)**: `customer` table - found-or-created by
  mobile number from inside `InvoiceServiceImpl` only. No
  `CustomerController`, no management UI - deliberately deferred to the
  real Module 5.
- **Invoice & Payment**: `invoice`, `invoice_item`, `payment` - `V9`.
  Business rules from the owner's exact spec, all live-verified via curl:
  optional initial payment (absent/zero → UNPAID, partial → PARTIALLY_PAID
  with correct balance, exact → PAID, over-total → rejected with 422
  `PAYMENT_EXCEEDS_TOTAL`); creating an invoice decrements stock via
  `StockService`; cancelling one restores it; `addPayment` re-derives
  paid/balance/status server-side, never trusting a client-supplied value.
  10 unit tests cover every one of these paths (`InvoiceServiceImplTest`),
  in addition to the live curl verification.
- **Frontend - the wizard the owner specifically asked for**: a full-page
  (not modal) 4-step Invoice creation flow - Customer → Items → Payment →
  Review - with a step indicator that's always visible and a **sticky
  bottom Back/Next/Save Invoice bar**, so the user never has to scroll to
  find navigation. The Items step has an internally-scrolling product
  table only once several items are added (legitimate table scroll, not
  page scroll). Product search/add uses a small debounced picker
  (`ProductPicker.tsx`) since no Combobox primitive existed yet.
- **Seed data**: `V902` adds 12 real hardware-shop products (locks,
  switches, wire, tools, pipe, paint, adhesive, taps) each with opening
  stock, so an invoice can be created immediately without manually
  adjusting stock first.
- **Profile page redesign**: the flat wall of ~25 raw permission-code
  badges (`PRODUCT_VIEW_STOCK`, `SUPPLIER_MANAGE`, ...) cramped into the
  narrow left "Account" card is gone. Permissions now live in their own
  tab, grouped by module with friendly headings (reusing
  `GET /v1/permissions/grouped`, the same data source `RoleForm`'s picker
  already used) - the left card is now a clean, compact identity summary.
- **`/forgot-password` blank-page report**: investigated thoroughly
  (route wiring, component code, `sonner`/`Toaster` setup, live Vite
  transform of every file on the path, backend endpoint re-curl-tested) -
  found nothing wrong server-side. Most likely a stale browser tab from
  early in a long edit session. See BUG-FE-001. Ask for a hard refresh and
  the exact browser console error if it recurs.

Full regression check after all of the above: `mvn clean verify` -
**99/99** runnable tests pass (was 89, +10 new Invoice tests, zero
existing tests broken). Frontend `tsc -b --force` and `vite build` both
clean.

**Not yet done**: a human/browser click-through of the new wizard (only
verified via curl + clean compiles + clean Vite transform, not by
actually clicking through a rendered page); Module 4/7 automated tests
beyond the 10 for Invoice (Stock adjustment itself has no dedicated unit
test yet, though it's exercised indirectly by every Invoice test);
Postman/docs for any module past Module 1.

### Test suite: first clean run (2026-08-22)

`mvn clean verify` had never once completed - this session ran it for real
and fixed everything it found:

- **BUG-TEST-001**: the three existing unit test classes didn't compile
  against CR-016's tenant-scoped repository signatures. Fixed.
- **BUG-AUTH-013**: a pre-existing Mockito `UnfinishedStubbingException` in
  `AuthServiceImplTest`, unrelated to CR-016. Fixed.
- **BUG-MONEY-001**: Indian digit grouping (PROJECT_SKILLS #29) was
  completely non-functional in both `SupplierMapper` and `ProductMapper` -
  `java.text.DecimalFormat` does not support the repeating secondary
  grouping Indian formatting needs, verified by direct reproduction. Fixed
  with a hand-written `IndianCurrencyFormat` utility both mappers now share.
- **BUG-BUILD-001**: the 5 `*ControllerIT` classes - the majority of the
  "184 tests written" figure - had never executed in any build, because
  `maven-failsafe-plugin` was never added to `pom.xml` and Surefire's
  default pattern doesn't match `*IT.java`. Fixed - Failsafe is now wired to
  `integration-test` + `verify`, and all 5 classes are confirmed discovered.
- **BUG-ENV-002 (open)**: those 5 `*IT` classes, plus 2 pre-existing
  `*Test` classes that also extend `AbstractIntegrationTest`
  (`PermissionCodeConsistencyTest`, `SecurityFilterRegistrationTest`),
  cannot actually run here - Docker Desktop 4.86 is healthy but
  testcontainers-java (tried 1.20.4 and the latest 1.21.3) cannot connect
  over either named pipe it exposes. Not a code defect; see BUG_REGISTRY.md
  for what was ruled out.

Result: **89/89** runnable tests pass. 7 tests remain blocked by BUG-ENV-002.

### Multi-tenancy foundation (CR-016) - reverses the original "single shop" decision

The owner explicitly asked for multi-tenancy mid-session, overriding the
locked "Not multi-tenant" decision in `CLAUDE.md`/`PROJECT_REGISTRY.md`.
Documented as CR-016 rather than silently applied. Shared database, shared
schema, `tenant_id` discriminator column - not schema-per-tenant or
database-per-tenant.

- New `tenant` table (`V6__multi_tenant_foundation.sql`); `tenant_id` added
  to `app_user`, `role`, `supplier`.
- **mobile_no/email stay globally unique across tenants, deliberately** -
  login has no tenant selector, so an identifier must resolve to one user
  platform-wide. `employee_code`, `role_code`/`role_name`, `supplier_code`
  and the supplier name/GST functional indexes all moved from global to
  per-tenant uniqueness.
- No new JWT claim needed - `tenant_id` rides on `AppUserDetails`, sourced
  fresh from the DB on every request as `JwtAuthenticationFilter` already
  does for `token_version` and permissions (BUG-AUTH-001's minimal-claims
  design extended, not broken).
- `SecurityUtils.requireCurrentTenantId()` is the one sanctioned scoping
  point; every repository query in Auth and Supplier was audited and
  updated - including real, previously-latent cross-tenant privilege gaps
  (`UserServiceImpl`/`RoleServiceImpl` assigning a role by id without
  checking it belonged to the caller's tenant, `guardLastActiveOwner`
  counting owners across every tenant instead of one).
- `BootstrapOwnerInitializer` now resolves the single seeded tenant rather
  than creating one - provisioning a *second* tenant is explicitly out of
  scope for CR-016, deferred to its own future CR.
- Applying V6 to the already-seeded dev database hit two real Flyway
  issues, both resolved without data loss: seed migrations (V900/901)
  numerically sort *after* real schema migrations added later, so
  `spring.flyway.out-of-order: true` was added to `application-dev.yml`;
  and editing the already-applied seed files (to add `tenant_id`) changed
  their checksums, fixed with `mvn org.flywaydb:flyway-maven-plugin:repair`
  (metadata-only - the data already matched, since V6's backfill `DEFAULT`
  had already set every existing row to the default tenant).
- **Not done**: provisioning a second tenant (own CR), and the existing
  184-test suite has not been run against the tenant-scoped schema yet -
  expect failures, see EXACT NEXT ACTION.

### Module 3: Category, Brand, Product (CR-020)

Built as one increment ahead of Customer, per the owner's explicit request -
see CR-020 for the module-order reasoning. Tenant-scoped from its first
migration, so none of CR-016's retrofit pain applied here.

- `V7__category_brand_product_schema.sql` - `category` (hierarchical,
  self-referencing `parent_category_id`), `brand`, `product`.
- Backend: entities, repositories, DTOs, mappers, services, controllers for
  all three, following the Supplier module's exact conventions (auto-coded
  `CAT-nnnn`/`BRD-nnnn`/`PRD-nnnnnn`, tenant-scoped uniqueness, soft delete
  for Product / hard-delete-if-unreferenced for Category and Brand).
- Reused existing `PRODUCT_VIEW`/`PRODUCT_MANAGE`/`PRODUCT_VIEW_COST`
  permissions rather than inventing new ones for Category/Brand.
- **Deliberate deviation from CR-004**: current pricing lives directly on
  `product` (paise, per the money rule) rather than being deferred entirely
  to a mandatory Product Variant layer - most hardware-shop items are
  single-SKU. Product Variant remains real future work for multi-SKU
  products, price history and the CR-004 loss-sale approval workflow - see
  the deviation note in `FEATURE_REGISTRY.md`.
- **Cost-visibility gap closed before it could ship**:
  `purchasePricePaise`/`purchasePriceDisplay` are null in every response
  unless the caller holds `PRODUCT_VIEW_COST` (STAFF has `PRODUCT_VIEW`
  only). Because `ProductRequest.purchasePricePaise` is required on every
  update, a `PRODUCT_MANAGE`-without-`PRODUCT_VIEW_COST` editor opening the
  edit form would submit a fabricated `0` and silently zero out the real
  cost - so the frontend gates the Edit action itself on holding *both*
  permissions, not just hiding the field. Recorded as PROJECT_SKILLS #33 so
  it isn't rediscovered the hard way on a future entity.
- Frontend: full module mirroring Supplier's structure (`types`,
  `constants`, `validation/schemas`, `services`, `components`, `forms`,
  `pages` for Category/Brand list pages plus Product list + detail), routes
  and sidebar wired, nav enabled.
- **Live-verified**, not just compiled: create/update/search for all three;
  auto-generated codes; tenant scoping; duplicate rejection (409); category/
  brand delete blocked while referenced (422) and succeeding once
  unreferenced (204); STAFF correctly blocked from managing (403) and
  correctly denied cost fields (200, fields absent) while OWNER sees them.
- **Not done**: zero automated tests written for this module yet (backend
  or frontend) - everything above was verified by live `curl`/build checks
  in this session, which found real issues but is not a substitute for a
  regression suite.

### Auth page polish + forgot-password investigation

- `AuthLayout.tsx`: brand panel gradient (existing `--primary`/`--chart-3`
  tokens, no new colors) plus a feature checklist, per a design reference
  the user shared. Saved to Claude memory for future sessions.
- Investigated a reported forgot-password failure: tested live end to end
  (`POST /v1/auth/forgot-password` → 200, reset link correctly logged in
  dev mode). Not actually broken in this codebase as it stands - the
  `AuthenticationFailedException` seen earlier in logs is Spring Boot's
  mail *health indicator* probing `smtp.gmail.com` with no credentials
  (why `/actuator/health` shows `DOWN`), unrelated to the actual
  `SmtpMailService` code path, which correctly skips SMTP when unconfigured.

*(Everything from the previous resume point - BUG-ENV-001 closure, six
first-real-run bugs (BUG-AUTH-010/011/012, BUG-SUP-002/003/004), Module 2
frontend, Module 1 docs - is unchanged and still accurate; see
`BUG_REGISTRY.md` for full detail on each.)*

---

## Running it locally (verified working, 2026-08-22)

Port 5432 was occupied by an unrelated container on this machine; worked
around with an alternate port. Adjust back to 5432 if that conflict does
not apply to you.

```bash
DB_PORT=5433 DB_PASSWORD=hardware_erp docker compose up -d

cd backend
export DB_PORT=5433 DB_USER=hardware_erp DB_PASSWORD=hardware_erp DB_NAME=hardware_erp
export JWT_SECRET=$(openssl rand -base64 32)
mvn spring-boot:run
```

Seeded owner login (dev profile, `V900__seed_dev_data.sql`, now
tenant-scoped to the one default tenant V6 creates):
`9876543210` / `Owner@2026`. Swagger UI:
`http://localhost:8080/api/swagger-ui.html`. `/api/actuator/health` reports
`DOWN` locally - expected, see the mail health-indicator note above.

If Flyway ever reports a checksum mismatch after editing an *already-applied*
seed migration on your local dev database (not something to do routinely -
only happened here mid-session while retrofitting tenant_id onto seed data
that had already run): `mvn org.flywaydb:flyway-maven-plugin:10.20.1:repair
-Dflyway.url=... -Dflyway.user=... -Dflyway.password=... -Dflyway.locations=filesystem:src/main/resources/db/migration,filesystem:src/main/resources/db/seed`
recalculates checksums without touching data - only safe when the actual
data already matches the corrected file, verify that first.

---

## Pending tasks

**Immediate**
1. `mvn clean verify` - full suite, post-CR-016 - expect tenant-related failures
2. Backend + frontend tests for Category/Brand/Product (currently zero)
3. Module 2 and Module 3 Postman collections and docs

**Deferred by design, not forgotten**
4. Product Variant: multi-SKU, `product_price_history`, CR-004's loss-sale
   approval workflow (GOOD/WARNING/APPROVAL REQUIRED/LOSS SALE)
5. A real second-tenant provisioning flow (CR-016 explicitly deferred this)
6. CR-017 (supplier wizard), CR-018 (bank account encryption + reveal
   permission + eye toggle), CR-019 (per-user theme/language + i18n) -
   written up in `CHANGE_REQUEST_REGISTRY.md`, not started

**Module 1 leftovers**
7. 12 remaining PDFs: 05, 06, 07, 08, 10, 11, 12, 13, 14, 15, 17, 18
8. Frontend unit tests - `src/modules/auth/tests/`, `src/modules/supplier/`,
   `src/modules/product/` are all still empty

**Blocked on environment**
9. `registry/static_check.py` - no Python interpreter in this environment
   (does not block anything above - it only checks static structure)

**After all modules**
10. Deployment package: production build, `start-erp.bat`, `stop-erp.bat`,
    browser auto-open, `RUNBOOK.md`, local deployment guide, Electron plan
    (planned, not implemented)

---

## Known bugs

| ID | Severity | Status |
|---|---|---|
| BUG-AUTH-001 … 009 | HIGH/MEDIUM | Fixed |
| BUG-AUTH-010 | MEDIUM | Fixed |
| BUG-AUTH-011 | HIGH | Fixed |
| BUG-AUTH-012 | CRITICAL | Fixed |
| BUG-SUP-001 | MEDIUM | Fixed before commit (CR-015) |
| BUG-SUP-002 | LOW | Fixed |
| BUG-SUP-003 | HIGH | Fixed |
| BUG-SUP-004 | HIGH | Fixed |
| **BUG-ENV-001** | INFO | **Closed 2026-08-22** |

No new numbered bugs from Module 3 or the multi-tenancy retrofit - both were
built and live-verified iteratively, so issues were caught before ever
compiling/running cleanly rather than after. Full detail on all bugs:
`project-knowledge/BUG_REGISTRY.md`.

---

## Change requests

| ID | Summary | Status |
|---|---|---|
| CR-001 … CR-012 | Architecture, naming, module order, structure | Approved and applied |
| CR-013 | Security audit log read endpoint | Applied |
| CR-014 | MySQL to PostgreSQL | Applied |
| CR-015 | Business activity log, separate from security audit | Applied |
| CR-016 | Multi-tenant architecture | Approved, in progress (see test-impact note) |
| CR-017 | Supplier form wizard, read-only code | Proposed, not started |
| CR-018 | Bank account encryption + reveal permission | Proposed, not started |
| CR-019 | Per-user theme/language + i18n foundation | Proposed, not started |
| CR-020 | Module order: Category/Brand/Product before Customer | Approved and applied |

---

## Verification commands

```bash
python3 registry/static_check.py         # NOT RUN - no Python interpreter in this environment
cd frontend && npm run typecheck         # PASS - Modules 1-3
cd frontend && npm run build             # PASS - exit 0
cd backend && mvn -DskipTests compile    # PASS
cd backend && mvn spring-boot:run        # PASS - starts clean, all Module 1-3 core paths verified live
cd backend && mvn clean verify           # NOT YET RUN TO COMPLETION since CR-016 - do this next
```
