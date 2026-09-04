# IMPROVEMENT OPPORTUNITIES & USAGE NOTES

**Written:** 2026-09-03, at the owner's request ("think about how to improve
this application, note the improvement points, and give tips/usage guidance
that help in any way"). Verified against the live repository (`git status`,
`Sidebar.tsx`'s `available` flags, `SECURITY_REGISTRY.md`,
`CHANGE_REQUEST_REGISTRY.md` through CR-057 phase 10, `RESUME_POINT.md`'s own
next-action list) rather than the five registries already known stale
(`PROJECT_REGISTRY.md`, `FEATURE_REGISTRY.md`, `MODULE_DEPENDENCY_MAP.md`,
etc. — see `MASTER_PROJECT_STATUS.md` §1). This is a note, not a Change
Request — nothing here is built yet; each item below still needs the normal
CR process (`CHANGE_REQUEST_REGISTRY.md` entry → migration → code → tests →
registries) before code changes.

---

## How to read this document

- **Improvement points** are ranked by a mix of risk and effort — cheap/high-value first.
- Every claim cites where it came from (a file, a nav flag, a registry note) so it can be checked, not taken on faith.
- **Tips & usage** (bottom half) is for day-to-day use of the app as it exists today, not a roadmap item.

---

## Part 1 — Improvement points, ranked

### P1. Ship the frontend for features that are already built on the backend

Three modules have a complete, tested backend and **no UI at all** — not
partial, literally no route, no nav entry, no page:

| Module | Backend | Frontend | Evidence |
|---|---|---|---|
| Sales Order | Done, CR-052 | **None** | Not in `Sidebar.tsx`'s `NAV_SECTIONS`; `PROJECT_REGISTRY.md` row says "not started" for frontend |
| Delivery Challan | Done, CR-052 | **None** | Same |
| Credit Note | Done, CR-052 | **None** | Same |

A shop that wants to issue a delivery challan or a sales order today cannot —
the API exists, curl/Postman can do it, a cashier at the counter cannot. This
is the single highest-value item in this list: it's not new design work
(the business rules, permissions, and DTOs already exist), it's "build the
form and list page the same way Invoice's were built."

