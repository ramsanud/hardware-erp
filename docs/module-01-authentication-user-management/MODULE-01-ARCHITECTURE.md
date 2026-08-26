# MODULE-01-ARCHITECTURE

## Deployment shape

One Spring Boot JAR, one React application, one PostgreSQL database. Not
microservices; module folders express **boundaries**, not deployment units.

```
  React (Vite)          Spring Boot 3.4 / Java 21        PostgreSQL 16
  localhost:5173  <-->  localhost:8080/api        <-->   localhost:5432
```

## Backend package structure

```
com.hardware.erp
├── HardwareErpApplication.java
├── common/                       shared, never module-specific
│   ├── entity/BaseEntity            created_at/by, updated_at/by (no @Version)
│   ├── dto/                         ApiResponse, ErrorResponse, PageResponse
│   ├── exception/                   BusinessException hierarchy + handler
│   └── web/RequestCorrelationFilter X-Request-ID + MDC
├── config/
│   ├── JpaAuditingConfig            AuditorAware from the security context
│   ├── AsyncConfig                  email only; never transactional work
│   ├── OpenApiConfig                Swagger, bearer scheme, module grouping
│   ├── BootstrapProperties
│   ├── BootstrapOwnerInitializer    first owner, opt-in, strength-checked
│   └── JwtSecretGuard               refuses to start on a placeholder secret
├── security/
│   ├── SecurityConfig               filter chain, CORS, headers, BCrypt(12)
│   ├── JwtService                   sign, parse, hash
│   ├── JwtAuthenticationFilter      token -> user -> token_version check
│   ├── AppUserDetails(-Service)
│   ├── RefreshTokenCookieService    HttpOnly SameSite=Strict transport
│   ├── SecurityUtils                current user, client IP, user agent
│   └── ratelimit/                   Bucket4j + Caffeine, in-process
└── auth/                         MODULE 1
    ├── entity/       Permission, PermissionCode, Role, RoleStatus, User,
    │                 UserStatus, RefreshToken, RevokedReason,
    │                 PasswordResetToken, SecurityAuditLog, AuditAction
    ├── repository/   6 Spring Data interfaces
    ├── dto/          17 records
    ├── mapper/       UserMapper
    ├── service/      6 interfaces
    ├── service/impl/ 8 implementations + TokenCleanupJob
    └── controller/   Auth, User, Role, Permission, SecurityAuditLog
```

Modules 2-12 add a sibling package under `com.hardware.erp` with the same
internal shape. `common`, `config` and `security` are never duplicated.

## Layer responsibilities

| Layer | Owns | Never does |
|---|---|---|
| Controller | HTTP, status codes, `@Valid`, `@PreAuthorize`, Swagger | business rules, SQL |
| DTO | the wire contract | JPA annotations |
| Mapper | entity to DTO | database access |
| Service | business rules, transactions, audit | HTTP concerns |
| Repository | queries | business decisions |
| Entity | persistence + invariants that belong to the row | HTTP or DTO knowledge |

Entities carry behaviour where it protects an invariant:
`User.applyNewPassword()` also bumps `tokenVersion` and clears the lock counter,
so no code path can change a password and leave old sessions alive.

## Security flow

```
Request
  |
  v  Order.HIGHEST_PRECEDENCE
RequestCorrelationFilter   X-Request-ID into MDC and the response
  |
  v  before UsernamePasswordAuthenticationFilter
RateLimitFilter            /auth/login, /refresh, /forgot, /reset
  |                        keyed by IP and by identifier -> 429
  v
JwtAuthenticationFilter    skipped on public paths
  |                        parse -> load user -> compare token_version
  v
FilterSecurityInterceptor  URL rules from SecurityConfig
  |
  v
@PreAuthorize              method-level permission check -> 403
  |
  v
Controller
```

Rate limiting runs **before** authentication deliberately: BCrypt at strength 12
costs about 250 ms per attempt, which is otherwise a denial-of-service lever.

