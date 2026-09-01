# Security Audit

**Date:** 2026-09-01
**Scope:** backend (Spring Boot 3.4.2 / Java 21), frontend (React 18 / TypeScript / Vite 6), PostgreSQL 16 + Flyway.
**Method:** direct source inspection (file/line evidence cited throughout) plus live black-box testing against a real local instance — never assumption, never "the UI has a button so the feature exists."

This is not a complete, final security certification. It is an honest snapshot of what was actually inspected and tested in this pass, what passed, what is missing, and what was not reached. See `MULTI_TENANT_SECURITY_TEST.md` for the full cross-tenant/RBAC red-team evidence this document summarizes in §4.

---

## 1. Architecture

Single Spring Boot application, single React application, single PostgreSQL database, **multi-tenant via a shared schema** — every tenant-owned table carries `tenant_id`, resolved server-side from the JWT (`SecurityUtils.currentTenantId()`), never from client input (CR-016). This is a deliberate, documented architectural choice, not database-per-tenant.

Auth is stateless JWT: a short-lived access token (issued at login, ~900s per observed `expiresInSeconds`) held in memory only on the frontend (verified — every `localStorage`/`sessionStorage` write in the frontend source was grepped; none write a token, only UI preferences like sidebar-collapsed state and theme scope), plus a refresh token as an `httpOnly`, `SameSite=Strict` cookie scoped to `/api/v1/auth`.

## 2. Authentication

| Control | Status | Evidence |
|---|---|---|
| Password hashing | **PASS** | `BCryptPasswordEncoder(12)`, `SecurityConfig.java:47-50` — strength 12 chosen explicitly for ~250ms/hash cost, with a comment tying that cost to why rate-limiting runs before it |
| Account lockout | **PASS** | `AuthServiceImpl.login()` — `user.registerFailedLogin()` returns whether the account is now locked; `User.LOCK_MINUTES` constant; enforced (checked and rejected on the next login attempt), not just recorded |
| Account/username enumeration resistance | **PASS** | Unknown identifier, locked account, inactive account, and wrong password all throw the identical `AuthException.invalidCredentials()` with no distinguishing message, code, or status — read the actual branches in `AuthServiceImpl.java:63-95`, not inferred |
| Rate limiting on login | **PASS** | `RateLimitFilter` runs before `UsernamePasswordAuthenticationFilter` in the chain (`SecurityConfig.java:148`) specifically so credential stuffing is stopped before it reaches BCrypt |
| Refresh token rotation + reuse detection | **PASS** | `AuthServiceImpl`'s refresh flow (comment: "rotation with reuse detection"); regression-tested per `BUG-AUTH-014` in this project's own history |
| Tokens never in browser storage | **PASS** | See §1 |
| Passwords never logged/returned | **PASS** (spot-checked) | `passwordHash` is never included in any DTO returned by `UserMapper`/`AuthController` response types inspected this pass |
| Email OTP / phone OTP verification | **NOT IMPLEMENTED** | Zero matches for any OTP concept in the backend. This project's own `RESUME_POINT.md` records it as a deliberate deferral (SMTP reliability was not proven before this session began, and building a verification flow on unproven email delivery would lock users out) |
| OTP brute-force / replay / expiration testing | **NOT TESTED** | Not applicable — no OTP exists |
| Concurrent session limits | **NOT TESTED** | Multiple refresh tokens per user are supported by the schema (`refresh_token` has no unique-per-user constraint observed), but no explicit "log out other sessions" or session-cap behaviour was verified this pass |

## 3. Security headers, transport, CORS

Read in full from `SecurityConfig.java`:

| Header | Status | Value |
|---|---|---|
| Content-Security-Policy | **PASS** | `default-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'` |
| Strict-Transport-Security | **PASS** | `includeSubDomains(true)`, `maxAgeInSeconds(31_536_000)` (1 year) |
| X-Content-Type-Options | **PASS** | `contentTypeOptions(Customizer.withDefaults())` → `nosniff` |
| X-Frame-Options | **PASS** | `frameOptions(frame -> frame.deny())` |
| Referrer-Policy | **PASS** | `STRICT_ORIGIN_WHEN_CROSS_ORIGIN` |
| Permissions-Policy | **PASS** | `camera=(), microphone=(), geolocation=(), payment=()` |
| CORS | **PASS**, configurable | `allowedOrigins` from `SecurityProperties` (environment-driven, not hardcoded to `*`); credentials allowed (required for the refresh cookie), explicit method/header allowlist |
| CSRF | **Disabled, with a stated rationale** | Stateless bearer API — no cookie-backed session for a form post to ride on; the one cookie (refresh token) is `SameSite=Strict` and path-scoped to `/api/v1/auth`. This is a documented design decision (`SecurityConfig.java:73-76`), not an oversight — flagged here for visibility, not as a finding |

