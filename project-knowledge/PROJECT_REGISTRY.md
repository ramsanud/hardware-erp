# PROJECT REGISTRY

**Project:** Hardware Shop ERP
**Type:** Multi-tenant (CR-016, 2026-08-22). Not microservices, not Kubernetes,
not database-per-tenant or schema-per-tenant — one shared schema, isolated by
a `tenant_id` discriminator column enforced server-side from the JWT.
**Architecture:** Monolith — one Spring Boot JAR, one PostgreSQL database
(CR-014), one React app.

## Technology stack (LOCKED)

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.2 |
| Security | Spring Security + JWT access tokens + opaque refresh tokens |
| Database | PostgreSQL 16 (14+ supported) |
| Migration | Flyway |
| API docs | springdoc-openapi 2.7.0 (`/api/swagger-ui.html`) |
| Rate limiting | Bucket4j 8.10.1 + Caffeine (in-process) |
| Build | Maven |
| Frontend | React 18 + TypeScript + Vite + Tailwind + shadcn/ui |
| Integration tests | Testcontainers + PostgreSQL 16 (**not H2**) |
| IDE | IntelliJ IDEA |

## Package structure (LOCKED)

```
com.hardware.erp
├── common.{entity,dto,exception,web}
├── config
├── security.{,ratelimit}
└── <module>.{entity,repository,dto,mapper,service,service.impl,controller}
```

Module packages: `auth` (M1), then `customer`, `supplier`, `category`, `brand`,
`product`, `purchase`, `inventory`, `quotation`, `invoice`.

## Naming standards (LOCKED)

| Rule | Value |
|---|---|
| DB → Java | `snake_case` → `camelCase`, no other transformation |
| Table names | singular, `snake_case`; line tables `<parent>_item` |
| Primary key column | `<table>_id`; Java field always `id` |
| FK column | `<target>_id`; entity holds the object, DTO holds `<target>Id` |
| Timestamps | `DATETIME(3)` / `LocalDateTime`, suffix `_at` |
| Dates | `DATE` / `LocalDate`, suffix `_date` |
| Booleans | no `is_` prefix (`system_role`, not `is_system_role`) |
| Lifecycle state | `status VARCHAR(20)` + CHECK — never TINYINT, never a native ENUM type |
| Money amounts | `BIGINT` paise (corrected 2026-08-23 — every table since Module 6/7 has used paise, never `DECIMAL(15,2)`; this row was wrong, not just stale) |
| Unit rates | `DECIMAL(18,6)` — a ₹875 box of 1000 screws is ₹0.875 each |
| Quantities | `DECIMAL(18,4)` |
| API paths | `/api/v1/<plural-kebab-noun>`; no verbs except under `/auth` |

Banned aliases: `name`, `customerFullName`, `clientName`, `phoneNo`,
`phoneNumber`, `emailId`, `gstin`, `gstNumber`, `AppUserService`,
`UserManagementService`, `UserHelper`, `UserUtil`.

Enforced by `registry/check_registry.py` (fails the build on any rename).

## Module status

**Corrected 2026-08-23** — this table had not been updated since CR-021
(2026-08-22) and had drifted badly out of date; see `MASTER_PROJECT_STATUS.md`
§1 for the full documentation-drift finding across five registry files.
Order originally LOCKED by CR-007 + CR-011 (Customer before Category/Brand/
Product). The owner re-scoped "Module 3" to Category + Brand + Product
together, ahead of Customer, on 2026-08-22 (CR-020); Inventory and
Invoice/Payment moved ahead of full Customer management too (CR-021);
Customer, Quotation, Payment, and a set of cross-cutting features (coupons,
tenant self-registration, notifications, subscription tiers, AI assistant)
shipped in the CR-022 through CR-028 rounds since. Product Variant and
Purchase remain data-dependency-ordered per PROJECT_SKILLS #22 and are the
only two modules from the original 12 still not started.

