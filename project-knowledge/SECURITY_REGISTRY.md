# SECURITY REGISTRY

## Authentication

- **Login identifier:** one field, `identifier`, accepting mobile **or** email.
  Counter staff do not remember which they were registered with.
- **Password hashing:** BCrypt strength 12 (~250 ms/hash). Plain passwords never
  stored, logged, returned, or written to the audit log.
- **Access token:** JWT, HS256, 15 minutes. **Minimal claims only:**

  ```json
  { "iss": "hardware-erp", "sub": "42", "tv": 5, "iat": ..., "exp": ... }
  ```

  No name, no role, no permission list, no email. Authorities are loaded from
  the database on each request, so a permission revoked at 10:00 takes effect at
  10:00 — not when the token expires.

- **Refresh token:** 48 bytes from `SecureRandom`, base64url, opaque — **not a
  JWT**. Only `SHA-256(token)` reaches the database.
- **JWT secret:** from `JWT_SECRET` env var. Must decode to ≥32 bytes.
  The application **refuses to start** in the `prod` profile if the placeholder
  value is present.

## Token lifecycle

| Event | Refresh token | token_version | Effect |
|---|---|---|---|
| Login | new row | unchanged | new session |
| Refresh | old revoked (`ROTATED`), new issued, `replaced_by_token_id` set | unchanged | rotation |
| Refresh with an already-revoked token | **all user sessions revoked** | **+1** | theft response |
| Logout | this token revoked (`LOGOUT`) | **unchanged** | other devices keep working |
| Logout-all | all revoked (`LOGOUT_ALL`) | **+1** | every device signed out |
| Password change / reset | all revoked | **+1** | every device signed out |
| User deactivated / role changed | all revoked | **+1** | immediate lockout |

`token_version` is validated in `JwtAuthenticationFilter` against the current
database row on every request. A field that is issued but never checked is
decoration, not security.

## Account protection

| Control | Value |
|---|---|
| Failed login lockout | 5 attempts → 15 minutes |
| Reset on success | `failed_login_attempts = 0`, `locked_until = null` |
| Rate limit — login | 10 / minute per IP + 5 / minute per identifier |
| Rate limit — forgot-password | 3 / hour per IP + 3 / hour per identifier |
| Rate limit — reset-password | 10 / hour per IP |
| Rate limit — refresh | 30 / minute per IP |

Rate limiting is Bucket4j in-process with a Caffeine cache. A single-shop
monolith is one JVM; Redis would be cost with no benefit.

## Enumeration protection

Unknown account, wrong password, inactive account and locked account all return
**the identical response**:

```
401 { "success": false, "code": "INVALID_CREDENTIALS",
      "message": "Invalid credentials", ... }
```

`forgot-password` always returns the same 200 message whether or not the account
exists. `POST /api/v1/users` duplicate checks are behind `USER_MANAGE`, so the
409 there is not an enumeration surface.

## Authorization

Permission-based, never role-name-based.

```java
@PreAuthorize("hasAuthority('USER_MANAGE')")   // correct
@PreAuthorize("hasRole('OWNER')")              // banned for business rules
```

`STAFF` deliberately lacks `PRODUCT_VIEW_COST`. Enforcement is server-side: the
cost field is **omitted from the response DTO entirely** for users without it,
not hidden in React.

## Last active owner protection

The final active OWNER cannot be deleted, deactivated, or moved to another role.
Enforced in the service layer inside the transaction, with the owner count read
under a pessimistic lock so two simultaneous admin requests cannot both pass the
check. Returns `422 LAST_OWNER_PROTECTED`.

## Bootstrap owner (CR-003)

The old "create an owner if the user table is empty" rule is removed. Replaced by:

```
APP_BOOTSTRAP_ENABLED=true          # default false; must be explicit
APP_BOOTSTRAP_MOBILE=9876543210
APP_BOOTSTRAP_EMAIL=owner@shop.local
APP_BOOTSTRAP_PASSWORD=<strong>     # validated: >=12 chars, mixed
```

