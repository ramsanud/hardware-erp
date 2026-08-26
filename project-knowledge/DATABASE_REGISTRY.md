# DATABASE REGISTRY

Engine: **PostgreSQL 14+** (developed and tested against 16-alpine), database
encoding `UTF8`, locale `C.UTF-8`.

Migrated from MySQL 8 under **CR-014**. Table and column names are unchanged.
Flyway owns the schema. Hibernate is `ddl-auto: validate` and never `update`.

## Migration history

| Version | File | Scope | Environments |
|---|---|---|---|
| V1 | `V1__auth_schema.sql` | Module 1 schema + roles + permissions + `set_updated_at()` | ALL |
| V2 | `V2__supplier_schema.sql` | Module 2: supplier, supplier_contact | ALL |
| V3 | `V3__activity_log.sql` | Business activity log (CR-015) | ALL |
| V4 | `V4__fix_token_hash_column_type.sql` | `refresh_token`/`password_reset_token.token_hash` CHAR→VARCHAR(64) (BUG-AUTH-011) | ALL |
| V5 | `V5__fix_supplier_column_types.sql` | `supplier.state_code`/`pincode` CHAR→VARCHAR (BUG-SUP-003) | ALL |
| V6 | `V6__multi_tenant_foundation.sql` | `tenant` table, `tenant_id` on role/app_user/supplier (CR-016) | ALL |
| V7 | `V7__category_brand_product_schema.sql` | Module 3: category, brand, product | ALL |
| V8 | `V8__inventory_schema.sql` | Module 4: stock, stock_movement (CR-021) | ALL |
| V9 | `V9__customer_invoice_payment_schema.sql` | Module 7 + minimal Module 5: customer, invoice, invoice_item, payment (CR-021) | ALL |
| V10 | `V10__invoice_gst_and_quotation_schema.sql` | Shop GST/address on `tenant`, address/state on `customer`, Module 10: quotation, quotation_item (CR-022) | ALL |
| V11 | `V11__image_storage.sql` | `user_avatar`, `tenant_logo`, `tenant_signature` - 1:1 image tables, deliberately not columns on app_user/tenant (CR-023) | ALL |
| V12 | `V12__invoice_gst_document_fields.sql` | Shop PAN/phone/email/bank fields on `tenant`, invoice shipment fields, `invoice_item`/`quotation_item.unit` (CR-025) | ALL |
| V13 | `V13__tenant_upi_qr.sql` | `tenant_upi_qr` - 1:1 image table, same pattern as `tenant_logo`/`tenant_signature` (CR-026) | ALL |
| V14 | `V14__notification_log.sql` | `notification_log` - audit trail of every outbound email/SMS/WhatsApp attempt (CR-027) | ALL |
| V15 | `V15__subscription_tier.sql` | `tenant.subscription_tier` (`FREE`/`PRO`/`MAX`, CHECK-constrained), default `FREE` (CR-027) | ALL |
| V16 | `V16__coupons.sql` | Module: `coupon`, `coupon_redemption` - retail invoice discounts, tenant-scoped (CR-028) | ALL |
| V17 | `V17__encrypt_supplier_bank_account.sql` | `supplier.bank_account_no` widened for AES-256-GCM ciphertext (CR-018) | ALL |
| V18 | `V18__project_management.sql` | Module 8: `work_type`, `project`, `project_material`, `project_expense`, `project_payment` (CR-029) | ALL |
| V19 | `V19__work_type_audit_columns.sql` | `work_type` missing `created_by`/`updated_by` (BUG-PROJ-001 follow-up) | ALL |
| V20 | `V20__subscription_coupons.sql` | `subscription_coupon`, `tenant.subscription_trial_expires_at` (CR-032) | ALL |
| V21 | `V21__purchase_schema.sql` | Module 9: `purchase`, `purchase_item`, `purchase_payment`, `purchase_document` (CR-035) | ALL |
| V22 | `V22__tenant_bank_accounts.sql` | `tenant_bank_account`, `tenant_bank_account_qr`, `invoice.bank_account_id`/`bank_account_qr_id` (CR-036) | ALL |
| V23 | `V23__product_image.sql` | `product_image` - 1:1 image table, same pattern as `tenant_logo`/`user_avatar` (CR-036) | ALL |
| V24 | `V24__business_expense.sql` | `expense_category`, `business_expense`, `expense_receipt` - standalone shop-wide expense ledger (CR-036 phase 3) | ALL |
| V25 | `V25__labour_module.sql` | `worker`, `worker_attendance`, `worker_payment` - Labour Monitor: worker directory, daily attendance, wage/payroll (CR-036 phase 4) | ALL |
| V26 | `V26__worker_payment_status.sql` | `worker_payment.status` (ACTIVE/CANCELLED) - a mistyped payment had no in-app correction (BUG-LAB-005, CR-037) | ALL |
| V27 | `V27__backfill_labour_grants_for_registered_tenants.sql` | Data-only repair: grants `LABOUR_VIEW`/`LABOUR_MANAGE` to MANAGER/ACCOUNTANT roles of shops registered after V25, which never received them (BUG-LAB-006, CR-037). Anti-join, safe to re-run | ALL |
| V28 | `V28__user_consent.sql` | `user_consent` - append-only record of which legal document version each user accepted; supports re-consent and marketing withdrawal, stores no IP/device data (CR-040) | ALL |
| V900 | `db/seed/V900__seed_dev_data.sql` | Module 1 sample rows | DEV/TEST ONLY (CR-009) |
| V901 | `db/seed/V901__seed_dev_supplier.sql` | 13 suppliers, 13 contacts, activity history | DEV/TEST ONLY |
| V902 | `db/seed/V902__seed_dev_products.sql` | 12 products with opening stock, so an invoice can be raised immediately | DEV/TEST ONLY |
| V903 | `db/seed/V903__seed_dev_categories_brands.sql` | 7 categories, 8 brands, backfills category_id/brand_id on existing products | DEV/TEST ONLY |

