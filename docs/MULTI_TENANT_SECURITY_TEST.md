# Multi-Tenant Security Test

**Date:** 2026-09-01
**Environment:** local (dev profile, real PostgreSQL via docker-compose), backend running at `localhost:8080/api`
**Method:** authorized testing against the application's own local instance, using real registered tenants and real API calls (not mocked). No third-party system was touched. No destructive action was taken against any tenant's data beyond the synthetic test data created for this exercise.

This document is the evidence trail for one slice of the full audit requested — cross-tenant isolation and RBAC enforcement. It does **not** cover the 100,000+ record synthetic dataset or performance testing (explicitly deferred, see `RESUME_POINT.md`), and it does not include browser screenshots (no browser automation tool is available in this environment — stated plainly, not fabricated).

---

## 1. Tenant setup

Three real tenants were registered through the actual public registration endpoint (`POST /v1/tenants/register`), not inserted directly into the database:

| Tenant | Tenant ID | Slug | Owner mobile |
|---|---|---|---|
| Hardware Tenant Alpha | 16 | `hardware-tenant-alpha` | 9990000001 |
| Hardware Tenant Beta | 17 | `hardware-tenant-beta` | 9990000002 |
| Hardware Tenant Gamma | 18 | `hardware-tenant-gamma` | 9990000003 |

All data is synthetic (`*.audit-test.local` emails, `999000*`/`999XXX*` mobile number ranges that do not correspond to real people). Credentials are **not** committed to git — they exist only in this local session's scratch files (`/tmp/audit/token-*.txt`, per-session temp storage) and are not written into this document.

## 2. Role accounts

Registration automatically creates four system roles per tenant (`OWNER`, `MANAGER`, `ACCOUNTANT`, `STAFF`) and one `OWNER` user. Three additional users per tenant were created through the real `POST /v1/users` endpoint (owner-only), one per remaining role — 12 accounts total, all logged in through the real `POST /v1/auth/login` endpoint to obtain genuine JWTs.

**A note on the requested "Labour" and "Customer" role accounts:** this system has no such login roles. `Worker` (labour) is a managed business entity with no authentication of its own — a shop tracks workers and pays them, but a worker does not sign in. There is likewise no customer-facing login/portal anywhere in this codebase (confirmed by code inspection, not assumption) — a customer is a record the shop maintains, not an account holder. Creating fake "labour" or "customer" logins would have meant inventing authentication surface area that does not exist in the product, which is out of scope for a security *audit* of what's actually built. This is stated here rather than silently substituted.

| Tenant | Role | Verified permission set on login (excerpt) |
|---|---|---|
| Alpha | OWNER | Every ERP permission except `DEVELOPER_INSPECT` (confirmed — see §5) |
| Alpha | MANAGER | Full operational set, excluding `USER_MANAGE`/`ROLE_MANAGE`/`AUDIT_VIEW`/`SETTINGS_MANAGE` (confirmed §4) |
| Alpha | ACCOUNTANT | Financial/billing set, excluding `PRODUCT_MANAGE`/`INVENTORY_ADJUST` (confirmed §4) |
| Alpha | STAFF | Counter-billing set, excluding `PRODUCT_MANAGE`/`SUPPLIER_VIEW`/`AUDIT_VIEW` (confirmed §3, §4) |
| Beta, Gamma | same four roles | Same grants — role definitions are identical across tenants by design (`TenantRegistrationServiceImpl.ROLE_PERMISSIONS`) |

## 3. Cross-tenant attack testing (IDOR / BOLA)

Tenant Alpha was populated with real records via the real API:

| Resource | ID | Note |
|---|---|---|
| Customer | 1064 | "Alpha Secret Customer" |
| Supplier | 1033 | "Alpha Secret Supplier" |
| Product | 10113 | "Alpha Secret Product", ₹500.00 |
| Invoice | 55 | `INV-000001` — confirms invoice numbering is genuinely per-tenant (Alpha's first invoice is `-000001` despite the database's own auto-increment `invoice_id` being 55) |

Tenant Beta's OWNER and STAFF tokens (real JWTs from real logins, not forged) were then used to attempt access to these exact IDs.

### 3.1 Read (GET) — Beta OWNER against Alpha's records

| Endpoint | Expected | Actual | Result |
|---|---|---|---|
| `GET /v1/customers/1064` | 403 or 404, never data | `404 {"message":"Customer not found"}` | **PASS** |
| `GET /v1/suppliers/1033` | 403 or 404, never data | `404 {"message":"Supplier not found"}` | **PASS** |
| `GET /v1/products/10113` | 403 or 404, never data | `404 {"message":"Product not found"}` | **PASS** |
| `GET /v1/invoices/55` | 403 or 404, never data | `404 {"message":"Invoice not found"}` | **PASS** |

### 3.2 Read (GET) — Beta STAFF against Alpha's records

| Endpoint | Expected | Actual | Result |
|---|---|---|---|
| `GET /v1/customers/1064` | 403 or 404 | `404` | **PASS** |
| `GET /v1/suppliers/1033` | 403 or 404 | `403 ACCESS_DENIED` (STAFF has no `SUPPLIER_VIEW` at all — the permission gate fires before the tenant-scoped lookup ever runs) | **PASS** |
| `GET /v1/products/10113` | 403 or 404 | `404` | **PASS** |
| `GET /v1/invoices/55` | 403 or 404 | `404` | **PASS** |

### 3.3 Write (WRITE-based IDOR is the more dangerous class — tested explicitly) — Beta OWNER against Alpha's invoice/customer