Two more nav items are wired up but explicitly marked **`available: false`**
in `Sidebar.tsx` (the code comment there explains why: *"a link that opens an
empty page is worse than no link"*) — meaning the permission codes exist,
the pages don't:

- **Stock adjustments** (`INVENTORY_ADJUST` permission already seeded)
- **Ledgers** and **Reports** (`REPORT_FINANCIAL`/`REPORT_VIEW` permission
  codes seeded since `V1`, per `MASTER_PROJECT_STATUS.md` §2, specifically
  reserved for this)

**Recommendation:** Sales Order/Delivery Challan/Credit Note frontends
first (revenue-facing, backend already paid for), then Stock adjustments
(operationally needed — right now a miscounted shelf has no in-app fix path),
then Reports (owners increasingly ask "how is the shop doing" and today only
the Dashboard answers that, narrowly).

### P2. Close the two security gaps the codebase already flags on itself

Neither is exploited today, both are named explicitly in `SECURITY_REGISTRY.md`
and `BUG_REGISTRY.md`'s own text as open:

1. **Platform Admin refresh token travels in the JSON body, not an HttpOnly
   cookie** (CR-054's own words: *"the first thing to close before this
   console handles anything more sensitive than viewing its own identity"*).
   Since CR-057 added Billing and tenant suspension to that console, that
   threshold has arguably already been crossed — this should move up the
   queue rather than wait.
2. **BUG-SET-001** (2026-09-03, per `BUG_REGISTRY.md`): the subscription-tier
   picker was a harmless placeholder before real billing existed; once
   Razorpay billing (CR-057 phase 9) shipped against the same field, it
   became a free-checkout bypass. It was caught while building, not by a
   report — worth a deliberate pass over anything else built "as a
   placeholder" earlier that now has a real consumer, since the same shape
   of bug (an assumption that was true when written, silently false later)
   can recur anywhere two features share a field across a time gap.

### P3. Frontend has zero automated test coverage

Backend has 298 unit + ~150 Testcontainers integration tests. Frontend has
**none** — no Vitest, no Playwright, no component tests — stated repeatedly
across CR-056/057 and top of `RESUME_POINT.md`'s next-action list. Every
frontend regression this project has ever caught (the Radix Select bug,
BUG-FE-006/007/012/018, the sidebar active-state issue) was caught by manual
click-through, which does not scale as the app now has 20+ frontend modules
and a Platform Admin Console on top. A minimal Vitest setup covering just the
shared components (`FormField`, `DatePicker`/`Calendar` just added, the
permission gates) would catch the highest-recurrence bug class cheaply.

### P4. The 3 newest integration test files have never actually run

`PlatformAdminAnalyticsControllerIT`, `PlatformAdminBillingControllerIT`,
`SubscriptionBillingControllerIT` compile but were never executed against
real PostgreSQL — Docker went down mid-session before Testcontainers could
run them (a recurrence of the long-standing BUG-ENV-002 Docker/Testcontainers
friction on this machine). Since Docker Desktop is confirmed working right
now (I started it and the backend booted cleanly against it this session),
this is a same-day, low-effort item: run `mvn clean verify` once while Docker
is up and confirm all ~150+3 integration tests are actually green, not
assumed green.

### P5. Native `<input type="date">` is still used in 15 other files

This session replaced it with a real popup calendar (`DatePicker`) on the
Tally export page only, per the specific screenshot given. The same
browser-inconsistent native picker is still used in:

```
PlatformAdminAuditLogPage, PlatformAdminIncidentsPage, WorkerDetailPage,
QuotationWizard, PurchaseForm, ProjectForm, ImportSupplierBillDialog,
AttendancePage, WorkerPaymentForm, ExpenseForm, ExpenseListPage,
ProjectPaymentFormDialog, ProjectExpenseFormDialog, CouponForm,
project/validation/schemas.ts
```

The new `shared/components/ui/date-picker.tsx` and `calendar.tsx` are
already built and reusable — swapping these in is now pure find-and-replace
per file, no new design work. Worth doing as a single dedicated pass rather
than one-off per bug report, since it's the same fix repeated 15 times.

### P6. Git workflow has drifted from what `CLAUDE.md` documents

`CLAUDE.md` specifies `main` → `develop` → `feature/*`/`bugfix/*`/`hotfix/*`,
never committing directly to `main`/`develop`. The actual repository has no
`develop` branch and no `feature/*` branches — work happens on ad-hoc
`checkpoint/YYYY-MM-DD-...` branches carrying several CRs' worth of change at
once (the current branch, `checkpoint/2026-09-02-cr051-to-cr054-backlog`,
actually contains CR-051 through CR-057). This isn't necessarily wrong for a
solo-developer project, but it means the documented workflow and the real one
have quietly diverged — either update `CLAUDE.md` to describe what actually
happens (checkpoint branches, squash-merged to `main` periodically), or
start actually using `develop`/`feature/*`. Documentation that nobody follows
becomes a trap for the next session that trusts it.

### P7. Five registry files are still stale

`PROJECT_REGISTRY.md`, `FEATURE_REGISTRY.md`, `MODULE_DEPENDENCY_MAP.md` and
others have been known-stale since `MASTER_PROJECT_STATUS.md` flagged it on
2026-08-23 — and `MASTER_PROJECT_STATUS.md` itself is now stale relative to
CR-057. Every session pays a real tax reconciling "what does this file claim"
against "what does `git log`/the code actually say" (this document did the
same reconciliation to be written safely). A one-time pass to bring the
stale files current, plus a rule that the *next* CR that touches a module
also updates that module's row (rather than batching corrections into a
big audit later), would stop this compounding.

### P8. No backup/restore story for the shop's own data yet

Platform Admin's "Backup Center" (per RESUME_POINT's own next-action list)
is a *platform-operator* feature — not the same thing as "can a shop owner's
own database be restored if the server dies tonight." Nothing in the
registries describes an actual backup schedule for the production Postgres
instance. This is a real-money risk (invoices, payments, customer credit
balances) independent of which feature ships next in the roadmap — see the
Tips section below for what to do about it *today*, without waiting for code.

---

## Part 2 — Tips & usage (how to get the most out of the app as it stands)

### Starting the app locally