Headers set on every response: `X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, HSTS (1 year, includeSubDomains),
`Referrer-Policy: strict-origin-when-cross-origin`,
`Content-Security-Policy: default-src 'self'; frame-ancestors 'none'`,
and a minimal `Permissions-Policy`.

CSRF is disabled because the API is stateless bearer-token based. The one cookie
in play - the refresh token - is `SameSite=Strict` and path-scoped to
`/api/v1/auth`.

## Entity relationships

```
   permission                     role                     app_user
  +-------------+           +--------------+           +---------------+
  | permission_id PK|        | role_id PK   |           | user_id PK    |
  | permission_code |        | role_code UQ |           | role_id FK ---+---> role
  | module_code     |        | role_name UQ*|           | mobile_no UQ  |
  +--------+--------+        | system_role  |           | email UQ*     |
           |                 | status       |           | password_hash |
           |                 +------+-------+           | status        |
           |                        |                   | token_version |
           |   role_permission      |                   | deleted_at    |
           +---+----------------+---+                   +-------+-------+
               | role_id PK,FK  |                               |
               | permission_id  |                               |
               +----------------+                               |
                                                                |
        refresh_token                     password_reset_token  |
       +----------------------+          +--------------------+ |
       | refresh_token_id PK  |          | reset_token_id PK  | |
       | user_id FK ----------+----------+ user_id FK --------+-+
       | token_hash UQ        |          | token_hash UQ      |
       | expires_at           |          | expires_at         |
       | revoked_at / reason  |          | used_at            |
       | replaced_by_token_id-+--+       +--------------------+
       +----------------------+  |
                    ^            |   self-reference: the rotation chain
                    +------------+

        security_audit_log        (no FK to app_user, deliberately)
       +------------------------+
       | audit_id PK            |  must stay readable regardless of what
       | action, entity_type    |  happens to the user row, and a failed
       | user_id, full_name     |  login for an unknown identifier has no
       | success, failure_reason|  user to point at
       | ip_address, request_id |
       +------------------------+
```

`UQ*` = case-insensitive, enforced by a functional unique index on
`lower(col)` because PostgreSQL compares case-sensitively (BUG-AUTH-009).

## Sequence: login

```
Browser   Controller   AuthService   UserRepo   PostgreSQL   AuditService
  |           |             |           |           |             |
  |--login--->|             |           |           |             |
  |           |--login----->|           |           |             |
  |           |             |--findByIdentifier---->|             |
  |           |             |<----------user--------|             |
  |           |             | locked? active?                     |
  |           |             | BCrypt.matches(...)                 |
  |           |             |--------------------------(failure)->|
  |           |             | registerSuccessfulLogin()           |
  |           |             |--save---->|---------->|             |
  |           |             |----------------------(LOGIN_SUCCESS)|
  |           |             | generateAccessToken(id, tv)         |
  |           |             | save SHA-256(refresh token)         |
  |           |<--response--|           |           |             |
  |<--200 + Set-Cookie------|           |           |             |
```

The audit write uses `REQUIRES_NEW`, so a failed login is still recorded even
though the surrounding transaction rolls back.

## Sequence: refresh with reuse detection

```
Browser        AuthService              RefreshTokenRepo      User
  |                 |                        |                |
  |--refresh------->|                        |                |
  |                 |--findByTokenHash------>|                |
  |                 |<-------row-------------|                |
  |                 |                                          |
  |                 | row.revokedAt != null ?                  |
  |                 |    yes -> revokeAllForUser(REUSE_DETECTED)
  |                 |        -> user.invalidateAllTokens()---->|
  |                 |        -> audit REFRESH_TOKEN_REUSE_DETECTED
  |<--401 TOKEN_REUSE                                          |
  |                 |                                          |
  |                 |    no  -> save new token                 |
  |                 |        -> old.revoke(ROTATED)            |
  |                 |        -> old.replacedByTokenId = new.id |
  |<--200 + new cookie                                         |
```

## Frontend architecture

```
src/
├── modules/auth/{pages,forms,components,services,hooks,types,validation,constants,tests}
├── shared/{components/ui,components,hooks,types,constants}
├── services/       apiClient (the only place axios lives), tokenStorage
├── layouts/        AuthLayout, AppLayout, Sidebar
├── routes/         AppRoutes, ProtectedRoute, RequirePermission
└── theme/          ThemeProvider, ModeToggle
```

```
Page  ->  Hook  ->  Service  ->  apiClient  ->  HTTP
                                    |
                          request:  attach Bearer token
                          response: on 401, single-flight refresh, retry once
```

Single-flight matters: when four requests 401 together, only the first
refreshes. Without it each would rotate the refresh token and the backend would
treat the replay as theft.

Access token lives in a module variable, never `localStorage`. The refresh token
is an HttpOnly cookie JavaScript cannot read.