| Attempt | Expected | Actual | Result |
|---|---|---|---|
| `PUT /v1/customers/1064` (rename to "HACKED BY BETA") | 403/404, no mutation | `404 Customer not found` | **PASS** |
| `POST /v1/invoices/55/payments` (record a ₹590 payment against Alpha's invoice) | 403/404, no mutation | `404 Invoice not found` | **PASS** |
| `POST /v1/invoices/55/cancel` (cancel Alpha's invoice, would reverse Alpha's stock) | 403/404, no mutation | `404 Invoice not found` | **PASS** |

### 3.4 File / document IDOR

| Attempt | Expected | Actual | Result |
|---|---|---|---|
| `GET /v1/invoices/55/pdf` as Beta OWNER (download Alpha's invoice PDF) | 403/404, no file | `404 Invoice not found` — no bytes returned, confirmed by inspecting the response (JSON error, not a PDF) | **PASS** |

### 3.5 Role / permission-object IDOR

| Attempt | Expected | Actual | Result |
|---|---|---|---|
| `GET /v1/roles/63` as Gamma OWNER (Alpha's own `OWNER` role id) | 403/404 | `404 Role not found` | **PASS** |

### 3.6 List-endpoint leak check

| Attempt | Expected | Actual | Result |
|---|---|---|---|
| `GET /v1/customers?search=Alpha Secret` as Beta OWNER | Empty result set, not Alpha's customer | `{"content":[],"totalElements":0}` | **PASS** |

### 3.7 Privilege escalation via cross-tenant role id

| Attempt | Expected | Actual | Result |
|---|---|---|---|
| Alpha OWNER creates a user with Beta's `MANAGER` role id (68) | Rejected — a tenant must not be able to grant a role it does not own | `404 Role not found` (role lookup is itself tenant-scoped) | **PASS** |

### 3.8 Admin / internal endpoint access (the "compromised owner" scenario, Phase 22)

| Attempt | Expected | Actual | Result |
|---|---|---|---|
| Alpha OWNER → `GET /actuator/env` | Denied | `404` (the path does not resolve at all under this profile's actuator exposure — no information disclosure either way) | **PASS** |
| Alpha OWNER → `GET /v1/dev/inspection/runtime` | Denied (OWNER does not hold `DEVELOPER_INSPECT` by design, CR-045) | `403 ACCESS_DENIED` | **PASS** |
| Unauthenticated → `GET /actuator/env` | Denied | `404`, identical to the authenticated case — no oracle for whether the path exists | **PASS** |
| No token at all → `GET /v1/customers` | 401 | `401 UNAUTHENTICATED` | **PASS** |

**Conclusion for the "malicious/compromised owner" scenario:** across 8 distinct attack categories (read IDOR, write IDOR, file IDOR, role IDOR, list-leak, privilege escalation, actuator access, developer-diagnostics access), the Alpha/Beta/Gamma owner accounts could not access, modify, or even detect the existence of another tenant's data or reach any internal endpoint beyond their own permission grant. **Zero cross-tenant leaks found.**

## 4. Within-tenant RBAC enforcement

Tested against Tenant Alpha's own MANAGER/ACCOUNTANT/STAFF accounts:

| Role | Action attempted | Required permission | Expected | Actual | Result |
|---|---|---|---|---|---|
| STAFF | `POST /v1/products` (create product) | `PRODUCT_MANAGE` | 403 | `403 ACCESS_DENIED` | **PASS** |
| ACCOUNTANT | `POST /v1/stock/{id}/adjust` | `INVENTORY_ADJUST` | 403 | `403 ACCESS_DENIED` | **PASS** |
| MANAGER | `POST /v1/users` (create a user) | `USER_MANAGE` (owner-only) | 403 | `403 ACCESS_DENIED` | **PASS** |
| STAFF | `GET /v1/security-audit-logs` | `AUDIT_VIEW` (owner-only) | 403 | `403 ACCESS_DENIED` | **PASS** |
| ACCOUNTANT | `GET /v1/invoices` (own permitted resource) | `INVOICE_VIEW` | 200 | `200`, real data returned | **PASS** (a negative-only test suite proves nothing; this confirms the gate isn't simply denying everything) |
| OWNER | `GET /v1/security-audit-logs` | `AUDIT_VIEW` | 200 | `200`, real `LOGIN_SUCCESS` events with timestamp/IP/user-agent for the just-created Alpha Staff account | **PASS** — also confirms audit logging is genuinely capturing real security events, not a stub |

Every permission check enforced here is a real `@PreAuthorize` / repository-tenant-scoping check reached over HTTP, not a frontend route guard being trusted — per the audit's own rule 15 ("frontend restrictions are not security controls"), no frontend code was involved in any test in this document.

## 5. Regression note

None of the code paths exercised here were modified during this test — this document is pure black-box verification of already-shipped behaviour (`findByIdAndTenantId` tenant-scoping, `@PreAuthorize` permission gates, CR-045's developer-inspection dual gate). No fix was required as a result of this pass; every test in §3 and §4 passed on the first attempt.

## 6. What this document does not cover

- The 100,000+ record synthetic dataset and performance testing under load (explicitly deferred — see `RESUME_POINT.md`).
- Browser/UI-level verification of role-based navigation (no browser automation tool is available in this environment; the backend enforcement tested above is the authoritative control per rule 15, but the frontend's own route-hiding was not independently screenshotted).
- OTP-related attack testing (brute force, replay, expiration) — not applicable, OTP is not implemented in this codebase (confirmed by code inspection, zero matches for any OTP concept).
- A full endpoint-by-endpoint security matrix across every one of the ~40 controllers in this codebase — the sample above targets the highest-risk categories (financial writes, file downloads, role/permission objects, admin endpoints) rather than exhaustively enumerating every GET.
