# Hardware Shop ERP — Single Shop Monolith

**Module 1: Authentication — COMPLETE**

Java 21 · Spring Boot 3.4 · MySQL 8 · Flyway · JWT · React 18 + TypeScript + Vite + Tailwind + shadcn/ui

No multi-tenancy. No microservices. No Kubernetes. One shop, one database, one JAR.

---

## Folder structure

```
hardware-erp/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/hardware/erp/
│       │   │   ├── HardwareErpApplication.java
│       │   │   │
│       │   │   ├── common/                      shared across all 10 modules
│       │   │   │   ├── entity/BaseEntity.java           audit columns + @Version
│       │   │   │   ├── dto/ApiResponse.java             success envelope
│       │   │   │   ├── dto/PageResponse.java            stable pagination shape
│       │   │   │   └── exception/
│       │   │   │       ├── BusinessException.java
│       │   │   │       ├── ResourceNotFoundException.java
│       │   │   │       ├── DuplicateResourceException.java
│       │   │   │       ├── AuthException.java
│       │   │   │       ├── ErrorResponse.java
│       │   │   │       └── GlobalExceptionHandler.java
│       │   │   │
│       │   │   ├── config/
│       │   │   │   ├── JpaAuditingConfig.java           created_by / updated_by
│       │   │   │   ├── AsyncConfig.java
│       │   │   │   ├── OpenApiConfig.java               Swagger + bearer auth
│       │   │   │   └── DataInitializer.java             first owner account
│       │   │   │
│       │   │   ├── security/
│       │   │   │   ├── SecurityConfig.java              filter chain, CORS, BCrypt
│       │   │   │   ├── JwtProperties.java
│       │   │   │   ├── JwtService.java                  sign / parse / hash
│       │   │   │   ├── JwtAuthenticationFilter.java
│       │   │   │   ├── JwtAuthEntryPoint.java           401 as JSON
│       │   │   │   ├── RestAccessDeniedHandler.java     403 as JSON
│       │   │   │   ├── AppUserDetails.java
│       │   │   │   ├── AppUserDetailsService.java
│       │   │   │   └── SecurityUtils.java
│       │   │   │
│       │   │   └── auth/                        MODULE 1
│       │   │       ├── entity/       User, Role, Permissions, UserStatus,
│       │   │       │                 RefreshToken, PasswordResetToken, AuditLog
│       │   │       ├── repository/   5 Spring Data interfaces
│       │   │       ├── dto/          13 request/response records
│       │   │       ├── mapper/       UserMapper
│       │   │       ├── service/      AuthService, UserService, RoleService,
│       │   │       │                 MailService, AuditService
│       │   │       ├── service/impl/ + TokenCleanupJob (nightly purge)
│       │   │       └── controller/   AuthController, UserController, RoleController
│       │   │
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/V1__auth_schema.sql
│       │
│       └── test/
│           ├── java/com/hardware/erp/
│           │   ├── security/JwtServiceTest.java              6 unit tests
│           │   ├── auth/service/AuthServiceImplTest.java    14 unit tests
│           │   ├── auth/controller/AuthControllerIT.java    11 integration tests
│           │   └── auth/controller/UserControllerIT.java     7 integration tests
│           └── resources/application-test.yml
│
└── frontend/          <- MODULE 1 UI, next response
```

---

## Running it

```bash
# 1. Database
mysql -u root -p -e "CREATE DATABASE hardware_erp CHARACTER SET utf8mb4;"

# 2. A real JWT secret (the default in application.yml is a placeholder)
export JWT_SECRET=$(openssl rand -base64 32)
export DB_USER=root
export DB_PASSWORD=yourpassword

# 3. Run
cd backend
./mvnw spring-boot:run

# 4. Tests
./mvnw test
```

Flyway creates the schema and seeds the four roles. `DataInitializer` then creates
the first owner account **only if the user table is empty**:

```
mobile   : 9999999999   (override with BOOTSTRAP_MOBILE)
password : Owner@123    (override with BOOTSTRAP_PASSWORD)
```

`must_change_password` is set, so the UI forces a change at first sign-in.

Swagger UI: `http://localhost:8080/api/swagger-ui.html`

---

## API endpoints — Module 1

