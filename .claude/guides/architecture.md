# Architecture

Load this when: starting a new module, touching the tenant model, changing
the app shell or layouts, doing git branch/merge work, or when a session
starts from a false picture of what exists.

## What this project is

Hardware ERP for hardware shops in India. **One** Spring Boot application,
**one** React application, **one** PostgreSQL database, **multiple tenants
(shops)** sharing that one schema via a `tenant_id` discriminator column
(CR-016, 2026-08-22).

**Not** microservices. **Not** database-per-tenant or schema-per-tenant.
Module folders express boundaries, not deployment units.

```
hardware-erp/
├── backend/            Java 21, Spring Boot 3.4.2, PostgreSQL 16, Flyway
├── frontend/           React 18, TypeScript, Vite 6, Tailwind, shadcn/ui
├── docs/               per-module documentation and Postman collections
├── project-knowledge/  8 registry files — the source of truth
├── registry/           static_check.py — run before every commit
└── .claude/guides/     these topic guides
```

## Tenant model

Every tenant-owned table carries `tenant_id`. Every query against one filters
by it, taken from `SecurityUtils.currentTenantId()` — never from a request
parameter or path variable. Login identifier (mobile/email) stays **globally
unique across tenants**, not scoped per tenant — a deliberate trade-off
recorded in CR-016. One login is one shop: there is no shop switcher and no
multi-tenant session (CR-016, restated when CR-082 declined to draw one).

## Frontend shell

- `AuthLayout` — the sign-in design (CR-081): 50/50 hero on `--sidebar`,
  `AuthCard` shell on all six auth screens, `BrandMark` (post-and-lintel H)
  everywhere the mark appears, including the favicon.
- `AppLayout` + `Sidebar` — the app shell (CR-082): Overview row, folding
  groups persisted per user, shop card, utility list, person footer; header
  with global search (⌘K), `?` tour (CR-075), theme toggle, avatar + role.
- `DashboardPage` — the approved dashboard (CR-082). Widget titles are
  counter-staff language and never duplicate a rail label.
- Onboarding (CR-075): a permission-filtered tour and per-page first-visit
  tips, both stored client-side per user via `themeScope`.
- **Default colour theme is `emerald`** (was royal-blue, CR-081). Eleven
  themes exist in `theme/colorThemes.ts`, each light and dark.

## Current state (verified 2026-09-13)

Twenty-plus backend modules and twenty-four frontend modules are built and
compiling. Migrations V1–V55 are applied. This project is in **maintenance
and extension** — never treat it as greenfield.

| Layer | Reality |
|---|---|
| Backend | ~725 Java files, 66 controllers, 55 Flyway migrations, 93+ test classes |
| Frontend | ~330 TS/TSX files, 24 modules, 69 pages |
| Built end-to-end | Auth/Users/Roles, Tenant & Settings, Supplier, Customer, Category, Brand, Product, Inventory, Purchase, Quotation, Invoice, Payment, Expense, Project, Labour, Coupon, Dashboard, Address map picker (CR-076), Manual WhatsApp (CR-080) |
| Backend-only | Notification (email via SMTP or SendGrid, SMS via Twilio, WhatsApp per tenant — CR-074), AI chat, Legal/user-consent (entities only) |
| Not present | Any PWA surface, any offline/IndexedDB layer (CR-043 was never built) |

Module order (completed, kept for history): Auth → Supplier → Customer →
Category → Brand → Product → Product Variant → Purchase → Inventory →
Quotation → Invoice → Payment.

## Git workflow (CR-045)

```text
main                    production-ready; tagged releases (v1.0.0 onward)
  └── develop           integration
        ├── feature/*
        ├── bugfix/*
        └── hotfix/*    from main, merged back into BOTH main and develop
```

- **Never commit feature work directly to `main` or `develop`.** Branch from
  `develop`, merge back with `--no-ff`.
- Runtime environments are **Spring profiles** (`local`, `dev`, `test`,
  `prod`) — never branches.
- Conventional Commits (`feat:` `fix:` `refactor:` `docs:` `test:` `chore:`
  `security:`). One concern per commit; the body says **why**, and names the
  previous behaviour when a security boundary or default changes.
- Tag a release only after `mvn clean verify`, typecheck and build have all
  passed **on the exact commit being tagged**.

## Concurrent sessions — read before committing

More than one Claude session works this checkout at once. Consequences:

- `git status` lists files you never touched. Split it into *mine* and
  *theirs*, stage by explicit pathspec, and say so. When a shared file
  (a registry, `RESUME_POINT.md`, `tests/run.mjs`) carries both, stage a
  version built from `HEAD` plus your hunks via `git hash-object -w` +
  `git update-index --cacheinfo` — never `git add` the whole file.
- **Stage and commit in one command.** Two tool calls leave a window the
  other session will win.
- Their `mvn clean` deletes class files your suite is loading, and their
  `vite build` rewrites the `dist/` your preview serves. See
  `testing.md` → *Isolate before you believe a failure*.
- CR numbers collide. **A number is allocated by writing the registry row
  first**, and by grepping `project-knowledge/`, `backend/src`, `frontend/src`
  and `docs/` for it before claiming it. CR-067 was issued twice; CR-077–079
  were reserved by a branch nobody in this checkout could see.

## Environment quirks on this machine

- `python3` is **not installed** — `registry/static_check.py` and
  `check_registry.py` cannot run. Report "not executed", never "passed".
- npm script shims fail under Git Bash (`'"node"' is not recognized`).
  Invoke tools directly: `node ./node_modules/typescript/bin/tsc -b --force`,
  `node ./node_modules/vite/bin/vite.js build`.
- `openssl` exists in Git Bash (`/mingw64/bin`) but not PowerShell;
  `scripts/new-secret.ps1` is the PowerShell equivalent.
- `docker` is on PATH in Git Bash but its credential helper is invisible
  there. Use an isolated config dir holding an empty `config.json` and set
  `DOCKER_CONFIG` to it — the images are public.
- Two Java language servers may be installed; the NetBeans one cannot load
  Lombok and floods Lombok files with phantom errors. Trust
  `mvn -o clean compile`, not the IDE.
- Playwright's browser build must match the installed version; after a
  `package.json` bump run `node ./node_modules/playwright/cli.js install chromium`.
