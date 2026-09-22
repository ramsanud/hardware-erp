# Premium growth pack: multi-branch, smart insights, daily summary, backups (CR-092)

Four PREMIUM features, each gated on its `FeatureKey` inside the service
(so an internal caller is refused exactly like an HTTP one), each proven
against real PostgreSQL. Migration V63.

## 1. Multi-branch (`MULTI_BRANCH`)

### The model, and the one deliberate limit

| Rule | Where |
|---|---|
| Every shop has exactly one MAIN branch. V63 created it for every existing tenant; an `AFTER INSERT ON tenant` trigger creates it for every new one. `UNIQUE INDEX … WHERE is_main` | V63 |
| Every invoice, purchase and stock movement carries a `branch_id`, set **server-side** from the acting user (`app_user.branch_id`, else MAIN) - never from a request body, for the same reason a tenant id never is | `BranchContext.actingBranchId()`; `InvoiceServiceImpl`, `PurchaseServiceImpl`, `PurchaseImportServiceImpl`, `StockServiceImpl` |
| A row inserted without a branch belongs to MAIN | `BEFORE INSERT` trigger `branch_default_main()` on the three tables - covers the dev/test seed (V9xx, never edited - rule 1) and direct SQL |
| **Stock availability is still the shop's `stock.quantity_on_hand`.** `branch_stock` is a measured breakdown of it, written by the same code and always summing to it | `StockServiceImpl.recordBranchDelta()` on every movement |
| A branch's first row for a product starts from the shop's figure while the shop is single-branch (that stock is, by definition, all at MAIN), and from zero once there are several | `recordBranchDelta()`; `BranchStockRepository.snapshotMainFromShopStock()` runs once, when the second branch is created |
| A branch figure can go negative - goods sold from a branch that never received them by purchase or transfer. Shown in red with the reason, never hidden or "corrected" (rule 12) | `BranchesPage` → Stock by branch |
| A transfer is a document (`stock_transfer` + items, `ST-000001` numbering) that writes one STOCK_TRANSFER_OUT and one STOCK_TRANSFER_IN movement per line, moves the breakdown, and leaves the shop total untouched. It is the one place `branch_stock` **is** enforced: you cannot transfer what the source branch does not hold | `StockService.applyBranchTransfer()` (new method; `applyMovement()` untouched) |
| Branch-wise figures are counts and sums over rows with that `branch_id`, cancelled documents excluded | `BranchSummaryRepository.summary()` |

Why availability is not re-keyed by branch in this cut: every stock check
in the system (invoice, credit note, delivery, project material, substitute,
discovery) reads the tenant row. Moving that authority to `branch_stock`
would touch all of them and change behaviour for every single-branch shop
for no benefit. The breakdown gives a multi-branch shop the truth about
where its goods are; the transfer gives it the tool to make that truth
match the floor. Recorded as the natural follow-up CR.

Permissions: `BRANCH_VIEW` (every role - the name is printed on documents),
`STOCK_TRANSFER_MANAGE` (owner, manager), `BRANCH_MANAGE` (owner: create,
edit, assign users). Proven by `BranchStockTransferIT` (3): MAIN exists and a
Basic shop cannot add a second; a transfer moves 12 of 20, refuses 9 more
than the source holds, a purchase lands at MAIN and the breakdown still sums;
a sale is stamped with the seller's branch and deducted from it.

## 2. Smart insights (`SMART_INSIGHTS`, `REPORT_VIEW`)

Six read-only views under `/v1/insights/*`, every figure a count, sum or
ratio over rows the shop recorded in the window. Where a rate would divide
by zero the field is null and the row says so.

| Insight | Rule |
|---|---|
| Slow-moving | in stock, sold nothing in the window; sorted by value at cost |
| Overstock | days of cover = on hand ÷ average daily sales, above the threshold (default 120) |
| Reorder | at/below `product.reorder_level`, or days of cover ≤ lead time (default 7); suggested = lead time × daily rate + reorder level − on hand |
| Demand trend | quantity this window vs the previous window of the same length; rising and falling |
| Bought together | product pairs on ≥ 2 of the same invoices; support = together ÷ invoices with A |
| Pricing (`PRODUCT_VIEW_COST` too) | below cost, under 10% margin, or realised price ≥ 10% under list |

An empty window returns an empty list and a summary sentence saying why.
`InsightsIT` (2) records four products and two sales and checks each view
reports exactly those; a Basic shop gets `FEATURE_NOT_AVAILABLE`.

## 3. Daily business summary

`DailyBusinessSummaryJob` at 20:30 IST (configurable
`app.daily-summary.cron`), one tenant per REQUIRES_NEW transaction through
`DailyBusinessSummaryService` (a separate bean - BUG-BE-002). For every
active shop: the day's invoices and sales, payments received, purchases,
total outstanding, products at/below minimum stock - written as an in-app
`owner_notification` (DAILY_SUMMARY). With `ADVANCED_NOTIFICATIONS` it is
also sent to the owner on WhatsApp (if connected) else email, through
`NotificationService.sendOwnerMessage()` - the same metered `attempt()` as
every customer send, so it counts against the plan. `GET
/v1/daily-summary/today` previews it; `POST /v1/daily-summary/send`
(SETTINGS_MANAGE) sends it now. `TenantBackupIT.dailySummaryReflectsTheDay`.

## 4. Backups (`BACKUP_MANAGE`; nightly needs `AUTO_BACKUP`)

`tenant_backup` keeps one row per backup **with the snapshot bytes**, so a
past backup can be downloaded again. The snapshot is the CR-057 tenant
export (`TenantDataExportService.buildSnapshot()` - a new method, so the
platform-admin export path is unchanged): products, customers, suppliers,
invoices, quotations, purchases, expenses, workers, as JSON or a CSV zip.

- `POST /v1/backups?format=` - on demand, any plan with `DATA_EXPORT`,
  activity-logged.
- `GET /v1/backups`, `GET /v1/backups/{id}/download` - this shop's only;
  another shop's id is 404.
- `TenantBackupJob` at 01:30 IST: Premium shops only, then prunes each
  shop to its newest **7**.

`TenantBackupIT` (3): a manual backup contains this shop's rows and not
another's, and the other shop cannot download it; nine scheduled runs leave
seven rows for a Premium shop and none for a Basic one.

Frontend: **Settings → Shop → Backups & daily summary** card (owner-only),
**Inventory → Branches**, **Accounting → Smart insights**.

## Pending, by scope

- Branch-keyed availability (see §1) - its own CR.
- Assigning a user to a branch has an API (`PUT /v1/branches/users/{id}`)
  but no control on the Users page yet; the owner uses the API or a later
  UI pass. Every user's documents land on MAIN until then.
- Transfers are immediate (COMPLETED on creation); no in-transit state.
- The daily summary goes to the owner's shop phone/email (`tenant.phone` /
  `tenant.email`); per-user recipients are not configurable.
- Backups are JSON/CSV data exports; uploaded images and documents are not
  included (they are not in the CR-057 export either).
- Backup files are stored in the database (`BYTEA`), capped by the 7-row
  prune; object storage is the natural next step if snapshots grow.