V4 and V5 fix the same class of defect: a column declared `CHAR(n)` in the
migration but mapped `@Column(length = n)` with no `columnDefinition` on the
entity, which Hibernate resolves to `VARCHAR(n)`. `ddl-auto: validate`
correctly refused to start until these were fixed forward - see BUG-AUTH-011
and BUG-SUP-003 in `BUG_REGISTRY.md`. `token_hash` and `state_code`/`pincode`
below are documented as `VARCHAR` now, reflecting V4/V5, not their original V1/V2
declarations.

## PostgreSQL conventions (locked by CR-014)

| Concern | Convention |
|---|---|
| Primary keys | `BIGINT GENERATED BY DEFAULT AS IDENTITY` — not `SERIAL`, which is legacy and does not own its sequence cleanly |
| Timestamps | `TIMESTAMP(3)` — millisecond precision, matching `LocalDateTime` |
| Booleans | native `BOOLEAN` |
| Enumerated columns | `VARCHAR(n)` + `CHECK (col IN (...))`, never a PostgreSQL `ENUM` type — adding a value to an enum type requires `ALTER TYPE` and cannot run inside every transaction |
| `updated_at` maintenance | Spring Data JPA auditing where the entity extends `BaseEntity`; the `set_updated_at()` trigger only where it does not (`permission`) |
| Case-insensitive uniqueness | functional unique index on `lower(col)` — see BUG-AUTH-009 |
| Selective indexes | partial indexes with `WHERE`, e.g. active users, live sessions, failed audit events |
| Named constraints | every PK, FK, UNIQUE and CHECK is explicitly named, so an error message identifies the rule |

Rule: an applied migration is never edited. Corrections go in a new version.

---

## Module 1 tables

### `permission`
First-class table so Modules 2–11 add permissions through their own migration
instead of editing a frozen Java constant list.

| Column | Type | Null | Notes |
|---|---|---|---|
| permission_id | BIGINT IDENTITY | NO | PK |
| permission_code | VARCHAR(60) | NO | UNIQUE `uk_permission_code` |
| permission_name | VARCHAR(120) | NO | |
| description | VARCHAR(255) | YES | |
| module_code | VARCHAR(30) | NO | AUTH, CUSTOMER, SUPPLIER, PRODUCT, PURCHASE, SALES, INVENTORY, PAYMENT, EXPENSE, REPORT, SETTINGS |
| display_order | INTEGER | NO | groups the permission picker in the UI |
| created_at / updated_at | TIMESTAMP(3) | | |

Index: `idx_permission_module (module_code, display_order)`

### `role`
| Column | Type | Null | Notes |
|---|---|---|---|
| role_id | BIGINT IDENTITY | NO | PK |
| role_code | VARCHAR(30) | NO | UNIQUE |
| role_name | VARCHAR(100) | NO | UNIQUE |
| description | VARCHAR(255) | YES | |
| system_role | BOOLEAN | NO | system roles cannot be deleted |
| status | VARCHAR(20) | NO | CHECK IN ('ACTIVE','INACTIVE') |
| created_at, created_by, updated_at, updated_by, version | | | audit block + `@Version` |

### `role_permission`
| Column | Type | Notes |
|---|---|---|
| role_id | BIGINT | PK part, FK → role, ON DELETE CASCADE |
| permission_id | BIGINT | PK part, FK → permission, ON DELETE RESTRICT |

RESTRICT is deliberate: deleting a permission that roles still reference must fail.

### `app_user`
Named `app_user` because `USER` is reserved in MySQL 8. **Never hard-deleted** —
ERP records reference `created_by` for the life of the business.

