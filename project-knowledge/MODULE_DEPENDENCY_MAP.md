# MODULE DEPENDENCY MAP

One Spring Boot monolith, one **PostgreSQL 16** database, one React
application, **multiple tenants sharing one schema** via a `tenant_id`
discriminator (CR-016). Dependencies below are **package-level imports**, not
network calls. Module folders express boundaries, not deployment units.

Verified against the source tree on 2026-09-09: 29 backend packages
(26 with controllers), 22 frontend modules.

---

## Foundation — depended on by everything

| Package | Owns |
|---|---|
| `auth` | identity, JWT, permissions, roles, users, MFA, refresh tokens |
| `tenant` | the tenant row, shop settings, subscription state |
| `common` | `BaseEntity`, `ApiResponse`, `PageResponse`, exception hierarchy, `GlobalExceptionHandler`, `RequestCorrelationFilter`, money/discount utilities |
| `security` | `SecurityUtils`, JWT filters, rate limiting, permission evaluation |
| `config` | Spring wiring, deployment-mode guards |

Every tenant-owned module reads its tenant from
`SecurityUtils.requireCurrentTenantId()` — never from a request parameter or
path variable. A module that needs auth imports from `security/`; it never
ships its own JWT handling, error envelope, or API client.

---

## Business modules — actual import graph

Derived from the imports present in the code, not from the original plan.

```
auth + tenant  (every module below depends on both)
 │
 ├── supplier
 ├── customer
 ├── product ──┬── (category, brand live inside `product`)
 │             └── coupon ── product
 │
 ├── inventory ─── product
 │
 ├── purchase ────┬── supplier
 │                ├── product
 │                ├── inventory
 │                └── invoice
 │
 ├── quotation ───┬── customer
 │                ├── product
 │                └── invoice
 │
 ├── invoice ─────┬── customer
 │                ├── product
 │                ├── inventory
 │                ├── coupon
 │                └── notification
 │
 ├── salesorder ──┬── customer, product
 │                ├── invoice
 │                └── deliverychallan
 │
 ├── deliverychallan ─┬── customer, product
 │                    ├── inventory
 │                    └── invoice
 │
 ├── creditnote ──┬── customer, product
 │                ├── inventory
 │                └── invoice
 │
 ├── project ─────┬── customer, product, supplier
 │                ├── inventory
 │                ├── invoice
 │                └── labour
 │
 ├── labour ──────┬── project
 │                └── invoice
 │
 ├── expense ───── invoice
 ├── dashboard ─── invoice
 ├── analytics ─── invoice
 ├── ai ────────── (read-only tools over the modules above)
 ├── export ────── (rendering only; owns no entity)
 ├── notification  (email live; SMS/WhatsApp stubbed)
 ├── supportticket
 ├── legal         (entities only — no controller)
 └── developer     (diagnostics, double-gated: environment + DEVELOPER_INSPECT)

platformadmin  ── cross-tenant, sits OUTSIDE tenant isolation by design
 └── billing ──── platformadmin
```

### Why `invoice` is imported so widely

`invoice` is the settlement point of the system: sales orders convert to it,
delivery challans bill against it, credit notes reverse it, projects and labour
bill through it, expenses reconcile against it, and both dashboard and
analytics aggregate it. It is a dependency magnet by design, which is why it
must stay free of upward imports — verified 2026-09-09: nothing in `invoice`
imports `salesorder`, `creditnote`, `deliverychallan`, `project`, `labour`,
`dashboard`, `analytics` or `expense`.

### Known cycle: `product` ↔ `invoice`

One bidirectional edge exists and is recorded here rather than hidden:

| Direction | Where | Why |
|---|---|---|
| `invoice` → `product` | invoice lines resolve a product | expected |
| `product` → `invoice` | `ProductServiceImpl.priceHistory()` injects `InvoiceItemRepository` and `InvoiceStatus` to list a product's recent sale prices | the cycle |

The behaviour is correct and tenant-scoped (`findRecentForProduct(id, tenantId, …)`,
cancelled invoices excluded). The coupling is the problem, not the query: it
means `product` can no longer be reasoned about, or extracted, without
`invoice`. `product` also reaches `purchase` for
`DocumentUploadValidation` in `ProductImportServiceImpl` — shared upload
validation that belongs in `common/`.

Neither is a defect in behaviour, so neither is being changed under a
documentation pass. Untangling them (a read port owned by `product`, or moving
`priceHistory` behind an `invoice` endpoint, plus lifting
`DocumentUploadValidation` into `common/`) is a refactor that must be proposed
as its own CR — see the "never silently build large new subsystems" bound in
CLAUDE.md.

### `platformadmin` is deliberately outside tenant isolation

It is the only package that reads across tenants. It therefore does **not**
use `SecurityUtils.requireCurrentTenantId()`, carries its own admin identity,
its own refresh tokens and its own audit log (`platform_audit_log`), and is
reached through separate endpoints. Never let a tenant-facing controller call
into it.

---

## Build order and why it was not negotiable

Kept for history — the order below is **complete, not pending**.

| Module | Cannot be built before | Reason |
|---|---|---|
| PRODUCT | CATEGORY, BRAND | `category_id` and `brand_id` are NOT NULL FKs (CR-004) |
| PURCHASE | SUPPLIER, PRODUCT | a purchase line needs a supplier and a product |
| INVENTORY | PURCHASE | stock arrives through goods receipt |
| INVOICE | PURCHASE, INVENTORY | loss-sale protection needs `purchase_price`; the stock-value alert formula `stock_qty × (old − new)` needs `stock_qty` |
| PAYMENT | INVOICE | a receipt settles an invoice |

CR-007 moved PURCHASE and INVENTORY ahead of INVOICE for exactly this reason.
Building INVOICE first would have left the loss-sale alert, the RED/GREEN price
alert and the margin badge stubbed, then retrofitted into a module already
signed off as complete.

---

## Class-level dependency comments

Every class that reaches into another module declares it:

```java
/**
 * Depends On:
 *   Category Module - category must exist and be active
 *   Brand Module    - brand must exist and be active
 *
 * Do not remove the existence checks in createProduct(); the FKs are
 * NOT NULL and a missing check surfaces as a 500 instead of a 422.
 */
public class ProductServiceImpl implements ProductService { }
```

---

## Shared code — never duplicated per module

**Backend:** `common/` (BaseEntity, ApiResponse, ErrorResponse, PageResponse,
exception hierarchy, GlobalExceptionHandler, RequestCorrelationFilter,
`IndianCurrencyFormat`, `LineDiscount`), `security/` (JWT, filters, rate
limiting, SecurityUtils), `config/`.

**Frontend:** `shared/` (api client, interceptors, permission hooks, UI
components, form utilities), `layouts/`, `routes/`, `theme/` (dark and light).

Money is `BIGINT` paise end to end. `BigDecimal` appears only as intermediate
precision inside `LineDiscount` and `IndianCurrencyFormat`, rounded back to
whole paise exactly once — it is never a stored type and never a DTO type.
