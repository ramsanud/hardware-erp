# CLAUDE.md

Instructions for Claude Code working on this repository. Read automatically at
the start of every session — do not wait to be told.

---

## What this project is

Hardware ERP for hardware shops in India. One Spring Boot application, one
React application, one PostgreSQL database, **multiple tenants (shops)**
sharing that one schema via a `tenant_id` discriminator column (CR-016,
2026-08-22).

**Not** microservices. **Not** database-per-tenant or schema-per-tenant —
one shared schema, isolated by `tenant_id` and enforced server-side from the
JWT, never from client input. Module folders express boundaries, not
deployment units.

Every tenant-owned table carries `tenant_id`. Every query against one must
filter by it, taken from `SecurityUtils.currentTenantId()` — never from a
request parameter or path variable. See CR-016 in
`CHANGE_REQUEST_REGISTRY.md` for the full design, including the deliberate
trade-off that login identifier (mobile/email) stays globally unique across
tenants, not scoped per-tenant.

```
hardware-erp/
├── backend/          Java 21, Spring Boot 3.4.2, PostgreSQL 16, Flyway
├── frontend/         React 18, TypeScript, Vite 6, Tailwind, shadcn/ui
├── docs/             per-module documentation and Postman collections
├── project-knowledge/  8 registry files — the source of truth
└── registry/         static_check.py — run before every commit
```

---

## Read these before writing any code

In this order. Never ask the user to re-explain a decision that is recorded here.

| File | Contains |
|---|---|
| `project-knowledge/PROJECT_REGISTRY.md` | stack, naming law, module status |
| `project-knowledge/DATABASE_REGISTRY.md` | every table, column, constraint |
| `project-knowledge/API_REGISTRY.md` | every endpoint and its permission |
| `project-knowledge/SECURITY_REGISTRY.md` | auth design, token rules |
| `project-knowledge/CHANGE_REQUEST_REGISTRY.md` | CR-001 … CR-015, all approved |
| `project-knowledge/BUG_REGISTRY.md` | every bug and its regression test |
| `project-knowledge/PROJECT_SKILLS.md` | lessons — do not repeat past mistakes |
| `RESUME_POINT.md` | exact next file to work on |

---

## Proactive scope — think beyond the literal ask (CR-037, 2026-08-25)

For every requirement — a bug report, a feature request, a one-line ask —
think through the complete real-world workflow across the roles it touches
(Owner, Salesperson, Purchase Staff, Inventory Manager, Warehouse Staff,
Accountant, Auditor, Customer, Supplier, Labour) before writing code, not
after. A single reported symptom is a signal to check the surrounding
module for the same class of defect — this is not new: BUG-FE-007 (a
Radix Select silently losing a just-created option) was found in the
Expense module and, on inspection, confirmed to also affect the
already-shipped Product module. Do that inspection by default, every
time, without being asked twice.

### What "proactive" means here, precisely

- **Always investigate and report** every adjacent gap, missing role
  workflow, or edge case noticed while working — even ones outside
  today's specific ask. State what was found in the response, whether or
  not it was fixed.
- **Always fix same-root-cause defects** found this way, in the same
  commit — the way BUG-FE-007's `ProductForm` fix rode along with the
  `ExpenseForm` fix that surfaced it.
- **Never silently build large new subsystems** to cover a gap just
  noticed (an OCR/document-extraction pipeline, a synonym/fuzzy-search
  layer, e-signatures, optimistic-locking concurrency control, a
  lifecycle-state engine) without confirming first. Record what's missing
  and why it matters, propose it as a new CR, and wait for approval —
  these are multi-day builds, not a "while I'm in here" pass. This is
  still governed by "do not overengineer" under Style below: proactive
  means *thorough investigation and honest reporting*, not *unbounded
  implementation*.

### Already covered by existing architecture — extend it, never rebuild it

Several asks that come up under this umbrella are already solved
project-wide:

| Concern | Existing mechanism |
|---|---|
| Role-based navigation/pages | `PermissionGate` (frontend), `PermissionCode` + `@PreAuthorize` (backend) — every module gates its own routes and endpoints this way already |
| Tenant isolation | `SecurityUtils.requireCurrentTenantId()` + `findByIdAndTenantId(...)` on every repository — see CR-016 |
| Audit trail | `security_audit_log` (security events) + `activity_log` (business changes, before/after values) — CR-015 |
| Soft delete / never break history | status columns (`ACTIVE`/`INACTIVE`/`CANCELLED`, etc.) — never a hard `DELETE` on a record another table references |
| Inline "create a related entity without losing the form" | the "+ Add new X" pattern already used by Category/Brand (CR-024) and Expense Category (CR-036) — watch for BUG-FE-007's Radix Select quirk whenever adding a new one |
| File/document import safety | preview → confirm → import, never upload → auto-insert — established by Purchase Bill Import and Product Import; any future OCR/extraction feature must follow the same shape, surface a confidence/match status per line, and never silently commit an uncertain match |