| Column | Type | Null | Notes |
|---|---|---|---|
| user_id | BIGINT IDENTITY | NO | PK |
| role_id | BIGINT | NO | FK → role, ON DELETE RESTRICT |
| employee_code | VARCHAR(30) | YES | UNIQUE |
| full_name | VARCHAR(200) | NO | |
| mobile_no | VARCHAR(15) | NO | UNIQUE |
| email | VARCHAR(255) | YES | UNIQUE |
| password_hash | VARCHAR(255) | NO | BCrypt strength 12 |
| status | VARCHAR(20) | NO | CHECK IN ('ACTIVE','INACTIVE','SUSPENDED') |
| must_change_password | BOOLEAN | NO | |
| token_version | INTEGER | NO | CHECK >= 0; bumping invalidates all access tokens |
| failed_login_attempts | INTEGER | NO | CHECK >= 0 |
| locked_until | TIMESTAMP(3) | YES | 5 failures → +15 min |
| last_login_at, password_changed_at | TIMESTAMP(3) | YES | |
| created_at … version | | | audit block + soft delete + `@Version` |
| deleted_at, deleted_by | | YES | soft delete |

Indexes: `idx_user_role`, `idx_user_status (status, deleted_at)`, `idx_user_full_name`

### `refresh_token`
One row per session. Only `SHA-256(token)` is stored — a database dump yields
nothing usable.

| Column | Type | Notes |
|---|---|---|
| refresh_token_id | BIGINT IDENTITY | PK |
| user_id | BIGINT | FK → app_user, CASCADE |
| token_hash | VARCHAR(64) | UNIQUE. CHAR(64) in V1, fixed to VARCHAR by V4 (BUG-AUTH-011) |
| expires_at | TIMESTAMP(3) | |
| revoked_at | TIMESTAMP(3) | |
| revoked_reason | VARCHAR(40) | ROTATED, LOGOUT, LOGOUT_ALL, REUSE_DETECTED, PASSWORD_CHANGED, USER_DEACTIVATED |
| replaced_by_token_id | BIGINT | self-FK, ON DELETE SET NULL — walkable rotation chain |
| ip_address | VARCHAR(45) | IPv6-capable |
| user_agent | VARCHAR(255) | |
| last_used_at, created_at | TIMESTAMP(3) | |

Indexes: `idx_refresh_token_user (user_id, revoked_at)`, `idx_refresh_token_expiry`

### `password_reset_token`
| Column | Type | Notes |
|---|---|---|
| reset_token_id | BIGINT IDENTITY | PK |
| user_id | BIGINT | FK → app_user, CASCADE |
| token_hash | VARCHAR(64) | UNIQUE — raw token never stored. CHAR(64) in V1, fixed by V4 |
| expires_at | TIMESTAMP(3) | 30 minutes |
| used_at | TIMESTAMP(3) | single-use |
| ip_address, created_at | | |

### `security_audit_log`
**Security events only.** Business transaction history belongs to each ERP
module's own tables. No FK to `app_user`: the log must remain readable
regardless of what happens to the user row.

| Column | Type | Notes |
|---|---|---|
| audit_id | BIGINT IDENTITY | PK |
| action | VARCHAR(40) | LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, LOGOUT_ALL, PASSWORD_CHANGED, PASSWORD_RESET_REQUESTED, PASSWORD_RESET, USER_CREATED, USER_UPDATED, USER_DEACTIVATED, ROLE_CHANGED, ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED, REFRESH_TOKEN_REUSE_DETECTED, ACCOUNT_LOCKED, RATE_LIMIT_EXCEEDED |
| entity_type, entity_id | VARCHAR(60), BIGINT | |
| user_id, full_name | BIGINT, VARCHAR(200) | name snapshotted |
| success | BOOLEAN | |
| failure_reason | VARCHAR(255) | |
| ip_address, user_agent, request_id | | correlation id |
| created_at | TIMESTAMP(3) | |

Indexes: `idx_audit_user_date`, `idx_audit_action_date`, `idx_audit_entity`, `idx_audit_created`

**Never stores:** passwords, password hashes, raw refresh tokens, raw reset
tokens, JWT secrets.

---

## Rules that apply to every future table

1. Audit block on every table: `created_at`, `created_by`, `updated_at`, `updated_by`.
2. `deleted_at` + `deleted_by` on master tables. Posted financial documents are
   **reversed**, never soft-deleted.
3. `@Version` only where concurrent edits are real. Not on append-only ledgers.
4. `status VARCHAR(20)` + CHECK constraint. Never TINYINT, never a native
   PostgreSQL ENUM type.
5. Every FK declared in the database, not just in JPA.
6. Every unique business key declared as a DB UNIQUE KEY, not just validated in Java.


---

## Engine migration note (CR-014)

The schema was authored against MySQL 8 and migrated to PostgreSQL before any
deployment, so no data migration was required. What changed:

