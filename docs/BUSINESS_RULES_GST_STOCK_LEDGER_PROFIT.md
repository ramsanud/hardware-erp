# Critical business logic: GST split, stock cost, customer ledger, profit (CR-091)

CR-091 turned four rules that used to live in comments and conventions into
columns, tables and one code path each. Every rule below names the code that
enforces it and the test that proves it against real PostgreSQL.

## 1. GST split - CGST/SGST vs IGST

One utility decides, everywhere: `common/util/GstSplit.java`.

| Question | Rule | Test |
|---|---|---|
| Where is the place of supply? | Customer's state code if given; else the first two digits of the customer's GSTIN; else the shop's own state (a walk-in sale is intra-state) | `GstSplitTest` |
| Intra or inter? | Place of supply = shop's state → INTRA (CGST + SGST, half each); otherwise INTER (IGST, whole) | `GstSplitTest` |
| Rounding | Whole-rupee-safe paise arithmetic: SGST = GST − CGST, so the two halves always sum exactly to the line GST; never two independent HALF_UP roundings | `GstSplitTest.oddPaisaGoesToSgst` |

Stored per invoice (`invoice.supply_type`, `place_of_supply_state_code`,
`cgst_paise`, `sgst_paise`, `igst_paise`) and per line (`invoice_item.cgst_paise`,
`sgst_paise`, `igst_paise`), written by `InvoiceServiceImpl.applyGstSplitAndCost()`
on create and on amend. V62 backfilled every existing invoice using the same
precedence. GSTR-1 (CR-087) already used this precedence; the invoice now
carries the answer instead of recomputing it at report time.

## 2. Stock cost - weighted average, frozen at sale

| Rule | Where | Test |
|---|---|---|
| Every purchase receipt moves the product's weighted-average cost: `(oldQty×oldAvg + recvQty×unitCost) / (oldQty+recvQty)`, HALF_UP to the paise | `StockServiceImpl.applyPurchaseReceipt()` (new interface method - `applyMovement()` untouched, per the "add a capability, don't change every caller" rule) | `PurchaseServiceImplTest.createIncreasesStockThroughStockService` |
| A purchase return (negative quantity) leaves the average alone | same - only `quantityChange.signum() > 0` updates it | - |
| At the moment of sale each invoice line freezes `cost_price_paise` = the stock row's average cost then (falling back to `product.purchase_price_paise` when no receipt has ever set one) | `InvoiceServiceImpl.applyGstSplitAndCost()` | `ProfitHistoricalCostIT` |
| Changing a product's purchase price later never changes a profit already booked | COGS reads `invoice_item.cost_price_paise`, never the product row | `ProfitHistoricalCostIT.laterPriceChangeDoesNotAffectAlreadySoldUnitsCogs` |

Stored on `stock.average_cost_paise` (V62 backfilled from
`product.purchase_price_paise`) and `invoice_item.cost_price_paise`.

Not built, deliberately: "Available = Physical − Reserved − Pending
Allocation". Nothing in this system reserves stock - a Sales Order does not
hold units - so the only honest available figure is `stock.quantity_on_hand`.
Inventing a reservation number to subtract would be drawing data that does
not exist (CLAUDE.md rule 12).

## 3. Invoice cancellation

| Rule | Where | Test |
|---|---|---|
| A reason is mandatory; blank is refused | `InvoiceCancelRequest(@NotBlank reason)`; `InvoiceServiceImpl.cancel()` refuses a blank reason too | `InvoiceServiceImplTest` |
| Stock is restored per line with a SALE_REVERSAL movement (unchanged from CR-021) | `InvoiceServiceImpl.cancel()` | `InvoiceServiceImplTest` |
| The customer ledger is reversed by an INVOICE_CANCELLATION credit for the invoice total | `CustomerLedgerService.postInvoiceCancellation()` | `CustomerLedgerServiceImpl` idempotent post |
| Who, when and why are kept on the invoice | `invoice.cancelled_at`, `cancelled_by`, `cancellation_reason`, shown on the detail page | - |

The frontend's cancel confirmation now collects the reason
(`InvoiceDetailPage`); the old plain confirm would be refused by the server.

## 4. Customer ledger - the one source of truth for outstanding

`customer_ledger_entry` is append-only. There is no `customer.balance`
column; the balance is `SUM(debit) − SUM(credit)` over the rows, positive
meaning the customer owes the shop.