- Disabled by default. Production never silently creates an account.
- Idempotent: runs only when zero users exist AND the flag is on.
- The password is **never logged**, not even at DEBUG.
- `must_change_password = true` is forced.
- Application fails to start if the flag is on and the password is weak or absent.

## Token transport (decision)

**Access token:** returned in the JSON body; the React app keeps it in **memory
only** (a module variable in the auth store), never `localStorage`. An XSS bug
then steals a token that dies in 15 minutes rather than a permanent credential.

**Refresh token:** `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie.
JavaScript cannot read it, so XSS cannot exfiltrate the long-lived credential.
`SameSite=Strict` plus a path scoped to the auth endpoints removes the CSRF
exposure that would otherwise come with cookie auth. Configurable via
`app.security.refresh-token-transport: cookie | json` for non-browser clients.

Cost: the app must be served same-site (React behind the same origin, or a
reverse proxy mapping `/api` to Spring Boot). That is the recommended
deployment anyway for a single shop.

## HTTP security headers

`X-Content-Type-Options: nosniff` · `X-Frame-Options: DENY` ·
`Strict-Transport-Security` (1 year, includeSubDomains) ·
`Referrer-Policy: strict-origin-when-cross-origin` ·
`Content-Security-Policy: default-src 'self'; frame-ancestors 'none'` ·
`Permissions-Policy` minimal.

CSRF is disabled for the JSON API (stateless bearer auth) but the refresh cookie
is protected by `SameSite=Strict` + path scoping.

## Never logged, never returned, never audited

passwords · password hashes · raw refresh tokens · raw reset tokens ·
JWT secret · database password · mail password · full JWT strings

## Multi-tenancy (CR-016, added to this registry 2026-08-23 — see MASTER_PROJECT_STATUS.md §1 for why this section was missing until now)

Shared database, shared schema, `tenant_id` discriminator column on every
tenant-owned table — not schema-per-tenant, not database-per-tenant.
`SecurityUtils.currentTenantId()`/`requireCurrentTenantId()` is the **only**
sanctioned source of the acting tenant on any request; it is sourced from
`AppUserDetails`, itself loaded fresh from the database on every request by
`JwtAuthenticationFilter` (the same per-request reload that already served
`token_version` and permission revocation) — **never** from a request
parameter, path variable, or JWT claim. Every tenant-scoped repository query
must filter by it. Login identifier (`mobile_no`/`email`) is **globally**
unique across tenants, deliberately — see CR-016 in
`CHANGE_REQUEST_REGISTRY.md` for the full trade-off; login has no tenant
selector, so an identifier alone must resolve to exactly one user
platform-wide.

**BUG-SEC-001 (CRITICAL, fixed 2026-08-23)**: `security_audit_log` has no
`tenant_id` column of its own by design (it scopes through `user_id`, the
same reasoning as `refresh_token`/`activity_log`), but the repository query
reading it had no join back through `User.tenant.id` to recover that scope —
any tenant's OWNER could read every other tenant's security events. Fixed by
adding the join; see `BUG_REGISTRY.md` for full detail. **Lesson for every
future audit/log-style table that deliberately omits its own `tenant_id`**:
the omission is only safe if every query reading it re-derives the scope
through the FK it does carry — verify this explicitly, it is not automatic.

**Tenant self-registration (CR-028)**: `POST /v1/tenants/register` is public
(`permitAll`), rate-limited 5/hour/IP (`REGISTER_PER_IP` rule, mirroring the
existing login/forgot-password rate limits above). Creates a new tenant, its
4 default roles (permissions mirrored from `V1`'s seed), and an owner
account, atomically. Global mobile/email uniqueness (above) still applies —
a duplicate identifier is rejected with 409 regardless of which tenant is
registering.

**Cross-tenant penetration testing performed (CR-028)**: a real second
tenant was created end-to-end through the actual API (not fabricated data),
then every CRUD verb was attempted against every resource type as the other
tenant's authenticated user — direct-by-ID access, list/search-endpoint
leakage, and a role-assignment cross-tenant injection attempt. Found and
fixed the one gap above; ~10 other resource types confirmed already correct.
This second tenant remains live in the dev database as an ongoing test
fixture.

## Open security items

- CR-008: public self-registration for users **within** an existing tenant —
  still recommended NO, unchanged. (Tenant-level self-registration is now
  built, see above — a different thing: CR-016 always deferred *that* as its
  own follow-up, not a reversal of CR-008.)
- CR-009: seed accounts must not reach production.
- CR-018: supplier bank account number encryption at rest — **applied
  2026-08-23**, see CR-018 in CHANGE_REQUEST_REGISTRY.md's "As built" note.
- HTTPS termination is a deployment concern; HSTS is meaningless over plain HTTP.

---

## CR-045 — environment separation and developer inspection

### The rule

> Developer inspection is available only to authorized developers, in
> non-production environments.

Not: "prevent users from opening Chrome DevTools." That distinction drives
everything below.

### Two gates, both server-side, both required

| | Question it answers | Mechanism |
|---|---|---|
| Environment | "Is this a place where debugging happens?" | `app.developer-inspection.enabled` |
| Person | "Is this human a developer?" | `DEVELOPER_INSPECT` permission |

Neither is sufficient alone. A developer signing into production gets nothing.
A shop owner on a dev box gets nothing, because OWNER does not hold
`DEVELOPER_INSPECT`.

**Production is closed twice.** `application-prod.yml` sets a literal `false`
with no `${...}` placeholder, so there is no environment variable that opens
it; and `DeveloperInspectionService.environmentAllows()` returns false whenever
the `prod` profile is active, whatever the property says. The second lock is
there because the first is one line in a file someone will edit — the same
reasoning behind `JwtSecretGuard`.

**Role is not environment.** ADMIN in production is ordinary ERP
administration. DEVELOPER in dev/test is diagnostics. The two are never the
same grant, which is why `DEVELOPER_INSPECT` is withheld from OWNER in both
places roles get their permissions (V30, and
`TenantRegistrationServiceImpl.DEVELOPER_MODULE`).

### Deliberately not implemented

No F12 interception, right-click blocking, Ctrl+Shift+I handler, DevTools
detection loop, or console clearing. Each is bypassed in seconds, none protects
anything, and together they make the application feel broken to honest users.
Anyone proposing them later should read this paragraph first.

Frontend permission checks — `PermissionGate`, `RequirePermission`, the sidebar
filter — remain what they have always been: a way to avoid showing a page the
server would refuse. They are never the control.

### Production information disclosure

| Surface | Production |
|---|---|
| Swagger UI / OpenAPI JSON | disabled (`springdoc.*.enabled: false`) |
| Actuator | `health` only, `show-details: never`; `env`/`beans`/`configprops`/`loggers`/`mappings`/`heapdump`/`threaddump` all unexposed |
| `/v1/dev/**`, `/v1/debug/**` | `denyAll` in the filter chain |
| Error bodies | code, message, path, request id, timestamp. Never a stack trace, SQL text, file path or class name — already true before CR-045, restated in `application-prod.yml` where a reader looks for it |
| Whitelabel error page | off |
| Frontend source maps | not emitted; asserted by CI |
| Logs | root `WARN`, application `INFO`. Never passwords, tokens or API keys |

### Secrets

`application.yml` carried a real developer database login as its
`spring.datasource` default. Removed **before the repository's first commit**,
so it is absent from history rather than merely from `HEAD`.

CI fails the build on a tracked `.env`, a tracked key or certificate, a
provider API key format (`sk-`, `AIza`, `ghp_`, `github_pat_`, `xox*-`), a PEM
private key block, or an assigned secret-like literal. The two base64 JWT
placeholders in `application.yml` and the test sources are allow-listed by
value; both are already listed in `JwtSecretGuard.KNOWN_PLACEHOLDERS` and the
application refuses to start with either under the `prod` profile.

### Rate limiting had no test coverage (BUG-SEC-003)

`RateLimitFilter` selected its bucket with `getServletPath()`, which a real
container fills in but MockMvc leaves empty — so every integration test passed
straight through the filter and four of `RateLimitIT`'s five assertions failed.
Production throttling worked; its evidence did not exist. Now keyed on
`SecurityUtils.requestPath()`, which is identical in both. A security control
no test can reach is indistinguishable from one that is switched off.
