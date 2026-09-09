# FEATURE REGISTRY

What is actually built, verified against the source tree and a green
`mvn clean verify` on 2026-09-09 (474 unit + 217 Testcontainers integration
tests, exit 0).

This project is in **maintenance and extension**, not greenfield build. The
originally locked module order was *completed*, not abandoned. Treat any
"planned" / "not yet built" wording found elsewhere as historical.

---

## Built end to end (backend + frontend)

| Module | Highlights |
|---|---|
| **Auth / Users / Roles** | login by mobile or email · BCrypt 12 · JWT access token held in memory only · opaque SHA-256 refresh tokens with rotation and reuse detection · `token_version` invalidation · logout vs logout-all · account lockout · forgot/reset password · MFA with backup codes · permission-based RBAC · soft-deleted users · last-owner protection · `security_audit_log` |
| **Tenant & Settings** | tenant row, shop profile, deployment modes, feature toggles, data reset (V54) |
| **Supplier** | CRUD, `SUP-nnnn` codes, contacts (one primary enforced by a partial unique index), credit limit and terms, soft delete |
| **Customer** | CRUD, search, due tracking, credit limit, credit days |
| **Category / Brand** | Category hierarchical (self-FK), Brand flat, `CAT-nnnn` / `BRD-nnnn` codes, delete refused while anything references them |
| **Product** | `PRD-nnnnnn`, barcode unique per tenant, HSN, GST rate, current pricing on the product row, cost fields hidden without `PRODUCT_VIEW_COST`, Excel/CSV import with preview then confirm, images, price history from recent sales |
| **Inventory** | stock, append-only `stock_movement`, warehouses, reorder rules, low and out-of-stock filtering (CR-070) |
| **Purchase** | PO to GRN to bill, landed cost, supplier price comparison, bill import with preview |
| **Quotation** | quote, revision, convert to invoice, margin badge |
| **Invoice** | GST / non-GST / mixed, GST on the final invoice rate, initial payment at creation, loss-sale protection with approval workflow, coupon application |
| **Payment** | full, partial, initial, later; history; reversal |
| **Sales Order / Delivery Challan / Credit Note** | order to challan to invoice to credit-note reversal, with the stock effect at each step |
| **Expense** | business expenses, categories with inline create, receipts |
| **Project** | project costing across customer, product, supplier, inventory, invoice and labour |
| **Labour** | labour master, work types, attendance, billing through invoice |
| **Coupon** | discount coupons applied at invoice time, per-line share rounded to whole paise |
| **Dashboard** | configurable widgets over invoice data |
| **Support ticket** | tenant-side ticket raising and messaging |

## Built, backend-only

| Module | State |
|---|---|
| **Notification** | email live; SMS and WhatsApp stubbed |
| **AI chat** | read-only tools over existing modules |
| **Analytics** | aggregation endpoints over invoice data |
| **Export** | PDF / Excel / CSV rendering; owns no entity |
| **Legal / user consent** | entities only, no controller |
| **Developer diagnostics** | double-gated: the environment **and** the `DEVELOPER_INSPECT` permission, which no default role holds |

## Built, cross-tenant (outside tenant isolation by design)

| Module | State |
|---|---|
| **Platform admin** | 15 controllers, 21 entities: tenants, incidents, feature flags, jobs, Razorpay config, its own admin identity, its own refresh tokens, its own `platform_audit_log` |
| **Billing** | subscription and billing on top of platform admin |

## Not present, deliberately

- **No PWA surface.** No service worker, no install prompt.
- **No offline / IndexedDB layer.** CR-043 was recorded and never built.
- **No public self-registration endpoint** (CR-008): the owner creates accounts.
- **No microservices, Kubernetes or event-driven infrastructure** (CR-001).
- **Product Variant** is still a future increment. Current pricing lives
  directly on `product`; see the deviation note below.

---

## Product carries current price directly, a deliberate deviation from CR-004

CR-004 originally put all pricing and price history exclusively on Product
Variant. It was built instead with current pricing on `product`
(`purchase_price_paise`, `selling_price_paise`, `mrp_paise`), because most
hardware-shop items are single-SKU and forcing every product through a
mandatory variant indirection added friction for zero benefit.

Product Variant remains a real future increment, not abandoned, and still owns:

- multiple sizes and finishes per product with independent SKUs and prices;
- `product_price_history` as an event log (`old_purchase_price`,
  `new_purchase_price`, `effective_date`). Today a price change on `product`
  simply overwrites the current price.
- `recommended_selling_price` and `minimum_selling_price`, which the full
  GOOD to WARNING to APPROVAL REQUIRED to LOSS SALE ladder needs and which do
  not exist on `product` today.

What ships today in place of the history table: `ProductServiceImpl.priceHistory()`
lists the recent **actual sale** prices for a product, read from invoice items
with cancelled invoices excluded. That is a sales record, not a price-change
audit. The two are not interchangeable, and the CR-004 price-history rule below
still stands unimplemented.

Invoice lines snapshot `product.sellingPricePaise` at sale time rather than
joining live: the "cost on an invoice line is a snapshot, not a lookup" rule
holds regardless of where current price is stored.

---

## Business rules locked by CR-004

- **Price history:** `product_price_history`, never overwritten. Fields:
  history_id, variant_id, old_purchase_price, new_purchase_price, supplier_id,
  effective_date, updated_by, updated_at. *(Awaits Product Variant.)*
- **Import preview:** never update prices directly. Show Product Name, Current
  Price, New Price, Difference, Difference %. *(Preview then confirm is built;
  the price-difference columns arrive with Variant.)*
- **Price decrease gives a RED alert**, `stock_qty × (old − new)` = potential
  stock value loss. **Price increase gives GREEN**, `stock_qty × (new − old)`.
- **Loss-sale states:** invoice price above recommended = GOOD; between minimum
  and recommended = WARNING; below minimum = APPROVAL REQUIRED; below purchase
  price = LOSS SALE.
- **Approval:** an employee cannot approve. Manager requests, owner approves.
  Stored: approval_required, approval_status, approved_by, approved_at,
  approval_reason.
- **Special discount:** original_rate, invoice_rate, discount_amount,
  discount_percentage, discount_reason.
- **GST always on the final invoice rate**, never the master price.
- **Price audit trail:** original rate, new rate, difference, user, timestamp,
  reason. Never deleted.

---

## Deferred, recorded rather than skipped

Backup and restore · document management · approval workflow engine ·
smart product search (keywords, aliases, spoken terms) · product knowledge
engine (door weight, material, rust resistance, compatible and alternative
products) · PDF catalogue import · OCR and document extraction ·
AI-assisted product creation.

Any future OCR or extraction feature must follow the established
preview, confirm, import shape, surface a confidence or match status per line,
and never silently commit an uncertain match.

---

## Rejected

| Feature | Reason |
|---|---|
| ~~Single-tenant single shop~~ | Reversed by CR-016 (2026-08-22): multi-tenant, shared schema, `tenant_id` isolation |
| Microservices / Kubernetes / event-driven | CR-001 |
| Public self-registration | CR-008, **APPROVED 2026-08-13**: owner-created users only |
| Seed accounts in production migrations | CR-009, **APPROVED 2026-08-13**: `db/migration` is schema, `db/seed` is dev and test only |
| H2 for integration tests | Replaced by Testcontainers **PostgreSQL 16** |
| Role-name authorization (`hasRole`) | Permission-based only |
| DevTools blocking, or a frontend-only diagnostics gate | CR-045: diagnostics stay behind two independent server-side gates |