```
docker compose up -d                 # PostgreSQL, port 5433
cd backend && mvn spring-boot:run    # API, port 8080 (profile: dev)
cd frontend && npm run dev           # UI, port 5173 (proxies /api -> 8080)
```
Login page is at `http://localhost:5173`. Platform Admin Console (staff-only,
separate login) is a completely separate auth system at
`/platform-admin/login` — a tenant password never works there and vice versa,
by design (CR-054).

### Reading the sidebar correctly

A nav item with no page behind it is *deliberately* never shown — `Sidebar.tsx`
filters out every `available: false` item (Stock adjustments, Ledgers,
Reports today) specifically so the menu never promises something that opens
to a blank page. If a feature you expect isn't in the sidebar, it's not
hidden by a permission — it may genuinely not be built yet. Check this
document's P1 list before assuming it's a bug.

### Roles and permissions

Permissions are the real access boundary (`@PreAuthorize`, checked server-side
on every request) — the sidebar and buttons you see are just a convenience
that hides things you couldn't do anyway. If a staff member says a button is
missing, check their role's permissions under **Administration → Roles**
before assuming it's a bug. `PRODUCT_VIEW_COST` is the one to know about
specifically: STAFF can see and sell products but never sees purchase cost,
even in the API response — that's intentional, not a display bug.

### Tally export — read the caveat on the page

The export produces ledger-level Sales/Purchase vouchers and Customer/
Supplier/Stock-Item masters — **not item-wise inventory vouchers**, and the
sign convention has never been verified against a real Tally install (no
Tally license exists in the dev environment). Always import into a **test
company** in Tally first, every time, before trusting it against real books —
the page says this, and it's not boilerplate caution, it's a genuine
unverified area.

### GST calculator vs. Invoice GST

The standalone GST calculator (`Tools → GST calculator`) is pure client-side
arithmetic with no permission gate — it doesn't touch tenant data, so it's
safe to hand to anyone, even to quote a customer before creating a real
invoice. It is not wired to actual invoice creation; use it for quick
mental-math checks, not as a substitute for the Invoice wizard's own GST
computation (which is computed server-side on the final invoice rate, per
the locked business rule).

### Subscription tiers — know what "FREE" actually blocks

FREE tier caps at 1 owner / 100 customers / 100 suppliers / 1000 products,
enforced server-side (not just a UI warning) via `EntitlementService`. If a
create action starts failing with an entitlement error as a shop grows, that
is the cap being hit, not a bug — the fix is upgrading the tier in
**Shop Settings → Plan usage**, which shows live progress bars against each
limit before it's hit.

### WhatsApp reminders — needs a real Meta connection per tenant

The reminder/notification pipeline exists, but sending an actual WhatsApp
message requires that specific tenant to have connected its own WhatsApp
Business account (Settings → WhatsApp) with real Meta Graph API credentials.
Without that connection, reminders silently log rather than send — check the
connection status first before assuming the feature is broken.

### Back up the database yourself, today, regardless of the roadmap

Until Platform Admin's Backup Center ships, protect real business data with
a plain scheduled dump of the actual Postgres container:

```
docker exec hardware-erp-postgres pg_dump -U hardware_erp -d hardware_erp \
  -F c -f /tmp/backup.dump
docker cp hardware-erp-postgres:/tmp/backup.dump ./backups/$(date +%F).dump
```

Put it on a daily cron/Task Scheduler job pointed outside the Docker volume
(a backup that lives on the same disk as the thing it backs up survives
nothing). This costs nothing to do now and removes the single largest risk
to real invoices/payments/customer data sitting in `project-knowledge`'s own
gap list as "not started."

### When something looks broken, check which layer first

Per `CLAUDE.md`'s own bug-triage rule: open the browser network tab before
assuming either side is at fault. If the API response is correct and the
screen is wrong, it's a frontend-only fix; if Postman gets the same wrong
answer the UI shows, the backend is at fault. This distinction is why almost
every bug in `BUG_REGISTRY.md` has a one-line "SCOPE:" tag — it keeps a fix
from accidentally touching a layer that was never broken.

---

## Suggested next action

Pick one item from Part 1 and say so — each is scoped to fit the existing
CR process (registry entry → migration if needed → code → tests →
registries). P1 (Sales Order/Delivery Challan/Credit Note frontends) is the
highest ratio of business value to remaining effort, since all backend and
business-rule work is already done and tested.