---

## BUG HANDLING — scope the fix correctly

When the user reports a bug, **first determine which layer is actually broken**,
then fix only that layer. Fixing more than the fault requires is how a small
bug becomes a regression somewhere else.

Before fixing, spend one pass checking whether the same root cause exists
elsewhere in the same module — same Select component, same
null-parameter query shape, same missing permission check (see "Proactive
scope" above). Fix what's found under the same root cause, in the same
commit; call out anything bigger as a candidate for its own CR rather than
pulling it in unscoped.

### Step 1 — reproduce and locate

Ask for, or work out: the exact steps, what was expected, what happened, and
any error text or `X-Request-ID`.

Then trace it:

```
Screen looks wrong, but the API returned correct JSON   -> FRONTEND ONLY
API returns wrong data / wrong status / 500             -> BACKEND ONLY
API is correct but the frontend cannot consume it       -> FRONTEND ONLY
                                                           (unless the contract is wrong)
The contract itself is wrong (field name, type, shape)  -> BOTH
Data is wrong in the database                           -> BACKEND + migration
```

Confirm with Postman before deciding. If Postman gets the right answer and the
screen does not, the backend is innocent.

### Step 2 — apply the matching rule

**FRONTEND-ONLY bug**
Touch `frontend/` only. Do not modify any Java file, DTO, migration or test.
Typical causes: wrong Tailwind class, missing loading state, form validation
that does not mirror the backend, a `useEffect` dependency, a mis-typed field
name in `types/index.ts`.
Verify: `cd frontend && npm run typecheck && npm run build`

**BACKEND-ONLY bug**
Touch `backend/` only. Do not modify any `.tsx` file.
Typical causes: wrong SQL, missing `@PreAuthorize`, wrong HTTP status, a
business rule in the wrong place, a missing transaction boundary.
Verify: `cd backend && mvn clean verify`
Add a regression test in the same commit. A bug fixed without a test will
return.

**SPECIFICATION CHANGE (new field, new endpoint, new rule)**
Touch both, in this order, and never skip a step:
1. `project-knowledge/CHANGE_REQUEST_REGISTRY.md` — record it as CR-nnn first
2. New Flyway migration `V{n}__description.sql` — **never edit an applied one**
3. Entity → repository → DTO → mapper → service → controller
4. Backend tests
5. `frontend/src/modules/{module}/types/index.ts` — mirror the DTO exactly
6. Frontend validation schema — mirror the Bean Validation rules
7. Service, form, page
8. Postman collection
9. All affected registries

**BUG THAT SPANS BOTH**
Only when the fault genuinely exists in both layers, or the contract is wrong.
Fix the backend first, verify with Postman, then fix the frontend against the
now-correct API. Never fix them simultaneously — you lose the ability to tell
which change resolved it.

### Step 3 — always

1. Add the entry to `project-knowledge/BUG_REGISTRY.md`: ID, severity, layer,
   root cause, fix, regression test.
2. Add the regression test.
3. Run `python3 registry/static_check.py`.
4. Update `RESUME_POINT.md`.

### Say which scope you chose

Begin every bug response with one line:

```
SCOPE: FRONTEND ONLY   — the API response was correct; the table column was hidden at the wrong breakpoint
SCOPE: BACKEND ONLY    — GET /v1/suppliers returned 500 on a null city
SCOPE: BOTH            — creditLimitPaise was typed as number in TS but is a Java Long
```

If you are unsure which layer is at fault, **say so and ask for the API
response body** rather than guessing and changing both.

---

## Naming law — locked, never rename

One concept, one name, across database → entity → DTO → JSON → TypeScript. The
only permitted transformation is `snake_case` → `camelCase`.

| Rule | Value |
|---|---|
| Base package | `com.hardware.erp` |
| Table names | singular `snake_case`; `app_user` not `user` (reserved) |
| Primary key column | `<table>_id`; Java field always `id` |
| Timestamps | `TIMESTAMP(3)` / `LocalDateTime`, suffix `_at` |
| Booleans | no `is_` prefix |
| Status columns | `VARCHAR(20)` + CHECK constraint, never TINYINT, never ENUM type |
| Money | `BIGINT` paise. Never float, never double |
| Unit rates | `DECIMAL(18,6)` |
| API paths | `/api/v1/<plural-kebab-noun>` |

Renaming anything already generated requires a Change Request first. Run
`python3 registry/check_registry.py` to detect drift.

---

## Hard rules

1. **Never edit an applied Flyway migration.** Add a new version.
2. **Hibernate is `ddl-auto: validate`.** Never `update`.
3. **PostgreSQL only.** No MySQL syntax; `static_check.py` fails the build on it.
4. **Seed data lives in `db/seed/`**, loaded by dev and test profiles only.
5. **No self-registration endpoint.** The owner creates accounts (CR-008).
6. **Authorization is permission-based.** Never `hasRole('OWNER')` for business rules.
7. **Users and suppliers are soft-deleted.** Financial records reference them forever.
8. **Security events → `security_audit_log`. Business changes → `activity_log`** (CR-015).
9. **Access token in memory only.** Never `localStorage`.
10. **Never claim a build passes without running it.** State "not executed" instead.

---

## Verification commands

```bash
python3 registry/static_check.py          # structure, entity↔migration agreement
cd backend  && mvn clean verify           # compile + 184 tests (needs Docker)
cd frontend && npm run typecheck          # tsc -b --force
cd frontend && npm run build              # production build
docker compose up -d                      # PostgreSQL 16
```

---

## Current state (verified 2026-08-26)

Seventeen backend modules and eighteen frontend modules are built and
compiling. Migrations V1–V28 are applied. The locked module order below was
**completed, not abandoned** — treat this project as in maintenance and
extension, never as a greenfield build.

| Layer | Reality |
|---|---|
| Backend | 508 Java files, 36 controllers, 28 Flyway migrations, 47 test classes |
| Frontend | 238 TS/TSX files, 18 modules, 43 pages |
| Built end-to-end | Auth/Users/Roles, Tenant & Settings, Supplier, Customer, Category, Brand, Product, Inventory, Purchase, Quotation, Invoice, Payment, Expense, Project, Labour, Coupon, Dashboard |
| Backend-only | Notification (email live, SMS/WhatsApp stubbed), AI chat, Legal/user-consent (entities only, no controller) |
| Not present | Any PWA surface, any offline/IndexedDB layer, any frontend test runner |

**BUG-ENV-001 is CLOSED.** `mvn clean compile` and `tsc -b --force` both pass
(verified 2026-08-26). The previous claim here that the backend had never been
compiled was years of work out of date and caused sessions to start from a
false picture.

### Environment quirks on this machine

- `python3` is **not installed**, so `registry/static_check.py` and
  `registry/check_registry.py` cannot run here. Report them as "not executed"
  rather than implying they passed (hard rule 10).
- npm script shims fail under Git Bash with `'"node"' is not recognized`.
  Invoke the tool directly — `node ./node_modules/typescript/bin/tsc -b --force`
  — or run `npm run` scripts from PowerShell.

### Known open defects

None outstanding. The document-number race previously listed here was fixed by
**CR-041** (`document_sequence`, V29, `SELECT … FOR UPDATE`).

The full suite is green as of 2026-08-26: 298 unit tests and 100 Testcontainers
integration tests, `mvn clean verify`, exit 0. Two failures found while running
it end to end for the first time were fixed under CR-045 — **BUG-AUTH-014**
(refresh-token reuse detection had no working test) and **BUG-SEC-003**
(`RateLimitFilter` keyed on `getServletPath()`, which MockMvc leaves empty, so
rate limiting was never exercised by any test). Both were coverage holes, not
production holes.

Module order (completed, kept for history): Auth → Supplier → Customer →
Category → Brand → Product → Product Variant → Purchase → Inventory →
Quotation → Invoice → Payment.

---

## Git workflow (CR-045, 2026-08-26)

The repository had **no commits at all** until 2026-08-26. It now has:

```text
main                    production-ready; tagged releases (v1.0.0 onward)
  └── develop           integration
        ├── feature/*
        ├── bugfix/*
        └── hotfix/*    from main, merged back into BOTH main and develop
```

**Never commit feature work directly to `main` or `develop`.** Branch from
`develop`, merge back with `--no-ff`.

Runtime environments are **Spring profiles** — `local`, `dev`, `test`, `prod` —
never branches. There is deliberately no `production` or `testing` branch: a
branch named after an environment invites merging *to deploy*, which is how a
repository acquires three diverging mainlines.

Commits follow Conventional Commits (`feat:` `fix:` `refactor:` `docs:` `test:`
`chore:` `security:`). One concern per commit; the body says **why**, and names
the previous behaviour when a security boundary or default changes.

Tag a release only after `mvn clean verify`, `npm run typecheck` and
`npm run build` have all passed on the exact commit being tagged. Never create
a tag on unverified work — hard rule 10 applies to releases too.

Developer diagnostics live behind two independent server-side gates (the
environment *and* the `DEVELOPER_INSPECT` permission, which no default role
holds). Never weaken that to a frontend check, and never add DevTools blocking —
see the "Explicitly rejected" note in CR-045.

---

## Style

- Comment **why**, not what. Explain non-obvious decisions and trade-offs.
- Match the surrounding code; do not introduce a new pattern for one file.
- Small, reviewable commits. One concern per commit.
- If an instruction conflicts with something in `project-knowledge/`, **stop and
  raise a Change Request** rather than silently choosing one.
