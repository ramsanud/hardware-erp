# Important business rules — and how work is scoped

Load this when: handling a bug report, a feature request, or a one-line
ask. It defines what "done" means here.

## Proactive scope — think beyond the literal ask (CR-037)

For every requirement, think through the complete real-world workflow across
the roles it touches — Owner, Salesperson, Purchase Staff, Inventory Manager,
Warehouse Staff, Accountant, Auditor, Customer, Supplier, Labour — **before**
writing code. A single reported symptom is a signal to check the surrounding
module for the same class of defect (BUG-FE-007 was found in Expense and
confirmed in Product on inspection).

- **Always investigate and report** every adjacent gap, missing role
  workflow or edge case noticed — even outside today's ask. State it in the
  response whether or not it was fixed.
- **Always fix same-root-cause defects** found this way, in the same commit.
- **Never silently build a large new subsystem** (OCR pipeline, fuzzy
  search, e-signatures, optimistic locking, a lifecycle engine) to cover a
  gap just noticed. Record it, propose it as a CR, wait for approval.
- If the ask draws something the product cannot honestly do (a shop
  switcher where one login is one shop; a sparkline with no series), say so,
  do the honest version, and state the deviation.

## Business invariants

- **Money is `BIGINT` paise**; the server formats every display string
  (Indian grouping, `1,50,000`). The frontend never formats money itself.
- **Financial records reference users and suppliers forever** — both are
  soft-deleted, never hard-deleted.
- **Invoices are cancelled, never deleted**, so numbering stays unbroken for
  GST. Document numbers come from `document_sequence` under row lock.
- **A quotation converts to an invoice**; nothing is retyped. "Pending
  estimates" means `status=SENT`.
- **Stock moves only through sales, purchases and adjustments**; a product
  with no stock row yet is normal (BUG-BE-003 was a 500 on exactly that).
- **The owner creates every account** — no self-registration (CR-008).
  Roles are rows the owner edits; never hardcode a role code in logic.
- **Credentials for platform-wide channels (Twilio, SendGrid) are app-level;
  WhatsApp is per tenant** because it must send from the shop's own number
  (CR-056 vs CR-074).

## Bug handling — scope the fix correctly

First determine which layer is broken, then fix only that layer.

```
Screen looks wrong, but the API returned correct JSON   -> FRONTEND ONLY
API returns wrong data / wrong status / 500             -> BACKEND ONLY
API is correct but the frontend cannot consume it       -> FRONTEND ONLY (unless the contract is wrong)
The contract itself is wrong (field name, type, shape)  -> BOTH
Data is wrong in the database                           -> BACKEND + migration
```

Confirm with the API (Postman or a direct call) before deciding. If the API
is right and the screen is wrong, the backend is innocent.

**FRONTEND ONLY** — touch `frontend/` only. Verify: typecheck + build + suite.
**BACKEND ONLY** — touch `backend/` only. Verify: `mvn clean verify`. Add a
regression test in the same commit; a bug fixed without a test returns.
**BOTH** — only when the contract is wrong. Backend first, verify, then
frontend against the now-correct API. Never both at once.

Begin every bug response with one line:
```
SCOPE: FRONTEND ONLY   — <one-line reason>
SCOPE: BACKEND ONLY    — <one-line reason>
SCOPE: BOTH            — <one-line reason>
```
If unsure, say so and ask for the API response body rather than guessing.

**Always, afterwards:** add the `BUG_REGISTRY.md` entry (ID, severity,
layer, root cause, fix, regression test) **with both its index row and its
body** — BUG-FE-036 shipped with a row and no body; add the regression
test; update `RESUME_POINT.md`; report `static_check.py` as not executed.

## Specification change — new field, endpoint or rule

In this order, never skipping a step:

1. `project-knowledge/CHANGE_REQUEST_REGISTRY.md` — claim it as CR-nnn
   **first** (index row + body; grep for the number before claiming it).
2. New Flyway migration `V{n}__description.sql` — never edit an applied one.
3. Entity → repository → DTO → mapper → service → controller.
4. Backend tests.
5. `frontend/src/modules/{module}/types/index.ts` — mirror the DTO exactly.
6. Frontend validation schema — mirror the Bean Validation rules.
7. Service, form, page.
8. Postman collection.
9. All affected registries, then `RESUME_POINT.md`.

## Design work

Auth pages and the dashboard were each reshaped several times from written
briefs and rejected on the result, until one was approved **as a render
first** (a design canvas), then coded exactly (CR-081, CR-082). For any
visible redesign: canvas → owner approval → code. Never redesign from a
written brief alone. Compare screenshots against the approved render before
calling it done — that comparison is what catches the defects tests do not.
