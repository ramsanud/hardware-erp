# MODULE-01-API

Base URL: `http://localhost:8080/api` · Swagger: `/api/swagger-ui.html`

## Response envelopes

Every 2xx:
```json
{ "success": true, "message": "...", "data": {}, "timestamp": "2026-08-14T09:14:22.331+05:30" }
```

Every 4xx/5xx:
```json
{ "success": false, "message": "...", "code": "...", "path": "...",
  "requestId": "...", "timestamp": "...", "errors": { "field": "reason" } }
```

`errors` appears only for `VALIDATION_ERROR`. `requestId` matches the
`X-Request-ID` response header and the server log line.

## Endpoint catalog

### Authentication - `/v1/auth`

| Method | Path | Permission | Success |
|---|---|---|---|
| POST | `/login` | public | 200 |
| POST | `/refresh` | public (cookie) | 200 |
| POST | `/forgot-password` | public | 200 |
| POST | `/reset-password` | public | 200 |
| POST | `/logout` | authenticated | 200 |
| POST | `/logout-all` | authenticated | 200 |
| GET | `/me` | authenticated | 200 |
| PUT | `/me` | authenticated | 200 |
| POST | `/change-password` | authenticated | 200 |
| GET | `/sessions` | authenticated | 200 |
| DELETE | `/sessions/{id}` | authenticated | 204 |

### Users - `/v1/users`

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `` | `USER_VIEW` | 200 |
| GET | `/{id}` | `USER_VIEW` | 200 |
| POST | `` | `USER_MANAGE` | 201 |
| PUT | `/{id}` | `USER_MANAGE` | 200 |
| POST | `/{id}/reset-password` | `USER_MANAGE` | 200 |
| DELETE | `/{id}` | `USER_MANAGE` | 204 |

Query parameters on `GET /v1/users`: `search`, `status`, `roleId`, `page`,
`size` (clamped to 100), `sortBy` (whitelisted), `sortDir`.

There is **no** self-registration endpoint. `POST /v1/users` is the only way an
account is created. "Activate" and "deactivate" are not separate endpoints:
activation is `PUT /{id}` with `status: ACTIVE`, deactivation is either
`PUT /{id}` with `status: INACTIVE` or `DELETE /{id}` for a soft delete.

### Roles - `/v1/roles`

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `` | `ROLE_VIEW` | 200 |
| GET | `/{id}` | `ROLE_VIEW` | 200 |
| POST | `` | `ROLE_MANAGE` | 201 |
| PUT | `/{id}` | `ROLE_MANAGE` | 200 |
| DELETE | `/{id}` | `ROLE_MANAGE` | 204 |

Permissions are assigned by sending the full `permissions` array on POST or PUT.
There is no separate assign-permission endpoint.

### Permissions - `/v1/permissions`

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `` | `ROLE_VIEW` | 200 |
| GET | `/grouped` | `ROLE_VIEW` | 200 |

### Security audit log - `/v1/security-audit-logs`

| Method | Path | Permission | Success |
|---|---|---|---|
| GET | `` | `AUDIT_VIEW` | 200 |

Query parameters: `userId`, `action`, `from`, `to`, `page`, `size`, `sortBy`, `sortDir`.

## Status codes

| Code | When |
|---|---|
| 200 | read / update / action succeeded |
| 201 | resource created |
| 204 | delete succeeded, no body |
| 400 | malformed JSON, bad parameter, validation failure |
| 401 | missing, invalid or expired token; invalid credentials |
| 403 | authenticated but lacks the permission |
| 404 | resource or endpoint not found |
| 409 | duplicate resource, or stale record (optimistic lock) |
| 422 | business rule violated (last owner, OWNER permissions, system role) |
| 429 | rate limit exceeded; carries `Retry-After` |
| 500 | bug - generic message only, detail in the server log |

## Request flow

```
Client
  |  POST /api/v1/users   Authorization: Bearer <jwt>
  v
RequestCorrelationFilter   attaches X-Request-ID
  v
RateLimitFilter            auth endpoints only -> 429
  v
JwtAuthenticationFilter    parse token -> load user -> compare token_version
  v
@PreAuthorize              hasAuthority('USER_MANAGE')  -> 403
  v
UserController             @Valid on the request body   -> 400
  v
UserServiceImpl            duplicates -> 409, business rules -> 422
  v
UserRepository             Spring Data JPA
  v
PostgreSQL
  v
UserMapper -> UserResponse -> ApiResponse -> JSON
```