| MySQL | PostgreSQL |
|---|---|
| `BIGINT AUTO_INCREMENT` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` |
| `DATETIME(3)` | `TIMESTAMP(3)` |
| `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` | removed; database-level `UTF8` |
| `ON UPDATE CURRENT_TIMESTAMP(3)` | `set_updated_at()` trigger |
| `NOW(3)` | `CURRENT_TIMESTAMP` |
| `DATE_SUB(x, INTERVAL n DAY)` | `x - INTERVAL 'n days'` |
| implicit case-insensitive UNIQUE via `utf8mb4_0900_ai_ci` | explicit `UNIQUE INDEX ON (lower(col))` |
| `KEY idx_x (...)` inside CREATE TABLE | separate `CREATE INDEX` statements |

`registry/static_check.py` now fails the build if any MySQL syntax reappears in
an executable line of a migration. Comments may still reference MySQL for
historical context.

**`app_user` keeps its name.** `USER` is reserved in PostgreSQL as it was in
MySQL, so the original workaround remains correct.

---

## Module 2 tables (V2)

### `supplier`
Never hard-deleted: Module 8 purchase documents reference `supplier_id`
permanently.

Key columns beyond the standard audit block: `supplier_code` (unique,
auto-generated as `SUP-nnnn`), `supplier_name`, `mobile_no`, `gst_no`,
`pan_no`, address fields including `state_code`, `payment_terms_days`,
`credit_limit_paise`, bank details, `status`.

Database-level checks, not only Java validation:
- `ck_supplier_gst` - regex for a 15-character GSTIN
- `ck_supplier_pan` - regex for a 10-character PAN
- `ck_supplier_mobile` - `^[6-9][0-9]{9}$`
- `ck_supplier_pincode` - `^[1-9][0-9]{5}$`
- `ck_supplier_status` - ACTIVE / INACTIVE / BLOCKED
- `ck_supplier_credit` and `ck_supplier_terms` - non-negative

Functional unique indexes, the BUG-AUTH-009 lesson applied again:
`uk_supplier_name_lower` on `lower(supplier_name)` and `uk_supplier_gst_lower`
on `upper(gst_no)`, both partial on `deleted_at IS NULL`.

**Money is `BIGINT` paise**, never a floating point rupee value.

### `supplier_contact`
A supplier usually has several people. `uk_supplier_contact_primary` is a
partial unique index on `(supplier_id) WHERE is_primary = TRUE`, so at most one
primary contact per supplier is enforced by the database rather than by hoping
the service layer gets the order of operations right.

## Common table (V3)

### `activity_log`
Business record history, separate from `security_audit_log` (CR-015).
`old_values` and `new_values` are `jsonb` because the shape differs per module.
Only changed fields are stored, and a redaction list keeps passwords, tokens and
bank account numbers out of it entirely.

## Module 10 tables + tenant/customer additions (V10, CR-022)

### `tenant` — new columns
`gst_no`, `address_line1`, `address_line2`, `city`, `state_code`, `pincode`,
`signatory_name` — all nullable. Printed on every invoice/quotation PDF;
`state_code` also drives the CGST+SGST-vs-IGST split (compared against the
customer's own `state_code`, computed at PDF render time, never stored).

### `customer` — new columns
`address_line1`, `address_line2`, `city`, `state_code`, `pincode` — all
nullable, same shape as the equivalent supplier columns. `gst_no` already
existed (CR-021) but was write-only from nowhere until this CR wired
`InvoiceRequest`/`QuotationRequest` to set it via `CustomerLookupService`.

### `quotation`
Structurally identical to `invoice` minus every payment column — a price
document, not a financial record. `status` CHECK: `DRAFT`, `SENT`,
`ACCEPTED`, `REJECTED`, `EXPIRED`, `CONVERTED`. `EXPIRED` is never written by
the application — `Quotation.isExpired()` computes it from `valid_until` at
read time; only `CONVERTED` + `converted_invoice_id` are ever set by code,
by `POST /v1/quotations/{id}/convert`.

### `quotation_item`
Structurally identical to `invoice_item`. `unit_price_paise`/
`gst_rate_percent` are snapshots for display only — Convert-to-Invoice
re-reads the *current* product price rather than trusting them, since a
quotation is not a price lock.

## Module 5 proper (CR-023) — Customer, superseding CR-021's minimal table

`CustomerController`/`CustomerService` are new; the `customer` table itself
is unchanged (all columns already existed). `CUSTOMER_VIEW`/
`CUSTOMER_MANAGE` (seeded since CR-021) are now actually enforced on real
endpoints. No return/damage tracking - no such concept exists anywhere in
the schema, and CR-023 deliberately did not fabricate one.

## Image storage (V11, CR-023; V13, CR-026)

### `user_avatar`, `tenant_logo`, `tenant_signature`, `tenant_upi_qr`
Four 1:1 tables (owner id is the PK), each `content_type` + `file_size` +
`image_data BYTEA` + `updated_at`. Deliberately **not** columns on
`app_user`/`tenant` — both those tables are reloaded in full on every
authenticated request (`JwtAuthenticationFilter`, BUG-AUTH-001's
per-request-reload design), so an eager image column there would bloat
every request with image bytes regardless of whether the page needs it.
Each is queried only by its own dedicated endpoint
(`AvatarController`/`TenantImageController`), never joined into the auth
hot path. `ck_*_size` CHECK constraints cap every row at 2MB, matching
`ImageValidation.MAX_BYTES` on the application side. `tenant_upi_qr`
(V13, CR-026) holds a shop-uploaded UPI/GPay QR code image, printed on
the invoice PDF in place of the QR `InvoicePdfService` otherwise
generates from `tenant.upi_id` text.

## Notifications, subscription tier (V14/V15, CR-027)

### `notification_log`
Append-only audit trail, one row per outbound email/SMS/WhatsApp attempt -
`channel`, `recipient`, `subject` (nullable), `body`, `status`
(`SENT`/`LOGGED_ONLY`/`FAILED`), and an optional `related_entity_type`/
`related_entity_id` (e.g. `'INVOICE'`/`123`). `LOGGED_ONLY` is not a failure -
it means `NotificationServiceImpl` correctly logged the message instead of
sending it because no real SMS/WhatsApp provider is configured yet (email is
real, via the existing SMTP setup). Modelled on `activity_log`/
`security_audit_log`: plain `tenant_id` column, no `updated_at`.

### `tenant.subscription_tier`
`VARCHAR(10)` CHECK-constrained to `FREE`/`PRO`/`MAX`, default `FREE`. Gates
the Notification and AI Assistant features only - every feature that existed
before CR-027 stays available on every tier, so no existing tenant lost
anything it already relied on. Self-declared by the shop owner via
`PUT /v1/settings` - **no payment gateway exists**, so this is a
feature-gating flag, not a billed subscription; `SubscriptionService` is the
one sanctioned way a service checks it (mirrors `SecurityUtils`'s permission
checks).

## Project Management (V18/V19, CR-029)

Module 8. `work_type` - user-extensible (not a Java enum), tenant-scoped,
seeded with common examples per tenant but never restricted to them.
`project` - header row, `project_number` auto-generated `PRJ-nnnn` per
tenant; two-field lifecycle (`status` VARCHAR(20)+CHECK, `outcome`
VARCHAR(20) nullable, `ck_project_outcome_only_when_completed` CHECK
enforcing `outcome` can only be set once `status = 'COMPLETED'`) rather
than one flat enum - see the migration's own comment for the full
reasoning. `project_material` - one product line per project, `supplier_id`
deliberately nullable (old stock with no recorded supplier must still be
addable), `unit_price_paise`/`unit` snapshotted at add-time like
`invoice_item`. `project_expense` - a manual ledger today
(`category` IN LABOUR/EMPLOYEE/FOOD/STAY/PETROL/OTHER); LABOUR/EMPLOYEE
entries are typed in by hand until a future Labour/Team/Attendance module
can derive them from real attendance records instead - the project's
profitability math had to work correctly today, not only once every future
module ships. `project_payment` - amounts received against
`project.project_value_paise`, reusing `invoice.payment`'s exact
`payment_method` value set rather than duplicating it.

Profit is never stored - `ProjectServiceImpl` computes it fresh on every
read as `project_value_paise - (sum(project_material.total_cost_paise) +
sum(project_expense.amount_paise))`, and revenue is the agreed contract
value, not the sum of payments received (a project earns revenue as work
completes, not as cash arrives - `balance receivable` is the separate,
distinct "how much cash is still owed" figure).

New permissions: `PROJECT_VIEW`, `PROJECT_MANAGE`, `PROJECT_MATERIAL_VIEW`,
`PROJECT_MATERIAL_MANAGE`.

**BUG-PROJ-001** (fixed same round): `project_material`/`project_expense`/
`project_payment` don't extend `BaseEntity` (no JPA auditing), so their
plain `created_at`/`updated_at` columns need the service layer to set them
explicitly - see `BUG_REGISTRY.md` for the full incident; `V19` was needed
separately to add `created_by`/`updated_by` to `work_type`, which *does*
extend `BaseEntity` but whose `V18` migration forgot those two columns.

**Explicitly not in this round**: `labour_team`/`labour_employee`/
`attendance` (a separate later module), any cash/bank/cheque ledger table
(separate Finance module), report tables (reports are computed from
existing data, never stored).

## Supplier bank account encryption (V17, CR-018)

`supplier.bank_account_no` widened from `VARCHAR(30)` to `VARCHAR(255)` to
hold AES-256-GCM ciphertext (`ENC:` + base64(IV + ciphertext)) instead of the
raw account number - see `common/security/FieldEncryptor` and
`supplier/entity/BankAccountNumberConverter`. The database never holds
plaintext once a row has been through the app's `APP_ENCRYPTION_KEY`; the
column is encrypted at the application boundary, not by PostgreSQL. New
global permission `SUPPLIER_VIEW_BANK_ACCOUNT`, granted to OWNER only by
this migration's backfill. Full detail: CR-018 in
`CHANGE_REQUEST_REGISTRY.md`.

## Coupons (V16, CR-028)

### `coupon`
Tenant-scoped discount code, `UNIQUE (tenant_id, lower(code))`. `discount_type`
`VARCHAR(10)` CHECK IN (`PERCENT`,`FLAT`); `discount_value DECIMAL(10,2)` -
a percentage (0-100) or a flat rupee amount depending on `discount_type`;
`min_purchase_paise BIGINT` nullable (no minimum when null);
`max_discount_paise BIGINT` nullable (no cap when null, only meaningful for
`PERCENT`); `valid_from`/`valid_until DATE`, both nullable (open-ended when
null); `usage_limit INT` nullable (unlimited when null); `times_used INT NOT
NULL DEFAULT 0`, incremented by `CouponService.recordUsage()` only after an
invoice referencing the coupon is actually saved, never speculatively;
`status VARCHAR(20)` CHECK IN (`ACTIVE`,`INACTIVE`) + CHECK constraint,
following the project's status-column convention (never a bare boolean).

### `coupon_product`
Join table, `(coupon_id, product_id)` composite PK, both FK with `ON DELETE
CASCADE`. An empty set for a given `coupon_id` means the coupon applies to
every product in the cart (`Coupon.isRestrictedToProducts()` returns false);
a non-empty set restricts the discount to only those line items -
`CouponServiceImpl.calculateDiscount()` groups the invoice's line items by
`product_id` and computes the discount only across the eligible subset,
never the whole cart, when this table has rows for the coupon.

### `invoice.coupon_id`, `invoice.discount_paise`
Nullable `coupon_id BIGINT REFERENCES coupon` (no coupon applied is the
common case) and `discount_paise BIGINT NOT NULL DEFAULT 0`. The coupon
reference is kept even though `coupon.code` could theoretically change later
(coupons are never renamed in practice, but the FK is the correct modelling
choice over duplicating the code string) - `InvoiceMapper.toResponse()`
reads `invoice.getCoupon().getCode()` fresh at read time for
`InvoiceResponse.couponCode`.

### `quotation.coupon_id`, `quotation.discount_paise`
Same two columns added to `quotation` for schema symmetry with `invoice`,
but **not yet wired into `QuotationServiceImpl`/`QuotationRequest`/
`QuotationResponse`** - a quotation cannot actually redeem a coupon today,
the columns exist so a future CR doesn't need another migration. Deliberately
deferred: the user's CR-028 request asked for coupon redemption "for the
products" at the point of sale, which in this codebase is the Invoice flow;
Quotation is a non-binding price document, not a sale, so coupon redemption
there is lower priority and was left for its own follow-up rather than
built speculatively.

### New permissions
`COUPON_VIEW`/`COUPON_MANAGE`, global rows in `permission` (permissions are
never tenant-scoped, only the grants in `role_permission` are - see CR-016).
Backfilled to every existing tenant's OWNER (both) and MANAGER (both) roles,
and view-only to ACCOUNTANT/STAFF, in the same `V16` migration - a coupon
feature that shipped ungranted to every already-provisioned tenant would be
invisible until an OWNER manually re-granted it, which is exactly the kind
of silent gap CR-016's tenant-scoping discipline exists to prevent.

## Subscription coupons (V20, CR-032)

### `subscription_coupon`
Distinct from `coupon` above (CR-028, discounts a customer's invoice) - this
is the shop's *own* subscription plan being granted for free, redeemed by
the OWNER for their own tenant. Tenant-scoped, `UNIQUE (tenant_id,
upper(code))`, same shape family as `coupon`: `granted_tier VARCHAR(10)`
CHECK IN (`FREE`,`PRO`,`MAX`); `trial_days INT NOT NULL` CHECK `> 0` - how
long the granted tier lasts from the moment of redemption, not from
`valid_from`/`valid_until` (those gate when the *code itself* can be used,
same distinction `coupon` already makes); `valid_from`/`valid_until DATE`
nullable; `usage_limit INT` nullable (unlimited when null); `times_used INT
NOT NULL DEFAULT 0`; `status VARCHAR(20)` CHECK IN (`ACTIVE`,`INACTIVE`).

### `tenant.subscription_trial_expires_at`
Nullable `TIMESTAMP(3)`. Null means `tenant.subscription_tier` (whatever it
is) is permanent - exactly CR-027's original self-serve-picker behaviour,
untouched. Set only by `SubscriptionCouponServiceImpl.redeem()`, to
`now() + trialDays`. Checked lazily by `SubscriptionServiceImpl
.currentTier()` on every call (feature gates, entitlement checks) - once
passed, the tier reverts to `FREE` and this column is cleared right there,
no scheduled job. `currentTier()` runs in its own `REQUIRES_NEW`
transaction specifically so that revert-write always flushes even when
called from inside a caller that's `readOnly = true` (a real bug caught
and fixed during design, not after - see CR-032's own entry in
`CHANGE_REQUEST_REGISTRY.md`). Manually picking a tier from the existing
dropdown in Shop Settings always clears this column
(`TenantSettingsServiceImpl.update()`), so an owner's explicit choice can
never be silently overridden by a stale trial later.

### Permissions
No new permission codes - reuses `SETTINGS_MANAGE` throughout (create,
update, delete, redeem), which is already OWNER-only in practice (MANAGER
holds `SETTINGS_VIEW` but not `SETTINGS_MANAGE` - see
`TenantRegistrationServiceImpl`), matching how sensitive plan/billing
management should be scoped.

## CR-035: Purchase + Supplier Bill Import (V21)

`purchase` (purchase_number, supplier_id, supplier_bill_number nullable,
purchase_date, subtotal/gst/total/paid/balance_paise, status, imported_at/
imported_by nullable - null means a manually-entered purchase), `purchase_item`
(price/GST snapshotted at purchase time, same pattern as invoice_item),
`purchase_payment` (mirrors `payment` exactly, reuses the existing
`PaymentMethod` enum rather than duplicating it), `purchase_document`
(the raw uploaded bill file - bytea, same pattern as tenant_logo/
tenant_signature/user_avatar, 20MB cap via CHECK constraint, one row per
purchase, only ever written by the import confirm path).

`stock_movement.movement_type`'s CHECK constraint widened to add
`PURCHASE_RECEIPT` (stock arriving) and `PURCHASE_RETURN` (the paired
reversal on cancel, mirroring the existing SALE/SALE_REVERSAL pair) -
receiving/cancelling a purchase both go through `StockService
.applyMovement()`, never a direct `quantity_on_hand` mutation.

No new permission codes - `PURCHASE_VIEW`/`PURCHASE_MANAGE` already
existed in the `permission` table since `V1`, seeded speculatively for
this module before it existed, and were already granted to OWNER/MANAGER/
ACCOUNTANT in the default per-tenant role templates.

## CR-036: multi-bank-account invoice payments + product image/import (V22, V23)

`tenant_bank_account` (one of possibly several accounts a shop can receive
payment into - label, bank_name, account_holder_name, account_number
encrypted via the reused `BankAccountNumberConverter` from CR-018,
ifsc_code, upi_id, `default_account` boolean, status). `tenant_bank_account_qr`
(an owner-labelled QR image per account - GPay/PhonePe/Bank app - bytea,
FK `ON DELETE CASCADE` from its account). `invoice.bank_account_id`/
`bank_account_qr_id` - both nullable, both live-resolved at PDF-render
time (never snapshotted, same as the pre-existing single-account payment
block always reading the tenant's *current* bank fields), `ON DELETE
SET NULL` so removing an account never breaks an already-issued invoice.
The pre-existing single-account fields on `tenant`
(bank_account_name/bank_account_no/bank_ifsc/bank_name/upi_id) and
`tenant_upi_qr` are untouched and remain the fallback whenever an invoice
has no account explicitly selected.

`product_image` - 1:1 image table (product_id PK), same pattern as
`user_avatar`/`tenant_logo`, `FK ... ON DELETE CASCADE` from `product`.
Deliberately its own table, never joined into the product list/search
read path.

No new tables for the bulk product-import feature - it reuses
`ProductService.create()` per row inside one transaction, same as every
other product-creation path.

No new permission codes - bank accounts reuse `SETTINGS_VIEW`/
`SETTINGS_MANAGE` (a shop's own accounts are shop settings, not a new
sensitivity class the way Supplier's third-party bank details are);
product image/import reuse `PRODUCT_VIEW`/`PRODUCT_MANAGE`.

## CR-036 phase 3: standalone expense ledger (V24)

`expense_category` - plain user-extensible table, same reasoning as
`work_type` (CR-029): a shop's own vocabulary for what it spends money on
is never restricted to what shipped in the seed. `business_expense`
(expense_date, category_id FK required, amount_paise, payment_method -
reuses the existing `PaymentMethod` enum rather than duplicating it,
same pattern Purchase already established for its own payments - notes,
status ACTIVE/CANCELLED). Deliberately a **separate, standalone ledger**
from `project_expense` (CR-029), which stays exactly as it was, scoped to
individual projects - the two were never meant to be the same table.
`expense_receipt` - optional 1:1 receipt photo per expense, same bytea
pattern as `product_image`/`user_avatar`.

No new permission codes - `EXPENSE_VIEW`/`EXPENSE_MANAGE` already existed
in the permission catalogue since `V1`, seeded speculatively for this
module before it existed (confirmed already granted to the OWNER/MANAGER/
ACCOUNTANT default role templates), exactly like `PURCHASE_VIEW`/
`PURCHASE_MANAGE` was before Purchase existed.

## CR-036 phase 4: Labour Monitor (V25)

`worker` - a shop's own day-wage labour force, deliberately separate from
Supplier/Customer: name, mobile_no, role_title (plain free text, not a
lookup table like `work_type` - a worker's skill label doesn't need the
same admin-editable-catalogue treatment Category/Brand/WorkType get),
daily_rate_paise, status ACTIVE/INACTIVE (soft-deleted like Supplier/
Customer - attendance and payment history reference a worker forever).

`worker_attendance` - one row per worker per day: attendance_date, status
PRESENT/ABSENT/HALF_DAY, an optional `project_id` FK (`ON DELETE SET
NULL`) for cost attribution, notes. `UNIQUE (tenant_id, worker_id,
attendance_date)` - marking the same worker/day again corrects the
existing row in place rather than creating a duplicate. Wage is **never
stored** - it is computed live everywhere it's read as
`daily_rate_paise x ratio` (1 / 0.5 / 0 for PRESENT/HALF_DAY/ABSENT),
using the worker's *current* rate, so a later rate correction is
reflected in every past summary instead of silently going stale
(`AttendanceStatus.wagePaiseFor()`, `WorkerAttendanceRepository`'s
`sumWagePaiseByWorker`/`sumWagePaiseByProject` JPQL `CASE WHEN` queries).

`worker_payment` - a payment made to a worker against wages earned;
mirrors `project_payment`'s shape exactly (amount_paise, payment_date,
payment_method reusing the existing `PaymentMethod` enum, notes).

**Project integration**: `ProjectResponse` gained a new
`totalLabourCostDisplay` field, computed live from
`worker_attendance` x each worker's current rate for that project.
Deliberately **additive only** - never folded into the existing
`totalCostDisplay`/`netProfitDisplay` figures (which stay computed from
`project_material` + `project_expense` exactly as before Labour Monitor
existed), so this addition never silently changes a profit number an
owner already relies on. Same "standalone, not merged" principle already
used for the Expense Tracker vs `project_expense` (CR-036 phase 3).

**New permission codes** - unlike every other phase of CR-036, no
`LABOUR_*` code was speculatively pre-seeded in `V1` (confirmed via grep
before building), so this is the first phase that genuinely adds new
`permission` rows: `LABOUR_VIEW`, `LABOUR_MANAGE` (module_code `LABOUR`).
Granted to OWNER, MANAGER and ACCOUNTANT (payroll is financial data, same
tier as `PAYMENT_*`/`EXPENSE_*`); deliberately withheld from STAFF, same
reasoning that excludes `PRODUCT_VIEW_COST` from STAFF (a worker's daily
rate is cost-like data). See BUG-LAB-001 for a real defect this
surfaced: **OWNER's grant in `V1` is a one-time `CROSS JOIN`, not a
standing rule** - it only covers permission codes that existed at V1's
own seed time. Every phase since then that adds a genuinely new
permission code (this one, and originally V18's Project module) must
explicitly grant OWNER again in that same migration, or OWNER silently
does not get the new permission.

## CR-041: per-tenant document number allocator (V29, seed V904)

`document_sequence` - one row per `(tenant_id, doc_type)` holding
`next_value`, the next number to issue. `UNIQUE (tenant_id, doc_type)`,
`CHECK` on the nine document types (`CUSTOMER`, `SUPPLIER`, `PRODUCT`,
`CATEGORY`, `BRAND`, `INVOICE`, `QUOTATION`, `PURCHASE`, `PROJECT`), FK to
`tenant`, indexed on `tenant_id`, standard `BaseEntity` audit columns.

Replaces the `MAX(existing) + 1` allocation that every one of those nine
code generators used. That read-then-write had no lock: two concurrent
callers read the same MAX and both attempted the same number. The
`UNIQUE (tenant_id, <code>)` constraint already on `invoice`, `quotation`,
`purchase`, `customer`, `supplier`, `product`, `category`, `brand` and
`project` meant **no duplicate was ever stored** - but the losing request
died on a constraint violation and its document was lost.

Allocation takes `SELECT ... FOR UPDATE` on the row and **joins the
caller's transaction** (`Propagation.MANDATORY`), deliberately not
`REQUIRES_NEW`: a rolled-back invoice must not consume a number, because
GST requires a consecutive serial with no gaps. Callers must allocate
before taking any other row lock (stock, coupon) so lock ordering stays
consistent and the system stays deadlock-free.

`V29` backfills `next_value` from `MAX(existing) + 1` per tenant, using the
same `^PREFIX-[0-9]+$` regex guard the old repository queries used, so a
hand-typed code that never belonged to the sequence cannot strand the
counter.

**`V904__seed_document_sequence.sql` (db/seed, dev and test profiles only).**
Flyway runs `V29` *before* `V900`-`V903`, so in dev and test the backfill
reads empty tables, seeds every counter at 1, and the seed then inserts
`SUP-0001`..`SUP-0013` and friends on top of it - the next generated code
would collide. `V904` re-syncs every counter after the seed with
`ON CONFLICT ... DO UPDATE SET next_value = GREATEST(...)`. Production is
unaffected; the `V9xx` seed never runs there. Caught by
`DocumentSequenceServiceIT.backfillContinuesExistingRun`, not by review.

No new permission codes - allocation is internal infrastructure, never an
endpoint.
