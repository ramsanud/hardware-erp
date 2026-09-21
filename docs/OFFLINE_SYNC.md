# Offline sync (CR-091 Phase 9)

Scope, exactly as the brief recommended for a first cut: **invoices only**.
A sale raised while the server cannot be reached is kept on the device and
sent later. Nothing else works offline - the product list, prices and
stock the wizard shows still come from the last successful server call,
and the wizard is not a cached PWA. (The phone build's own scope - no PWA,
no offline app - is unchanged; this is the web app's invoice outbox.)

## How it works

```
InvoiceCreatePage
  navigator.onLine false, or create() fails with NETWORK_ERROR / TIMEOUT
     → outbox.queueInvoice(request)          IndexedDB "hardware-erp-outbox"
     → navigate to /sync                     status PENDING

OutboxAutoSync (mounted in AppLayout)        on the browser's "online" event, or on load
  → syncOutbox()
     POST /v1/sync/transactions { transactions: [ {clientUuid, deviceId, INVOICE, clientCreatedAt, payload} ] }
     ← { results: [ {clientUuid, status, replay, resultReferenceId, resultReferenceNumber, conflictReason} ] }
     → outbox.applyResult() per row          SYNCED / CONFLICT / FAILED
```

Server side, `SyncTransactionServiceImpl` walks the batch and hands each
row to `SyncTransactionExecutor.processOne()`, which replays it through the
real `InvoiceService.create()` - so an offline sale gets exactly the same
stock check, GST split, cost freeze, customer-ledger entry and numbering as
one entered online. No logic is duplicated for the offline path.

## The guarantees

| Guarantee | How |
|---|---|
| Re-sending never duplicates | `sync_transaction UNIQUE (tenant_id, client_uuid)`; a UUID already seen returns the stored result with `replay: true`. A race between two uploads of the same UUID is resolved by the constraint, and the loser returns the winner's row. `OfflineSyncIT.duplicateUploadIsANoOp` checks one invoice, one stock movement, one ledger row, stock decremented once |
| One bad row never blocks the rest | each row's invoice attempt runs in its own REQUIRES_NEW transaction (`SyncInvoiceCreator`), and its outcome row is written in a separate one - see the bug note in `BUSINESS_RULES_GST_STOCK_LEDGER_PROFIT.md` §6 |
| Server data is never silently overwritten | the payload carries no prices - `InvoiceService.create()` has never accepted one - so there is nothing stale to overwrite. What CAN conflict is stock a walk-in customer took meanwhile: that surfaces as the server's own INSUFFICIENT_STOCK and is recorded as CONFLICT, shown to the owner, never retried silently (`OfflineSyncIT.insufficientStockAtSyncTimeIsAConflictNotASilentRetry`) |
| Only an answer from the server moves a row out of the queue | a network failure during upload leaves rows PENDING; a 4xx/5xx marks them FAILED with the message; the Sync page offers Retry (FAILED) and Discard (CONFLICT/FAILED). Discard removes the row from the device only |
| Tenant and authority | the endpoint is gated on INVOICE_CREATE and takes no tenant id - the tenant is the caller's, from the JWT. Syncing still needs a live login; the outbox holds payloads, never a token (rule 9) |
| Audit | every attempt is a `sync_transaction` row: device, client time, received time, status, result reference or conflict reason, attempt count |

## What is stored on the device

IndexedDB database `hardware-erp-outbox`, store `transactions`, keyed by
`clientUuid`: the `InvoiceRequest` exactly as the wizard would have posted
it, the device id, client timestamp, status and the last result. The device
id is a random UUID in `localStorage` (`erp.deviceId`) - an identifier, not a
credential.

## Not built (pending, by scope)

- Offline product/customer lookup or a cached wizard - the brief's recommended
  first scope was the transaction queue, and a cached catalogue is a PWA
  concern the mobile brief explicitly excluded.
- Payments, credit notes or purchases offline - `SyncTransactionType` has one
  value, INVOICE, and the executor handles only it. Adding a type means a
  new enum value and a branch in `processOne()`; the queue, idempotency and
  page need no change.
- Background sync while the tab is closed - there is no service worker; sync
  runs when the app is open and comes online.

## Tests

`OfflineSyncIT` (real PostgreSQL, over HTTP): duplicate upload is a no-op at
every layer; insufficient stock at sync time is a CONFLICT with no invoice
created. Frontend: tsc, Vite build and the Playwright suite (272/272) pass
with the sync page and outbox wired in; the queue itself is exercised
manually - there is no Playwright spec that drops the network yet.
