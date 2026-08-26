# FEATURE REGISTRY

## Implemented (Module 1, in progress under CR-003)

Login by mobile or email · BCrypt 12 · JWT access tokens with minimal claims ·
opaque SHA-256-hashed refresh tokens · rotation · reuse detection ·
token_version invalidation · logout / logout-all separation · account lockout ·
forgot/reset password · permission-based RBAC · soft-deleted users ·
last-owner protection · security audit log · pagination + sort whitelist ·
correlation id · standard response envelope · rate limiting · Swagger.

## Implemented — Category, Brand, Product (2026-08-22)

Built as "Module 3" per the owner's own re-numbering (Category + Brand +
Product together), ahead of Customer in the originally locked order. See the
module-order note in `CHANGE_REQUEST_REGISTRY.md`. All three tenant-scoped
from their first migration (CR-016).

- **Category**: hierarchical (`parent_category_id`, self-FK), `CAT-nnnn`
  auto-generated code, hard-deletable only when no product or sub-category
  references it.
- **Brand**: flat, `BRD-nnnn` auto-generated code, same delete protection.
- **Product**: `product_code` (`PRD-nnnnnn`, unique per tenant),
  `category_id`, `brand_id`, `model_no`, `manufacturer_code`, `barcode`
  (unique per tenant), `unit`, `hsn_code`, `gst_rate_percent`. Identification
  priority in search is barcode → manufacturer_code → model_no →
  product_code, never name alone, per the original CR-004 spec.
  Soft-deleted (deactivated), never hard-deleted - future Purchase/Inventory/
  Invoice modules will reference `product_id` permanently.
- **Current pricing lives directly on `product`** (`purchase_price_paise`,
  `selling_price_paise`, `mrp_paise`) rather than being deferred entirely to
  Product Variant - see the "Product carries current price" note below for
  why, and what Product Variant still owns.
- **Cost visibility**: `purchasePricePaise`/`purchasePriceDisplay` are
  omitted from every response unless the caller holds
  `PRODUCT_VIEW_COST` (STAFF has `PRODUCT_VIEW` but not
  `PRODUCT_VIEW_COST` - unchanged from the Module 1 design). The list
  projection (`ProductSummaryResponse`) omits it unconditionally, matching
  `SupplierSummaryResponse`.
- Excel/CSV import with preview: not yet built - still applies to the "Import
  preview" business rule below once it lands.

### Product carries current price directly - a deliberate deviation from the original CR-004 shape

CR-004 originally put all pricing (and price history) exclusively on Product
Variant, so a product's price never lived on the Product row itself. Built
instead with current pricing directly on `product`, because most hardware-shop
items are single-SKU and forcing every product through a mandatory variant
indirection added friction for zero benefit. Product Variant remains a real,
separate future increment - not abandoned - and still owns:

- Multiple sizes/finishes per product with independent SKUs and prices.
- `product_price_history` - price changes on `product` today simply
  overwrite the current price; once Variant lands, a price change becomes an
  event with `old_purchase_price`, `new_purchase_price`, `effective_date`,
  matching the rule below.
- The full loss-sale approval workflow (GOOD/WARNING/APPROVAL
  REQUIRED/LOSS SALE), which needs `recommended_selling_price` and
  `minimum_selling_price` - fields that do not exist on `product` today.

Until Product Variant exists, an invoice line item (Module 11, not yet
built) must snapshot `product.sellingPricePaise` at sale time rather than
trusting a live join - the "cost on an invoice line is a snapshot, not a
lookup" rule below applies regardless of where current price is stored.

## Planned — remaining modules

| Module | Key features |
|---|---|
| Inventory | stock, stock_movement (append-only), warehouse, reorder rules, dead/slow stock |
| Customer | CRUD, search, due tracking, credit limit, credit days |
| Sales / Invoice | GST/non-GST/mixed, **GST computed on final invoice rate, never master price**, optional initial payment at creation, loss-sale protection with approval workflow |
| Payment | full/partial/initial/later payment, payment history, reversal |
| Product Variant | SKU, attributes, current_purchase_price, current_selling_price, recommended_selling_price, minimum_selling_price, `product_price_history` - see the deviation note above |
| Purchase | PO → GRN → bill, landed cost, supplier price comparison |
| Quotation | quote → revision → convert to invoice, margin badge |

## Business rules locked by CR-004

- **Price history:** `product_price_history` — never overwrite. Fields:
  history_id, variant_id, old_purchase_price, new_purchase_price, supplier_id,
  effective_date, updated_by, updated_at.
- **Import preview:** never update prices directly. Show Product Name, Current
  Price, New Price, Difference, Difference %.
- **Price decrease → RED alert**, formula `stock_qty × (old − new)` =
  potential stock value loss. **Price increase → GREEN**, `stock_qty × (new − old)`.
- **Loss-sale states:** invoice price > recommended = GOOD; between minimum and
  recommended = WARNING; below minimum = APPROVAL REQUIRED; below purchase price
  = LOSS SALE (red alert with loss per unit, qty, total loss).
- **Approval:** Employee cannot approve. Manager requests. Owner approves.
  Stored: approval_required, approval_status, approved_by, approved_at,
  approval_reason.
- **Special discount:** original_rate, invoice_rate, discount_amount,
  discount_percentage, discount_reason.
- **GST always on final invoice rate.**
- **Price audit trail:** original rate, new rate, difference, user, timestamp,
  reason. Never deleted.

## Deferred — post Module 11 (recorded, not skipped)

Backup & restore · notification centre · document management · approval
workflow engine · report export (PDF/Excel/CSV) · dashboard analytics ·
master settings · smart product search (keywords, aliases, spoken terms) ·
product knowledge engine (door weight, material, rust resistance,
compatible/alternative products) · PDF catalogue import · OCR ·
AI-assisted product creation.

**Why deferred rather than built into Module 1:** each depends on data that does
not exist until Products, Inventory and Invoices exist. Backup/restore is an
operations task, not a code module. Building them now means building against
imaginary tables.

## Rejected

| Feature | Reason |
|---|---|
| ~~Multi-tenancy~~ | Reversed by CR-016 (2026-08-22) — now multi-tenant, shared schema, `tenant_id` isolation |
| Microservices / Kubernetes / event-driven | CR-001 |
| Public self-registration | CR-008 — recommended rejection, awaiting decision |
| Seed accounts in production migrations | CR-009 — recommended rejection, awaiting decision |
| H2 for integration tests | Replaced by Testcontainers MySQL 8 |
| Role-name authorization (`hasRole`) | Permission-based only |