| Event | Entry | Posted by |
|---|---|---|
| Invoice created | INVOICE debit, invoice total | `InvoiceServiceImpl.create()` |
| Initial or later payment | PAYMENT credit | `InvoiceServiceImpl.create()` / `addPayment()` |
| Credit note (sales return) | SALES_RETURN credit, credit-note total | `CreditNoteServiceImpl.create()` |
| Invoice cancelled | INVOICE_CANCELLATION credit, invoice total | `InvoiceServiceImpl.cancel()` |
| Unpaid invoice amended | the INVOICE debit follows the new total (same document, not a correction row) | `CustomerLedgerService.amendInvoice()` |
| Manual correction | ADJUSTMENT debit or credit, reason mandatory, PAYMENT_MANAGE, written to `activity_log` | `POST /v1/customers/{id}/ledger/adjust` |

Idempotent by construction: `UNIQUE (tenant_id, entry_type, reference_type,
reference_id)` plus an `existsBy…` check before every insert, so the same
payment can never be posted twice. V62 backfilled the ledger from every
existing invoice, payment, credit note and cancelled invoice.

The brief's worked example - ₹10,000 invoice, ₹3,000 paid → ₹7,000; ₹2,000
more → ₹5,000; ₹1,000 returned → ₹4,000 - is `CustomerLedgerIT
.ledgerFollowsTheBriefsWorkedExample`, end to end over HTTP.

Ageing (`GET …/ledger/ageing`) buckets open invoice balances 0-30 / 31-60 /
61-90 / 90+ days from the invoice date. Invoices carry no separate due date,
so age is counted from the date of sale - the response says so.

## 5. Profit

`GET /v1/analytics/profit?from&to` (REPORT_FINANCIAL, same gate as the Tally
export - margin is owner/accountant information):

```
net revenue  = invoice revenue − credit-note returns
net COGS     = Σ invoice_item.cost_price_paise × (qty + free qty)
             − Σ credit_note_item cost of the returned units
gross profit = net revenue − net COGS
net profit   = gross profit − business_expense in the period
```

Every term is a recorded figure; nothing is estimated from today's prices.
Frontend: **Accounting → Profit & loss**.

## 6. Two bugs the integration tests found

Both were invisible to the mocked unit tests and both are the same species
this codebase already named BUG-BE-002 (a `@Transactional` boundary that is
not where it looks like it is):

1. **Offline sync returned 500 for any conflicting row.**
   `InvoiceServiceImpl.create()` is plain `@Transactional`; called from the
   sync executor's own REQUIRES_NEW transaction it joined it, and when it
   threw INSUFFICIENT_STOCK it marked that shared transaction rollback-only
   before the catch block ran. Catching the exception could not undo that;
   the CONFLICT row was saved and then discarded at commit with
   `UnexpectedRollbackException`. Fix: the attempt runs in
   `SyncInvoiceCreator`'s own REQUIRES_NEW transaction, the executor holds
   none. Found by `OfflineSyncIT.insufficientStockAtSyncTimeIsAConflictNotASilentRetry`.

2. **Every automatic customer notification was failing silently (CR-088 regression).**
   `UsageTrackingServiceImpl.tryConsume(tenantId, key)` delegated to the
   three-arg overload with `this.tryConsume(...)` - a self-invocation that
   bypasses the proxy, so its REQUIRES_NEW never opened and the modifying
   query threw `TransactionRequiredException` from inside `@Async
   notifyInvoiceCreated`, before `provider.send()` was ever reached. Fix:
   the two-arg overload is annotated too. Seen as "Async method
   notifyInvoiceCreated failed" on every invoice creation in the IT logs;
   gone after the fix.

## Files

Backend: `common/util/GstSplit`, `customer/ledger/*`, `sync/*`,
`invoice/dto/InvoiceCancelRequest`, changes in `InvoiceServiceImpl`,
`CreditNoteServiceImpl`, `StockService(Impl)`, `PurchaseServiceImpl`,
`PurchaseImportServiceImpl`, `AnalyticsRepository/Service/Controller`,
migration `V62__gst_split_ledger_cost_sync.sql`.
Frontend: `modules/customer/components/CustomerLedgerPanel`,
`modules/report/pages/ProfitReportPage`, `modules/sync/*`, invoice
detail/create pages. Offline sync itself: `OFFLINE_SYNC.md`.