## Authentication flow

```
POST /v1/auth/login { identifier, password }
        |
        +-- unknown identifier ------+
        +-- account locked ----------+--> 401 "Invalid credentials"
        +-- account inactive --------+     (all four identical, on purpose)
        +-- password mismatch -------+     5th mismatch -> locked 15 min
        |
        v  all checks pass
   failed_login_attempts = 0, last_login_at = now
        |
        v
   access token (JWT, 15 min)  -> response body
   refresh token (opaque, 7 d) -> HttpOnly SameSite=Strict cookie
```

`identifier` accepts a mobile number **or** an email address.

## JWT flow

```
Login -> server signs a JWT with HS256

   { "iss": "hardware-erp", "sub": "1", "tv": 0, "iat": ..., "exp": ... }

Client keeps it in memory (never localStorage)
   |
   |  every request: Authorization: Bearer <jwt>
   v
JwtAuthenticationFilter
   |-- signature valid?          no -> anonymous -> 401
   |-- issuer matches?           no -> anonymous
   |-- not expired?              no -> anonymous
   |-- load user by sub
   |-- token_version == tv?      no -> anonymous   <-- instant revocation
   |-- user active and unlocked? no -> anonymous
   v
Authentication set with the user's current permissions
```

Only five claims. No name, role, email or permission list: a JWT is signed, not
encrypted, and stale authorities would otherwise survive until expiry.

## Refresh token flow

```
POST /v1/auth/refresh    (token read from the cookie)
        |
        v
   look up SHA-256(token) in refresh_token
        |
        +-- not found -----------------> 401 INVALID_REFRESH_TOKEN
        +-- already revoked -----------> THEFT RESPONSE:
        |                                revoke every session for the user,
        |                                increment token_version,
        |                                401 TOKEN_REUSE
        +-- expired -------------------> 401 REFRESH_TOKEN_EXPIRED
        |
        v  usable
   issue a new token, revoke the old one,
   set replaced_by_token_id on the old row
        |
        v
   new access token + new refresh cookie
```

The rotation chain is walkable through `replaced_by_token_id`, which is what
turns "a token was reused" into "here is the session it came from".

## What invalidates what

| Event | Refresh tokens | token_version | Effect |
|---|---|---|---|
| Login | new row | unchanged | new session |
| Refresh | old revoked, new issued | unchanged | rotation |
| Reuse detected | **all revoked** | **+1** | full lockout |
| Logout | this one revoked | unchanged | other devices unaffected |
| Logout-all | all revoked | **+1** | every device out |
| Password change / reset | all revoked | **+1** | every device out |
| Role change / deactivation | all revoked | **+1** | immediate |

## Permission matrix

| Permission | OWNER | MANAGER | ACCOUNTANT | STAFF |
|---|---|---|---|---|
| USER_VIEW | yes | yes | - | - |
| USER_MANAGE | yes | - | - | - |
| ROLE_VIEW | yes | yes | - | - |
| ROLE_MANAGE | yes | - | - | - |
| AUDIT_VIEW | yes | - | - | - |
| CUSTOMER_VIEW | yes | yes | yes | yes |
| CUSTOMER_MANAGE | yes | yes | yes | yes |
| SUPPLIER_VIEW | yes | yes | yes | - |
| SUPPLIER_MANAGE | yes | yes | - | - |
| PRODUCT_VIEW | yes | yes | yes | yes |
| PRODUCT_MANAGE | yes | yes | - | - |
| **PRODUCT_VIEW_COST** | yes | yes | yes | **no** |
| PRODUCT_VIEW_STOCK | yes | yes | yes | yes |
| INVOICE_CREATE | yes | yes | yes | yes |
| INVOICE_CANCEL | yes | - | - | - |
| REPORT_FINANCIAL | yes | - | yes | - |

31 permissions are seeded across 11 module groups. The `permission` table is
authoritative; `PermissionCode.java` mirrors it only so `@PreAuthorize`
expressions are compiler-checked. `PermissionCodeConsistencyTest` fails the
build if the two diverge.

Authorization is always permission-based. `hasRole('OWNER')` is never used for
a business rule.