| # | Module | Backend | Frontend | Tests | Status |
|---|---|---|---|---|---|
| 1 | Authentication & User Management | DONE | DONE | 149 written | IN PROGRESS (core paths verified live) |
| 2 | Supplier | DONE | DONE (wizard) | 37 written | IN PROGRESS — CR-018 bank encryption applied 2026-08-23 |
| 3 | Category, Brand & Product | DONE | DONE | 0 written | IN PROGRESS (verified live) |
| 4 | Inventory | DONE | DONE | 0 written (exercised via Invoice tests) | IN PROGRESS (verified live) |
| 5 | Customer | DONE (full CRUD, CR-023) | DONE | covered | IN PROGRESS |
| 10 | Quotation | DONE (CR-022) | DONE | 7 written | IN PROGRESS — no coupon redemption |
| 11 | Invoice | DONE, coupon-aware (CR-022/028) | DONE (4-step wizard) | 10+ written | IN PROGRESS |
| 12 | Payment | DONE (standalone view, CR-027) | DONE | 3 written | IN PROGRESS |
| — | Coupons | DONE (CR-028) | DONE | 9 written | cross-cutting, not part of the original 1-12 |
| — | Tenant self-registration | DONE (CR-028) | DONE | 5 written | fulfills CR-016's deferred 2nd-tenant provisioning |
| — | Notifications | DONE, email real / SMS+WhatsApp stub (CR-027) | DONE (contact-admin only, no log viewer) | 7 written | cross-cutting |
| — | Subscription tiers | DONE, self-declared, no payment gateway (CR-027); entitlement limits (owners/customers/suppliers/products) enforced server-side (CR-031); trial coupons to grant a plan free for a set period (CR-032) | DONE | — | cross-cutting |
| — | AI Assistant | DONE, Anthropic-only, no key configured (CR-027) | DONE | 4 written | cross-cutting |
| — | Product Variant (price history, loss-sale workflow) | — | — | — | deferred — see FEATURE_REGISTRY deviation note |
| — | Purchase | — | — | — | not started — blocks real Supplier Payables, see `MASTER_PROJECT_STATUS.md` §4.4 |
| 8 | Project Management | DONE (CR-029) | DONE | 3+7=10 written | work types, projects, materials, expenses, payments, server-computed profitability, rooftop calculator — live-verified end to end |
| — | Labour / Team / Attendance | — | — | — | not started — Phase 7, see MASTER_PROJECT_STATUS.md |
| — | Finance / Cash-Bank-Cheque ledger | — | — | — | not started — Phase 8 |
| — | Reports (daily/weekly/monthly/yearly) | — | — | — | not started — Phase 9 |

There is now a public tenant self-registration endpoint (`POST
/v1/tenants/register`, rate-limited, CR-028) — CR-008's "no self-registration"
decision still stands for *users within* an existing tenant (the owner
creates those via `POST /api/v1/users`); only *new-tenant* provisioning
became self-service, which CR-016 always anticipated as a deferred follow-up.

## Known constraints

- Maven Central was unreachable from the original authoring environment, so
  the backend went unbuilt and unrun for a long stretch (BUG-ENV-001). Closed
  2026-08-22: a working environment compiled and ran it against real
  PostgreSQL 16, surfacing and fixing six defects invisible to static checks
  (BUG-AUTH-010/011/012, BUG-SUP-002/003/004 - see BUG_REGISTRY.md).
- `mvn clean verify` completed a clean run for the first time 2026-08-22,
  after fixing BUG-TEST-001 (unit tests never updated for CR-016's tenant
  retrofit - did not compile), BUG-AUTH-013 (pre-existing Mockito misuse),
  BUG-MONEY-001 (Indian digit grouping was silently broken everywhere - real
  production bug) and BUG-BUILD-001 (the `*ControllerIT` suite - the
  majority of the "184 tests written" figure - had never once executed
  because `maven-failsafe-plugin` was never wired into `pom.xml`). All 89
  non-Docker-dependent tests now pass. The 7 Testcontainers-dependent
  classes (2 `*Test`, 5 `*IT`) cannot run on this machine - see BUG-ENV-002,
  open: Docker Desktop is healthy but testcontainers-java cannot negotiate
  its API over either named pipe it exposes. Do not mark Module 1 or 2 fully
  verified until BUG-ENV-002 is resolved and the Testcontainers suite
  actually runs green.
- Backup/restore, notification centre, document management and approval
  workflow are recorded in FEATURE_REGISTRY as post-Module-11 work. They are
  not folded into Module 1.