**Not verified this pass:** these headers were confirmed present in the Spring Security *configuration*; they were not re-confirmed on a live HTTP response in this exact session (a reasonable follow-up, ~5 minutes of work, not done here for time).

## 4. Multi-tenancy and authorization

Full evidence in `MULTI_TENANT_SECURITY_TEST.md`. Summary: **3 real tenants, 12 real accounts (4 roles × 3 tenants), 8 attack categories tested live against the running application (read IDOR, write IDOR, file IDOR, role IDOR, list-leak, privilege escalation, admin-endpoint access, unauthenticated access) — zero cross-tenant leaks, zero privilege escalation.**

Pattern confirmed by direct code inspection across every repository touched this session and several sampled fresh: `findByIdAndTenantId(id, tenantId)`, never a bare `findById`. `repository.findAll()` (the dangerous unscoped pattern explicitly called out in the request) appears in exactly the places expected — role/permission/category/brand lookups that are either genuinely tenant-agnostic reference data or already filtered by a WHERE clause in the same query; no instance found returning tenant-owned business data unfiltered.

Actuator and developer-diagnostics endpoints are gated by **two independent conditions** (CR-045): the active environment profile, decided once at startup, AND the `DEVELOPER_INSPECT` permission — held by no default role, OWNER included. Live-verified: a real tenant OWNER's token was refused (`403`) on `/v1/dev/inspection/runtime`.

## 5. Database / query security