### Public

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/login` | Sign in with mobile **or** email |
| POST | `/api/v1/auth/refresh` | Rotate the token pair |
| POST | `/api/v1/auth/forgot-password` | Request a reset link |
| POST | `/api/v1/auth/reset-password` | Set a new password from a token |

### Authenticated

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/auth/me` | any |
| PUT | `/api/v1/auth/me` | any (own name/email) |
| POST | `/api/v1/auth/change-password` | any |
| POST | `/api/v1/auth/logout` | any |
| POST | `/api/v1/auth/logout-all` | any |
| GET | `/api/v1/users` | `USER_VIEW` — search, filter, paginate, sort |
| GET | `/api/v1/users/{id}` | `USER_VIEW` |
| POST | `/api/v1/users` | `USER_MANAGE` |
| PUT | `/api/v1/users/{id}` | `USER_MANAGE` |
| POST | `/api/v1/users/{id}/reset-password` | `USER_MANAGE` |
| DELETE | `/api/v1/users/{id}` | `USER_MANAGE` — soft delete |
| GET | `/api/v1/roles` | `ROLE_VIEW` |
| GET | `/api/v1/roles/permissions` | `ROLE_VIEW` |
| GET | `/api/v1/roles/{id}` | `ROLE_VIEW` |
| POST | `/api/v1/roles` | `ROLE_MANAGE` |
| PUT | `/api/v1/roles/{id}` | `ROLE_MANAGE` |
| DELETE | `/api/v1/roles/{id}` | `ROLE_MANAGE` |

---

## Design decisions worth knowing

**Permissions, not role names.** `@PreAuthorize` checks `USER_MANAGE`, never
`hasRole('OWNER')`. When the shop owner says "let the accountant do X too",
that becomes a row in `role_permission`, not a code change and a redeploy.
The 26 permission codes already cover Modules 1–10, so the role screen won't
need editing at every release.

**`STAFF` has no `PRODUCT_VIEW_COST`.** Counter staff must not see purchase
price or margin. This is the one permission most likely to be asked for on day
one and it is already wired.

**Refresh tokens are opaque random strings, stored as SHA-256 hashes.** Not
JWTs. A database dump yields nothing usable. Rotation on every use, and **reuse
of a rotated token revokes every session for that user** — the standard signal
that a token was stolen.

**`token_version` on the user row.** Every access token carries it. Bumping it
invalidates all outstanding tokens instantly, which is how password change,
role change, deactivation and logout-everywhere all work without a blacklist.

**Login accepts mobile or email in one field.** Counter staff will not remember
which one they were registered with.

**Wrong password and unknown user return the identical response.** Same for
`forgot-password`, which always reports success. Otherwise these endpoints
become a way to discover which mobile numbers are registered.

**Account lockout:** 5 failed attempts → 15 minutes.

**Sort fields are whitelisted and page size is clamped to 100.** A raw sort
parameter reaching `ORDER BY` is an injection surface; `size=1000000` is a
memory exhaustion surface.

**Users are soft-deleted.** Invoices will reference `created_by` for the life of
the business, so the row must stay resolvable. The last active owner cannot be
deleted, deactivated, or have their role changed.

**Audit writes use `REQUIRES_NEW`** so a logging failure never rolls back the
user's action, and a failed action still leaves the attempt on record.

**BCrypt strength 12** (~250ms/hash). Swap to `Argon2PasswordEncoder` by
changing one bean in `SecurityConfig` if you add BouncyCastle.

---

## Known gaps to close before production

1. **Rate limiting on `/auth/login` and `/auth/forgot-password`.** Per-account
   lockout is in; per-IP throttling is not. Add Bucket4j or do it in Nginx.
2. **HTTPS.** HSTS headers are set but mean nothing over plain HTTP.
3. **`JWT_SECRET` must be a real environment variable.** The default in
   `application.yml` is a placeholder and the app should refuse to start with
   it in a production profile.
4. **Integration tests run on H2 in MySQL mode.** Faster than Testcontainers but
   not the same engine. Move to `MySQLContainer` once Docker is available in CI.
5. **This code has not been compiled.** Maven Central is unreachable from the
   environment it was written in. Package declarations and brace balance are
   verified; expect to fix an import or two on first `mvn compile`.

---

## Next

**Module 1 frontend** — Vite + React + TypeScript + Tailwind + shadcn/ui:
axios client with a refresh-on-401 interceptor and a single-flight refresh queue,
auth store, `ProtectedRoute` and `RequirePermission` guards, forced
password-change gate, and the Login / Forgot / Reset / Profile / Users / Roles
screens.

Then **Module 2: Customer Management**.