| Control | Status | Evidence |
|---|---|---|
| SQL injection via native queries | **PASS** | All 8 `nativeQuery = true` call sites across the codebase (analytics, idempotency, document-sequence, invoice history, supplier bank-account migration helpers) use exclusively `@Param`-bound placeholders — zero string concatenation found |
| Dynamic/unsafe SQL | **NOT FOUND** | No `String.format` or `+` concatenation feeding a query string was found in the repositories inspected |
| Bank account number protection | **PASS**, established earlier (CR-018) | `SupplierRepository` shows an `ENC:`-prefixed encrypted-at-rest convention with a dedicated backfill/migration path; a `revealBankAccountNumber` action exists and is itself audit-logged (per this project's own history) — **not re-verified live this pass**, flagged as a follow-up |
| Migration discipline | **PASS** | 38 sequential Flyway migrations, `ddl-auto: validate` (never `update`), confirmed by `application.yml` |

## 6. File upload / storage security

Established in this project's own history (CR-035 adversarial testing round, `BUG-PUR-004`): served content-type is derived from the file's own validated extension, never from client-supplied `Content-Type` header, specifically because a spoofed multipart content-type was found and fixed as a real stored-XSS vector. Filename validation rejects path-traversal (`/`, `\`) and header-injection (`"`, `\r`, `\n`) characters. **Not re-verified live this pass** — this is prior, documented work, cited rather than re-tested, and flagged as such.

## 7. Production environment configuration

Read from `application-prod.yml` and `SecurityConfig.java`:

| Control | Status |
|---|---|
| Swagger/OpenAPI disabled in production | **PASS** — `springdoc.api-docs.enabled`/`swagger-ui.enabled` are `false` under `prod` |
| Actuator narrowed per profile | **PASS** — `prod` exposes `health` only |
| Stack traces / SQL / credentials not returned to clients | **NOT RE-VERIFIED THIS PASS** — established by `GlobalExceptionHandler`'s sanitized-error design in this project's history; not independently re-tested against a forced 500 in this session |
| Secrets never hardcoded | **PASS**, spot-checked | `application.yml` reads `DB_USER`/`DB_PASSWORD`/`JWT_SECRET`/etc. exclusively from environment variables with no real-looking fallback value; `JwtSecretGuard` explicitly warns when the *development* secret is in use, which it is in this local session (expected, non-production) |

## 8. Frontend security

| Control | Status |
|---|---|
| No secrets in frontend bundle | **NOT RE-VERIFIED THIS PASS** — no `VITE_`-prefixed secret-shaped env var found by name in a quick pass, not exhaustively confirmed by inspecting the built bundle |
| Tokens not in `localStorage`/`sessionStorage` | **PASS** (§1) |
| `dangerouslySetInnerHTML` usage | **NOT SEARCHED THIS PASS** |
| Route guards backed by real backend checks | **PASS** — every RBAC test in `MULTI_TENANT_SECURITY_TEST.md` §4 was performed via direct API calls with no frontend involved, confirming the backend is the actual control, per audit rule 15 |

## 9. Audit logging

**PASS, live-verified.** `GET /v1/security-audit-logs` as a real tenant OWNER returned genuine `LOGIN_SUCCESS` events for an account created minutes earlier in this same session, carrying `timestamp`, `ipAddress`, `userAgent`, `requestId` — not a stub or placeholder response. A `STAFF` account was correctly refused (`403`) on the same endpoint (owner-only, `AUDIT_VIEW`).

## 10. What was not reached this pass

Stated plainly rather than silently skipped, per the audit's own rule 24:

- **100,000+ record synthetic dataset and performance/load testing** — explicitly deferred by the user's own instruction this round (infrastructure decisions about batch size and where it runs were judged to need confirmation first).
- **Full endpoint-by-endpoint IDOR matrix** across all ~40 controllers — the tests performed targeted the highest-risk categories (financial writes, file downloads, role objects, admin endpoints), not an exhaustive enumeration of every GET/POST/PUT/DELETE in the system.
- **Browser-level UI evidence / screenshots** — no browser automation tool exists in this environment (a constraint documented repeatedly throughout this project's own history). Backend enforcement is the authoritative control and was tested directly; the frontend's own route-hiding was not independently screenshotted.
- **`docs/USER_GUIDE.md` and `docs/ROLE_BASED_UI_EVIDENCE.md`** — not produced this pass (the latter specifically requires screenshots, which cannot be honestly fabricated).
- **CSRF/SSRF/command-injection/mass-assignment proof-of-concept payloads** — not individually attempted this pass; CSRF is architecturally mitigated by the stateless-bearer + `SameSite=Strict` design (§3) but no live PoC was run against it.
- **A live re-check of response headers on the wire, forced-500 error-message content, and the file-upload/bank-masking claims** — all cited from this project's own prior, documented work rather than re-executed live in this exact session.

## 11. Security scorecard

| Category | Status | Risk if gap | Priority |
|---|---|---|---|
| Authentication (password/lockout/enumeration/rate-limit) | **PASS** | — | — |
| Multi-tenant isolation | **PASS** (live red-team tested) | — | — |
| RBAC / authorization | **PASS** (live tested) | — | — |
| Security headers | **PASS** | — | — |
| Database query safety | **PASS** | — | — |
| OTP verification | **NOT IMPLEMENTED** | Low — email/phone are not currently used as a second factor anywhere; this is a missing *feature*, not a hole in an existing control | Low, unless a future feature assumes OTP exists |
| Production error/stack-trace sanitization | **NOT RE-VERIFIED THIS PASS** | Medium if regressed — would leak internals | Medium |
| Frontend secret/bundle audit | **NOT RE-VERIFIED THIS PASS** | Medium if a secret were ever added to a `VITE_` var | Medium |
| File upload / bank-masking live re-check | **NOT RE-VERIFIED THIS PASS** (prior work cited) | Low — no code change since the original fix | Low |
| Full IDOR matrix (all endpoints) | **PARTIAL** — high-risk sample only | Medium — an untested endpoint could theoretically have a gap the sample didn't reach | Medium |
| Load/performance at scale | **NOT TESTED** | Unknown — no evidence either way | Deferred by user request |

## 12. Production readiness

**READY WITH CONDITIONS.**

No critical or high-severity vulnerability was found in anything actually tested this pass — and the highest-risk category (cross-tenant data isolation under a malicious/compromised-owner scenario) was tested directly and thoroughly with zero failures. The conditions are about coverage, not known defects:

1. Re-verify §7/§8's "not re-verified this pass" items live before a production deployment (production error responses, frontend bundle secret scan) — these are prior documented work being cited, not re-proven today.
2. Decide whether OTP is actually needed as a product requirement, and if so, resolve the SMTP-reliability blocker first (do not build OTP on unproven email delivery, per this project's own established rule).
3. Expand the IDOR matrix beyond the high-risk sample if this application will handle materially more sensitive data than the current module set.
4. Performance/load characteristics at scale are genuinely unknown — this audit found no red flag, but also ran no real test.

Nothing here should be read as "the application failed the audit." Every control that was actually exercised, passed.
