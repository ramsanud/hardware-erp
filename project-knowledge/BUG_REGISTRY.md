# BUG REGISTRY

Every defect found is recorded before it is fixed. Read this file before
generating new code; never reintroduce a listed bug.

| ID | Module | Severity | Status |
|---|---|---|---|
| BUG-AUTH-001 | Authentication | HIGH | Fixed (CR-003) |
| BUG-AUTH-002 | Authentication | HIGH | Fixed (CR-003) |
| BUG-AUTH-003 | Authentication | MEDIUM | Fixed (CR-003) |
| BUG-AUTH-004 | Authentication | HIGH | Fixed (CR-003) |
| BUG-AUTH-005 | Authentication | MEDIUM | Fixed (CR-003) |
| BUG-AUTH-006 | Authentication | HIGH | Fixed (CR-003) |
| BUG-AUTH-007 | Authentication | MEDIUM | Fixed (CR-003) |
| BUG-AUTH-008 | Authentication | HIGH | Fixed |
| BUG-AUTH-009 | Authentication | HIGH | Fixed (CR-014) |
| BUG-AUTH-010 | Authentication / Build | MEDIUM | Fixed |
| BUG-AUTH-011 | Authentication / Database | HIGH | Fixed |
| BUG-SUP-001 | Supplier | MEDIUM | Fixed before commit (CR-015) |
| BUG-SUP-002 | Supplier / Testing | LOW | Fixed |
| BUG-SUP-003 | Supplier / Database | HIGH | Fixed |
| BUG-SUP-004 | Supplier + Authentication / Database | HIGH | Fixed |
| BUG-AUTH-012 | Authentication / Security | CRITICAL | Fixed |
| BUG-ENV-001 | Build | INFO | Closed 2026-08-22 |
| BUG-TEST-001 | Auth / Supplier / Testing | HIGH | Fixed |
| BUG-AUTH-013 | Authentication / Testing | LOW | Fixed |
| BUG-MONEY-001 | Supplier / Product / Display | HIGH | Fixed |
| BUG-BUILD-001 | Build / Testing | HIGH | Fixed |
| BUG-ENV-002 | Build / Testing | INFO | Open |
| BUG-FE-001 | Frontend | HIGH | Fixed (CR-022) |
| BUG-FE-002 | Frontend | MEDIUM | Fixed (CR-026) |
| BUG-FE-003 | Frontend | MEDIUM | Fixed (CR-026) |
| BUG-FE-004 | Frontend | MEDIUM | Fixed (CR-026) |
| BUG-PAY-001 | Payment / Database | HIGH | Fixed (CR-027) |
| BUG-DASH-001 | Dashboard / Database | HIGH | Fixed (CR-027) |
| BUG-AI-001 | AI Assistant / Frontend contract | HIGH | Fixed (CR-027) |
| BUG-SEC-001 | Security / Multi-tenancy | CRITICAL | Fixed (CR-028) |
| BUG-CUST-001 | Customer / Database | HIGH | Fixed (CR-028) |
| BUG-FE-005 | Frontend | MEDIUM | Fixed (CR-028) |
| BUG-SEC-002 | Security / Database | HIGH | Fixed |
| BUG-PROJ-001 | Project / Database | HIGH | Fixed |
| BUG-INV-001 | Inventory / Invoice | HIGH | Fixed |
| BUG-FE-006 | Settings / Frontend | LOW | Fixed |
| BUG-PUR-001 | Purchase Import / Backend | MEDIUM | Fixed |
| BUG-PUR-002 | Purchase Import / Backend / Database | MEDIUM | Fixed |
| BUG-PUR-003 | Purchase Import / Backend | LOW | Fixed |
| BUG-PUR-004 | Purchase Import / Security | CRITICAL | Fixed |
| BUG-INV-002 | Invoice / Backend | MEDIUM | Fixed (CR-036) |
| BUG-EXP-001 | Expense / Backend / Database | MEDIUM | Fixed (CR-036) |
| BUG-AUTH-014 | Authentication / Testing | MEDIUM | Fixed (CR-045) |
| BUG-FE-007 | Product + Expense / Frontend | MEDIUM | Fixed (CR-036) |
| BUG-LAB-001 | Labour / Database | HIGH | Fixed (CR-036) |
| BUG-LAB-002 | Labour / Frontend | HIGH | Fixed (CR-037) |
| BUG-LAB-003 | Labour / Backend | MEDIUM | Fixed (CR-037) |
| BUG-LAB-004 | Labour / Backend | MEDIUM | Fixed (CR-037) |
| BUG-LAB-005 | Labour / Frontend | MEDIUM | Fixed (CR-037) |
| BUG-LAB-006 | Tenant provisioning / Security | HIGH | Fixed (CR-037) |
| BUG-UI-001 | Layout / Frontend | MEDIUM | Fixed (CR-037) |
| BUG-ENV-003 | Ops / Health check | HIGH | Fixed (CR-038) |

---

**BUG-AUTH-001 — Sensitive data in JWT payload**
Layer: Backend / Security.
The access token carried `name`, `role` and the full permission array. A JWT is
signed, not encrypted — anyone holding it reads the payload. It also meant a
revoked permission stayed live until token expiry.
Root cause: convenience over least privilege.
Fix: claims reduced to `iss`, `sub`, `tv`, `iat`, `exp`. Authorities loaded from
the database per request.
Regression test: `JwtServiceTest.accessTokenCarriesNoPersonalData`.

**BUG-AUTH-002 — Logout signed the user out of every device**
Layer: Backend.
`logout` and `logout-all` behaved identically in effect because the design did
not distinguish them; closing the counter terminal killed the owner's phone session.
Fix: `logout` revokes only the presented refresh token and leaves `token_version`
untouched. `logout-all` revokes all and bumps `token_version`.
Regression test: `AuthControllerIT.logoutDoesNotAffectOtherSessions`.

**BUG-AUTH-003 — `@Version` on BaseEntity applied to every future entity**
Layer: Database / JPA.
Optimistic locking forced onto append-only tables (stock ledger, audit log) adds
a column and a WHERE clause for rows that are never updated, and turns harmless
concurrent inserts into spurious 409s.
Fix: `BaseEntity` carries audit columns only. `@Version` declared per entity
where concurrent edits are real (`app_user`, `role`).

**BUG-AUTH-004 — Bootstrap silently created an owner account**
Layer: Security / Configuration.
"Create an owner if the user table is empty" fires in production, with a default
password, and logs the mobile number at WARN.
Fix: `APP_BOOTSTRAP_ENABLED` defaults to false; password must be explicitly
configured and passes a strength check or the application refuses to start;
nothing sensitive is logged.
Regression test: `BootstrapOwnerInitializerTest`.

**BUG-AUTH-005 — Audit log could not answer "why did this fail"**
Layer: Backend.
No `success`, no `failure_reason`, no `user_agent`, no `request_id`. A failed
login and a successful one were indistinguishable apart from the action string.
Fix: fields added to `security_audit_log`; table renamed to separate security
events from future business history.

**BUG-AUTH-006 — No rate limiting on authentication endpoints**
Layer: Security.
Per-account lockout does not stop credential stuffing across many accounts, and
`forgot-password` could be used to flood a mailbox.
Fix: Bucket4j + Caffeine filter on `/auth/login`, `/auth/forgot-password`,
`/auth/reset-password`, `/auth/refresh`, keyed by IP and by identifier.
Returns 429 with `Retry-After`.
Regression test: `RateLimitIT.loginIsRateLimitedPerIp`.

**BUG-AUTH-007 — Integration tests ran on H2, production on MySQL**
Layer: Testing.
H2 in MySQL mode does not reproduce MySQL locking, CHECK constraint behaviour,
collation-driven uniqueness, or `ON UPDATE CURRENT_TIMESTAMP`.
Fix: Testcontainers MySQL 8 with `@ServiceConnection`; Flyway runs the real
migration; H2 dependency removed.

**BUG-AUTH-008 — Security filters registered twice**
Layer: Backend / Spring configuration.
`JwtAuthenticationFilter` and `RateLimitFilter` were annotated `@Component`.
Spring Boot auto-registers any `OncePerRequestFilter` bean into the servlet
filter chain, so each filter ran **twice per request** — once in the servlet
chain and once inside the Security chain — and also ran on paths the Security
chain excludes. For the rate limiter this consumes two tokens per request,
halving every configured limit. For the JWT filter it doubles the per-request
database read.
Root cause: `@Component` on a filter is not the same as adding it to the
Security chain; Boot's `ServletContextInitializer` picks it up independently.
Fix: `@Component` removed from both. They are now declared as plain `@Bean`
methods in `SecurityConfig` and added explicitly with `addFilterBefore` /
`addFilterAfter`. `RequestCorrelationFilter` keeps `@Component` deliberately —
it must run for every request including Swagger and actuator.
Regression test: `SecurityFilterRegistrationTest` asserts each filter appears
exactly once in the effective chain, and `RateLimitIT` asserts the Nth request
is the first to be rejected (it would fail at N/2 with the bug present).

**BUG-AUTH-009 — Case-insensitive uniqueness lost in the PostgreSQL migration**
Layer: Database.
Under MySQL the tables used `utf8mb4_0900_ai_ci`, an accent- and
case-insensitive collation. `UNIQUE (email)` therefore rejected
`Owner@shop.in` when `owner@shop.in` already existed, and `UNIQUE (role_name)`
behaved the same way. PostgreSQL's default collation is case-sensitive, so a
literal port of the DDL would have accepted both rows.

Why it matters: `UserRepository.existsByEmailIgnoreCase` and
`RoleRepository.existsByNameIgnoreCase` would still block the duplicate at the
service layer, but the database guarantee behind them would be gone. Any path
that bypassed the service — a future bulk import, a data fix, a second code
path — could create two accounts that both match the same login lookup. The
`findByIdentifier` query uses `lower(u.email) = lower(:identifier)` and would
then match two rows and throw.

Root cause: assuming DDL ports across engines when collation semantics do not.
Fix: functional unique indexes in V1 —
`CREATE UNIQUE INDEX uk_user_email_lower ON app_user (lower(email));`
`CREATE UNIQUE INDEX uk_role_name_lower ON role (lower(role_name));`
The plain `UNIQUE (email)` constraint is dropped, since the functional index
supersedes it. PostgreSQL treats NULLs as distinct in both, so users without an
email address are unaffected.
Regression test: `UserControllerIT.duplicateEmailDiffersOnlyByCase`.
Status: Fixed.

**BUG-SUP-001 — Business changes were being written to the security audit log**
Layer: Backend / architecture.
The first draft of `SupplierServiceImpl` recorded supplier creation through
`SecurityAuditService` using `AuditAction.USER_CREATED`. Wrong twice: the action
name is a lie, and `DATABASE_REGISTRY.md` already states that
`security_audit_log` holds security events only.
Impact if shipped: by Module 11 the security log would be full of invoice edits
and nobody could find a failed login in it.
Root cause: reaching for the nearest existing service rather than the correct
one.
Fix: CR-015 - new `activity_log` table (V3) and `common/activity/
ActivityLogService`, used by every business module. `SupplierServiceImpl`
rewired; it diffs before and after and records only fields that changed.
Regression test: `SupplierServiceImplTest.writesActivityLog` asserts the
activity log is called; the security log is not injected into the service at
all, so it cannot be used by accident.
Status: Fixed before commit.

**BUG-AUTH-010 — Rate-limit filter did not compile**
Layer: Backend / Build.
First real `mvn compile` (BUG-ENV-001) surfaced two defects in the Bucket4j
rate-limit filter, invisible to static checks because they require type
resolution:
1. `RateLimitService.Decision` is a record with boolean component `allow`,
   which auto-generates an instance accessor `allow()`. The class also
   declared a static factory `Decision.allow()` with the same name — a
   static and instance method cannot share a name and signature in one
   class, so the record's accessor failed to generate at all. That failure
   cascaded into a second, confusing error at the call site
   (`RateLimitFilter` calling `.allowed()`, which didn't exist either way).
2. `RateLimitFilter` referenced `HttpServletResponse.SC_TOO_MANY_REQUESTS`,
   which does not exist on the jakarta Servlet API — 429 postdates the
   constant set the interface defines.
Root cause: code written and reviewed without ever being compiled.
Fix: record component renamed `allow` → `allowed`, so the generated
accessor (`allowed()`) matches what the filter already called and no longer
collides with the static factory `Decision.allow()`. The status constant
replaced with `HttpStatus.TOO_MANY_REQUESTS.value()`.
Regression test: covered by `RateLimitIT`, which cannot pass without a
successful compile.

**BUG-AUTH-011 — `token_hash` declared CHAR in the database, VARCHAR in the entity**
Layer: Database / JPA.
`V1__auth_schema.sql` declares `token_hash CHAR(64)` in both `refresh_token`
and `password_reset_token`. Both `RefreshToken.tokenHash` and
`PasswordResetToken.tokenHash` are mapped `@Column(length = 64)` with no
`columnDefinition` override, which Hibernate resolves to `VARCHAR(64)`.
`ddl-auto: validate` therefore refuses to start the application — this is
exactly the class of drift that setting stops silent `update` for.
Schema validation aborts on the first mismatched table, so only
`password_reset_token` appeared in the stack trace; `refresh_token` carries
the identical defect and would have failed next.
Root cause: migration and entity written independently, never checked
against each other because the application had never started.
Fix: `V4__fix_token_hash_column_type.sql` adds
`ALTER TABLE ... ALTER COLUMN token_hash TYPE VARCHAR(64)` for both tables,
per the hard rule against editing an applied migration. A SHA-256 hex digest
is always exactly 64 characters, so no stored value changes shape — only the
declared type now matches what the entity has always expected.
Regression test: every `@SpringBootTest` integration test fails to load the
context while this is present, so the full IT suite is the regression check.

**BUG-SUP-002 — Test used a `JsonNode` method not in the resolved Jackson version**
Layer: Testing.
`SupplierControllerIT.onePrimaryContact` called `JsonNode.valueStream()`,
added in jackson-databind 2.19. The version resolved via the Spring Boot BOM
is 2.18.2, so this failed test compilation the first time it was ever built.
Fix: replaced with `StreamSupport.stream(node.spliterator(), false)`, which
works against any Jackson version since `JsonNode` implements `Iterable`.

**BUG-SUP-003 — `state_code` and `pincode` declared CHAR in the database, VARCHAR in the entity**
Layer: Database / JPA.
Same defect as BUG-AUTH-011, found immediately after it in the same first
real run: `V2__supplier_schema.sql` declares `state_code CHAR(2)` and
`pincode CHAR(6)`, but `Supplier.stateCode` / `Supplier.pincode` are mapped
`@Column(length = N)` with no `columnDefinition`, which Hibernate resolves
to VARCHAR. Every other column in V1-V3 was checked against its entity after
this was found; these two were the only other instances.
Fix: `V5__fix_supplier_column_types.sql` adds
`ALTER TABLE supplier ALTER COLUMN ... TYPE VARCHAR(n)` for both.
Regression test: `SupplierControllerIT` and `SupplierServiceImplTest` fail to
load the Spring context while this is present.

**BUG-SUP-004 — PostgreSQL rejected optional search filters as "function lower(bytea) does not exist"**
Layer: Database / JPA (Supplier and Authentication).
`SupplierRepository.search` and `UserRepository.search` both filter on a
nullable text parameter using the pattern `(:search is null or
lower(field) like lower(concat('%', :search, '%')) or ...)`. Against
PostgreSQL, the bare `:search is null` comparison gives Hibernate no type
context, and it binds that parameter as `bytea` rather than text; the later
`lower()`/`concat()` calls then fail with `function lower(bytea) does not
exist`, a 500 on every call to `GET /v1/suppliers` and `GET /v1/users` with
no error-free path. `:city` in `SupplierRepository.search` has the identical
shape and the identical defect.
Root cause: the query pattern is a known Hibernate/PostgreSQL parameter type
inference gap - it worked in the original MySQL-targeted design and was
carried over by CR-014 without being exercised against real PostgreSQL,
since the application had never run.
Fix: `cast(:search as string)` (and `cast(:city as string)`) at every
occurrence, forcing an explicit text bind type. `SecurityAuditLogRepository`
and `ActivityLogRepository` use the same `:param is null or ...` shape but
never pass those parameters through a text function, so they were checked
and left alone.
Regression test: `SupplierControllerIT.searchAcrossFields` and any
`UserControllerIT` search test exercise this path against a real
PostgreSQL container and would fail with the bug present.

**BUG-AUTH-012 — Login and forgot-password could never succeed once rate limiting was added**
Layer: Backend / Security.
`RateLimitFilter` reads the request body early, via
`ContentCachingRequestWrapper`, to extract the `identifier` field for the
per-account rate-limit key. `ContentCachingRequestWrapper` caches bytes as
something *downstream* reads them - it does not replay them on a second
`getInputStream()` call. Reading the body in the filter, before
`chain.doFilter`, permanently empties the stream; the controller's `@Valid
@RequestBody` deserialization then found nothing and every login attempt -
correct credentials included - returned `400 MALFORMED_REQUEST`. This is not
a rate-limit edge case: it broke `/auth/login` and `/auth/forgot-password`
unconditionally, on every request, from the first real run.
Root cause: `ContentCachingRequestWrapper` was reached for as the obvious
"cache the body" type without checking that its caching model requires the
body to be read downstream first, not by the filter itself.
Fix: a small `CachedBodyHttpServletRequest` wrapper that reads the body once
into a `byte[]` and returns a fresh `ServletInputStream` over it on every
call to `getInputStream()`, so the filter's own read and the controller's
later read are both served from the same buffered copy. Only applied to
`LOGIN` and `FORGOT`, the two paths that key by identifier; `RESET` and
`REFRESH` pass the original, unwrapped request through.
Regression test: `AuthControllerIT` logs in over HTTP through the full
filter chain, so it cannot pass while this is present; it had simply never
been run.

**BUG-ENV-001 — Code has never been compiled**
Layer: Build.
Maven Central was unreachable from the original authoring environment, so
`mvn compile`, `mvn test` and Testcontainers had never been executed. Static
checks only: package/path agreement, brace balance, registry drift,
entity↔migration column agreement.
Status: **Closed 2026-08-22.** A working environment (Maven Central, Docker
Desktop, a real JDK) compiled the backend and ran it against PostgreSQL 16
for the first time. As expected, the first real build and run surfaced six
defects invisible to static checks - BUG-AUTH-010 through BUG-AUTH-012 and
BUG-SUP-002 through BUG-SUP-004 above - all now fixed. `mvn spring-boot:run`
starts cleanly, and `/v1/auth/login`, `/v1/suppliers` (with search) and
`/v1/users` (with search) were exercised directly and returned correct
responses. `mvn clean verify` (the full 184-test suite) has not yet been
run cleanly to completion - see "Pending" in `RESUME_POINT.md`.
Known non-bug: `/actuator/health` reports `DOWN` locally because Spring
Boot's mail health indicator tries to reach `smtp.gmail.com` and no SMTP
credentials are configured - expected, since `app.mail.log-links-when-
unconfigured` is deliberately `true` in the dev profile so reset links print
to the console instead.

**BUG-TEST-001 — Unit test suite did not compile after the CR-016 tenant retrofit**
Layer: Backend / Testing.
`RoleServiceImplTest`, `UserServiceImplTest` and `SupplierServiceImplTest`
were never updated when CR-016 changed repository method signatures
(`existsByCode` -> `existsByCodeAndTenantId`, `lockActiveOwners()` ->
`lockActiveOwners(tenantId)`, `findById` -> `findByIdAndTenantId`, etc.).
`mvn clean verify` had not been run since the retrofit, so this went
undetected (see BUG-ENV-001 note: 184 written, 0 confirmed passing).
Root cause: the retrofit touched production repositories and their callers
but the accompanying unit tests were not part of the same change.
Fix: updated every stub in all three test classes to the tenant-scoped
signatures, and added an authenticated `SecurityContextHolder` principal
(tenant id 1) to each `@BeforeEach`/`@AfterEach`, since every service method
now calls `SecurityUtils.requireCurrentTenantId()` and throws
`AuthException` with no authentication set.
Regression test: existing suite now compiles and 89/89 non-Docker-dependent
tests pass.

**BUG-AUTH-013 — Mockito `UnfinishedStubbingException` in `AuthServiceImplTest.deactivatedUser`**
Layer: Backend / Testing (pre-existing, unrelated to CR-016).
`storedFor(raw)` - which calls the real `jwtService.hashToken()` - was
invoked inline inside `.thenReturn(storedFor(raw))`, after
`when(refreshTokenRepository.findByTokenHash(...))` had already opened an
ongoing stub. Mockito interprets the nested mock interaction as an attempt
to stub a second call before the first is finished. The other three tests
in the same class (`rotation`, `reuseDetection`, `expired`) already used the
correct pattern of assigning `storedFor(raw)` to a local variable first.
Fix: hoisted the call the same way.
Regression test: `AuthServiceImplTest$Refresh.deactivatedUser` now passes.

**BUG-MONEY-001 — Indian digit grouping was completely non-functional in every money display**
Layer: Backend / Display. Severity HIGH because it violates PROJECT_SKILLS
lesson 29 on every screen that shows an amount.
`SupplierMapper.rupees()` and `ProductMapper.rupees()` both used
`new DecimalFormat("##,##,##,##0.00")`, expecting the pattern's multiple
grouping separators to produce Indian lakh/crore grouping (5,00,000.00).
`java.text.DecimalFormat` on JDK 21 does not support a repeating secondary
grouping size from a pattern string - verified by direct reproduction: the
same pattern, and `NumberFormat.getNumberInstance(Locale.forLanguageTag(
"en-IN"))`, both produced plain 3-digit Western grouping ("500,000.00").
This had never been caught because the money-formatting unit test could not
run until BUG-TEST-001 was fixed (the class didn't compile).
Root cause: an incorrect assumption about `DecimalFormat` pattern
capabilities, uncaught because the test that would have caught it never ran.
Fix: added `com.hardware.erp.common.util.IndianCurrencyFormat`, a
BigDecimal/string-based formatter that groups the last 3 digits then
repeating groups of 2, verified against 500,000 / 50,00,000 / 1,23,45,678 -
type cases. Both mappers now delegate `rupees()` to it; the broken
`DecimalFormat` field and now-unused imports were removed from both.
Regression test: `SupplierServiceImplTest$Money.indianGrouping` now passes
(previously failed: expected "5,00,000.00", was "500,000.00").

**BUG-BUILD-001 — Every `*ControllerIT` integration test has never executed, in any build**
Layer: Build. Severity HIGH - this is the majority of the "184 tests
written" figure quoted throughout the registries (`AuthControllerIT`,
`RateLimitIT`, `RoleControllerIT`, `UserControllerIT`,
`SupplierControllerIT`).
`pom.xml` had no `maven-failsafe-plugin` bound to the `integration-test`/
`verify` phases. Maven Surefire's default include pattern is
`**/*Test.java` / `**/*Tests.java` / `**/*TestCase.java` only - it does not
match `**/*IT.java`, which is Failsafe's convention. `mvn clean verify` and
`mvn test` were both silently skipping every `*IT` class with no warning,
error, or "skipped" line - they simply never appeared in the test run at
all.
Root cause: the Testcontainers-based integration tests were written to the
Failsafe naming convention, but the plugin that runs that convention was
never added to `pom.xml`.
Fix: added `maven-failsafe-plugin` bound to `integration-test` + `verify`.
Verified by invoking the Failsafe goal directly
(`org.apache.maven.plugins:maven-failsafe-plugin:3.5.2:integration-test`):
all 5 `*IT` classes are now discovered and run, each failing identically at
Testcontainers class-init on this machine (see BUG-ENV-002) rather than
being silently absent.
Regression test: none needed - this is build wiring, not application code;
the presence of `[INFO] Running com.hardware.erp.auth.controller.*IT` in
`mvn verify` output is itself the check.

**BUG-ENV-002 — Testcontainers cannot reach Docker Desktop on this machine (open)**
Layer: Build / Testing environment, not application code.
Docker Desktop 4.86 (engine API 1.55) is running and healthy - `docker ps`,
`docker version`, and `docker --context default|desktop-linux ps` all work
correctly from the CLI. But testcontainers-java (tried 1.20.4 and the
latest 1.21.3) fails every strategy
(`EnvironmentAndSystemPropertyClientProviderStrategy`,
`NpipeSocketClientProviderStrategy`) against both the `docker_engine` and
`dockerDesktopLinuxEngine` named pipes, always with an identical
`BadRequestException (Status 400)` carrying an empty/zeroed `/info` body.
This blocks every class extending `AbstractIntegrationTest` -
`PermissionCodeConsistencyTest`, `SecurityFilterRegistrationTest`, and all
5 `*ControllerIT` classes (see BUG-BUILD-001, now correctly wired to run).
Status: **Open - environment issue, not a code defect.** Not resolved by:
bumping testcontainers to the newest released version (1.21.3), pointing
`~/.testcontainers.properties` at either named pipe Docker Desktop exposes,
or confirming the daemon itself is reachable via the native CLI. Likely an
API-version negotiation gap between docker-java (used inside
testcontainers) and this specific, very recent Docker Desktop build. Needs
either a Docker Desktop version change or exposing the daemon over TCP -
both are host-machine changes outside this repository's control and were
not made without the owner's decision.
Regression test: none - tracked here so the next session does not
re-diagnose the same dead end. `mvn clean verify` will show exactly this
error for all 7 Docker-dependent classes until resolved; every other test
(89/89) passes cleanly.

**BUG-FE-001 — `/forgot-password` (and later the Dashboard) rendered as a blank white page — RESOLVED**
Layer: Frontend.
Originally logged as "not reproduced in code" after a static audit found
nothing wrong with `routes/index.tsx`, `ForgotPasswordPage.tsx`, or the
backend endpoint. The real cause surfaced later the same day when the
identical blank-page symptom recurred on the new Dashboard, this time with
the browser console attached: `Uncaught Error: Slot failed to slot onto
its children. Expected a single React element child or Slottable.`
Root cause: `shared/components/ui/button.tsx`'s `Button` always rendered
`{loading ? <Loader2/> : null}{children}` inside `Comp`. When `asChild` is
true, `Comp` is Radix's `Slot`, which requires *exactly one* element child
- the `null` sibling turned it into an array of two, and `Slot` throws,
unmounting the entire React tree above it (not just the button). Every
`<Button asChild>` in the app was affected - both `ForgotPasswordPage`'s
"Back to sign in" links and the Dashboard's "New invoice" button use it -
which is why the failure looked identical in two unrelated places days
apart.
Fix: when `asChild`, `Comp` now receives `children` with no injected
sibling; the loading-spinner wrapper only applies to a real `<button>`.
Regression test: none added (a unit test would need to mount `Slot`
against a router `<Link>`, which isn't exercised by any existing frontend
test infrastructure) - covered instead by a headless-Chrome render check
of every route using `asChild` after the fix (see CR-022 in
RESUME_POINT.md).

**BUG-FE-002 — Sidebar shop name/logo and header avatar never reflected a Settings/Profile save**
Layer: Frontend.
`SidebarBrand` fetched `/v1/settings/brand` once in a mount-only `useEffect`
with no refresh trigger; `AppLayout`'s header user-menu never fetched or
rendered the avatar image at all, showing initials unconditionally
regardless of upload state. Since `AppLayout`/`Sidebar` stay mounted across
in-app navigation, a shop name/logo change saved on the Shop Settings page,
or a photo uploaded on the Profile page, never appeared anywhere else in
the running session - only a hard reload picked it up.
Root cause: each consumer owned its own fetch-once state with no shared
source of truth to invalidate.
Fix: `frontend/src/layouts/AppChromeProvider.tsx`, a context wrapping
`AppLayout` that both `SidebarBrand` and the header avatar read from;
`ShopSettingsPage`/`ProfilePage` call its `refreshBrand()`/
`bumpAvatarVersion()` after a save so every consumer re-fetches
immediately.
Regression test: none added (no frontend component test infrastructure
exists in this codebase to mount `AppLayout` and assert a re-render) -
verified manually: save Settings, confirm the Sidebar's name/logo update
without a reload; upload a Profile photo, confirm the header avatar
updates without a reload.

**BUG-FE-003 — A numeric field defaulting to 0 showed "0100" instead of "100" as the user typed**
Layer: Frontend.
Every `type="number"` react-hook-form field whose default value is 0
(Product's GST rate/prices/stock levels, and several others) never
stripped the leading zero as the user typed - `<input type="number">`
only ever does this, inconsistently, on blur in some browsers, never live.
Typing "1" then "0" then "0" into a field showing "0" produced "0100".
Root cause: relying on native `type="number"` formatting instead of
controlling the displayed string.
Fix: `frontend/src/shared/components/ui/number-input.tsx`, a controlled
`NumberInput` (renders as `type="text"` with `inputMode="decimal"`) that
strips a leading zero itself, shows genuinely empty instead of forcing
"0" into view, and commits 0 for a cleared field. Applied via `Controller`
across Product, Supplier, Customer, Invoice and Quotation forms.
Regression test: none added (no frontend component test infrastructure
exists to simulate keystrokes) - verified manually against the reported
Product "Selling price" field and the other converted fields.

**BUG-FE-004 — A manually-typed lowercase product/category/brand code was silently rejected, not saved**
Layer: Frontend.
`codeRules` in `frontend/src/modules/product/validation/schemas.ts`
validated `productCode`/`categoryCode`/`brandCode` against a regex
requiring uppercase, but never transformed the input to uppercase first -
unlike `gstNo`/`panNo`/`bankIfsc` elsewhere, which already `.toUpperCase()`
before their own regex check. Typing a lowercase code (e.g. "prd-0002")
failed validation outright; the record was never created, which read as
"this product isn't in the database" with no indication why.
Root cause: `codeRules` was written before the `.toUpperCase()` pattern
was established elsewhere and never brought in line with it.
Fix: `codeRules` now `.toUpperCase()`s before the regex check, and
`className="uppercase"` was added to the Product/Category/Brand code
inputs for live visual feedback, matching the existing GSTIN/PAN/IFSC
fields.
Regression test: none added (schema-level, no frontend unit test
infrastructure exists for validation schemas in this module) - verified
manually: typing "prd-0002" now saves as "PRD-0002" instead of failing
validation.

**BUG-PAY-001 — `GET /v1/payments` 500'd whenever the method or date filter was left blank**
Layer: Backend / Database. Found by live-clicking the new Payment list page
against real data (CR-027 session) - the exact BUG-SUP-004 class of defect,
reintroduced in a brand-new query written after that bug was already fixed
and documented.
`PaymentRepository.search()`'s `paymentMethod`/`fromDate`/`toDate` filters
used the bare `(:param is null or ...)` pattern with no cast on the
null-check side. PostgreSQL's prepared-statement parameter type inference
cannot resolve a parameter's type from a bare `? is null` check alone, so
`could not determine data type of parameter $N` on every call where any of
those three filters was left at its default ("All methods"/"All time") -
i.e. on every normal page load, since the filters default to unset.
`search` itself already used the correct `cast(:search as string)` pattern,
which is exactly why it wasn't the parameter that failed.
Root cause: the established fix for this exact class of bug
(BUG-SUP-004) wasn't applied to two new parameters (`paymentMethod`) and a
new type (`LocalDateTime`, for `fromDate`/`toDate`) in the same query -
`InvoiceRepository`'s equivalent date filters use `LocalDate`, which
PostgreSQL happens to infer correctly, masking that the pattern itself
still needs the cast for any type that doesn't.
Fix: `cast(:paymentMethod as string)` and `cast(:fromDate as timestamp)` /
`cast(:toDate as timestamp)` added to every null-check in
`PaymentRepository.search()`.
Regression test: none added at the repository/PostgreSQL level (would need
a Testcontainers-backed test, blocked by BUG-ENV-002 in this environment) -
verified live: `/payments` with all filters at their default now returns
the tenant's 4 real payments instead of a 500, confirmed via a real browser
session against the live app.

**BUG-DASH-001 — Dashboard's Total Sales/Today's Sales/Outstanding Balance cards 500'd with real invoice data**
Layer: Backend / Database. Found live-clicking the Dashboard in the same
session, immediately after BUG-PAY-001 - the Dashboard had apparently never
been exercised with more than a handful of test invoices since it shipped
in CR-023.
`InvoiceRepository.tenantSalesSummary()` is a two-column JPQL aggregate
(`select coalesce(sum(...),0), coalesce(sum(...),0) from Invoice ...`, no
`GROUP BY`) declared to return `Object[]` directly. Spring Data/Hibernate
actually returns a single-element `List<Object[]>` for this query shape;
assigning that list straight into an `Object[]` variable meant
`totals[0]` was the *whole row* (itself an `Object[]`) and `totals[1]`
didn't exist at all - `((Number) totals[0]).longValue()` then threw
`ClassCastException: class [Ljava.lang.Object; cannot be cast to class
java.lang.Number`.
Root cause: an incorrect assumption about how Spring Data unwraps a
single-row multi-column `@Query` projection - zero test coverage existed
for `DashboardServiceImpl` (confirmed: no `dashboard` test package existed
at all), so this was never caught.
Fix: `tenantSalesSummary()`'s declared return type changed to
`List<Object[]>`; `DashboardServiceImpl.salesSummary()` now takes `.get(0)`
(the query is an unconditional aggregate, always exactly one row).
Regression test: `DashboardServiceImplTest` (new file, 2 tests) - asserts
the real-data shape unwraps to correct totals, and that a zero-invoice shop
still returns a valid (zero) summary rather than throwing.

**BUG-AI-001 — AI assistant chat always failed in the browser despite the API call itself succeeding**
Layer: Backend/Frontend contract. Found live-testing the AI widget in the
same session - a textbook case of "verify at three levels: API responds
correctly -> the browser shows it", where skipping the third level would
have shipped this silently.
`AiChatController.chat()` returned a bare `AiChatResponse` record instead
of wrapping it in `ApiResponse.ok(...)`, the envelope every other
controller in this codebase uses. The frontend's `apiPost()` helper
unconditionally unwraps `response.data.data` (`ApiResponse<T>`'s `data`
field); against the un-enveloped body `{"reply": "..."}`, `data.data` was
`undefined`, and `const { reply } = await aiChatService.chat(...)` threw a
plain `TypeError` destructuring it - caught by the widget's generic
catch-all and shown as "Something went wrong. Please try again.", which
looked identical to a real backend failure. A raw HTTP capture (request
succeeded, 200, correct body) was needed to tell the two apart - the
browser-visible symptom alone pointed at the wrong layer.
Root cause: `AiChatController` was written without checking the response
envelope convention every sibling controller already follows.
Fix: `chat()` now returns `ApiResponse<AiChatResponse>` via
`ApiResponse.ok(...)`, matching the rest of the API.
Regression test: none added at the HTTP-contract level (would need a
`@SpringBootTest` MockMvc test - `AiChatServiceTest` already covers the
service logic beneath the controller, which was never the broken layer) -
verified live: the widget now shows the real assistant reply text in its
own chat bubble instead of a generic error.

**BUG-SEC-001 — `GET /v1/security-audit-logs` returned every tenant's security events, not just the caller's**
Layer: Backend / Multi-tenancy. Severity CRITICAL — found while carrying
out CR-028's explicit instruction to "fix the security log" and to check
cross-tenant isolation "very carefully because it's data oriented", via a
real second tenant created through the live API (not a code-only review).
`SecurityAuditLogRepository.search()` had no `tenant_id` filter of any
kind — its JPQL selected from `SecurityAuditLog` with only
`userId`/`action`/date-range predicates, none of them tenant-scoped.
`security_audit_log` itself was correctly designed with no `tenant_id`
column (per CR-016, it scopes through `user_id`, the same reasoning
applied to `refresh_token`/`activity_log`), but nothing at the query layer
ever joined back through `user.tenant_id` to enforce that scope — every
tenant's OWNER could see every other tenant's login attempts, password
resets and role changes by calling an endpoint they were already
authorized to use for their own shop.
Root cause: `security_audit_log` was deliberately built without its own
`tenant_id` column, correctly, but the repository query written against
it was never updated to join through `User.tenant.id` to recover that
scope — the omission was structural (a missing join), not a typo in an
existing filter, so it wouldn't have been caught by the BUG-SUP-004 class
of "add a cast" fix.
Fix: `search()` gained a new first parameter, `tenantId`, and the JPQL
gained `a.userId in (select u.id from User u where u.tenant.id =
:tenantId)` as its first AND-clause;
`SecurityAuditQueryServiceImpl.search()` now sources it from
`SecurityUtils.requireCurrentTenantId()`, never from client input, per
CLAUDE.md's rule for every tenant-owned query.
Regression test: `SecurityAuditQueryServiceImplTest` (new, 2 tests) —
asserts the tenant id is threaded into the repository call via
`ArgumentCaptor`, and that a tenant with no events gets back a clean empty
page rather than another tenant's data. Also verified live: created a real
second tenant ("Rival Hardware Co") via the actual registration/API path,
logged in as its owner, confirmed `GET /v1/security-audit-logs` no longer
returns tenant 1's events and vice versa.

**BUG-CUST-001 — `GET /v1/customers/{id}/financial-summary` 500'd for every real customer**
Layer: Backend / Database. Reported by the user live-clicking a Customer
detail page after CR-028 shipped (console showed three repeated 500s on
`/v1/customers/6/financial-summary`) - the exact BUG-DASH-001 class of
defect, a sibling method in the same repository that BUG-DASH-001's fix
never touched.
`InvoiceRepository.customerFinancialSummary()` is a four-column JPQL
aggregate (`select count(i), coalesce(sum(...),0) x3 from Invoice ...`, no
`GROUP BY`) declared to return `Object[]` directly - Spring Data/Hibernate
actually returns a single-element `List<Object[]>` for this query shape, so
`CustomerServiceImpl.financialSummary()` assigning that list straight into
an `Object[]` variable made `invoiceAggregate[0]` the whole row (itself an
`Object[]`) and every other index out of bounds, throwing on the very first
field access.
Root cause: `tenantSalesSummary()`, right below this method in the same
file, already carries BUG-DASH-001's fix (`List<Object[]>`) - this sibling
method predates that fix (built in CR-023, before CR-027's Dashboard round
found the pattern) and was never brought in line with it, because the
Customer financial summary was never live-clicked with real invoice data
until now.
Fix: `customerFinancialSummary()`'s declared return type changed to
`List<Object[]>`; `CustomerServiceImpl.financialSummary()` now takes
`.get(0)`, matching `DashboardServiceImpl.salesSummary()`'s existing fix.
Regression test: none added at the repository/PostgreSQL level (would need
a Testcontainers-backed test, blocked by BUG-ENV-002 in this environment) -
verified live: `GET /v1/customers/6/financial-summary` returns 200 with the
correct zeroed summary instead of a 500, and the Customer detail page in a
real browser session renders the Financial Summary card without an error
state.
**Lesson**: this is the second time this exact JPQL-projection pattern has
caused a 500 (`tenantSalesSummary` in BUG-DASH-001, now
`customerFinancialSummary`) - any future multi-column, no-`GROUP-BY`
`@Query` aggregate must be declared `List<Object[]>`, never bare `Object[]`,
as a standing rule, not case-by-case.
**Codebase-wide audit run after this fix**: grepped every `@Query` in
`backend/src/main/java` for a multi-column select with no `GROUP BY`
(JPQL and native). Only two such queries exist anywhere in the backend -
`tenantSalesSummary` and `customerFinancialSummary`, both in
`InvoiceRepository`, both now fixed. Every native `@Query` elsewhere
(`Customer`/`Supplier`/`Product`/`Brand`/`Category`/`Quotation`/`Invoice`
code-number generators) returns a single scalar `int`, which has no
`Object[]`-unwrapping hazard at all. No other latent instance of this bug
class exists as of this audit (2026-08-23).

**BUG-FE-005 — Changing the subscription plan from the quick-select control on Shop Settings never reached the AI assistant widget**
Layer: Frontend. Reported by the user: changed the plan to Max, the "Max"
badge and dropdown updated correctly on the Settings page itself, but the
AI assistant widget still showed "AI assistant is a Max plan feature" -
exactly the BUG-FE-002 pattern from CR-026 (a Settings save not reaching
`AppChromeProvider`, the single source the Sidebar/avatar/AI widget all
read from), reintroduced in a second write path.
`ShopSettingsPage.tsx` has two ways to change the subscription tier: the
full edit-mode form's Save button (which already called `refreshBrand()`
after saving), and a separate `changeTier()` function behind the read-only
view's quick Select control on the `SubscriptionPlanCard` - built
"independent of the main edit form... so switching plans never accidentally
saves an unrelated unfinished edit" (see its own code comment), which
correctly called `settingsService.update()` and updated the page's own
local `settings` state, but never called `refreshBrand()`. Since
`AiChatWidget` reads `subscriptionTier` from `AppChromeProvider`, not from
`ShopSettingsPage`'s local state, the widget kept showing whatever tier was
loaded when the page first mounted.
Root cause: `AppChromeProvider` was introduced in CR-026 specifically to
fix this class of bug for Sidebar/avatar, but a second write path added
later (the plan quick-select, CR-027) was never audited against it - the
same gap BUG-FE-002 closed for two call sites reopened for a third.
Fix: `changeTier()` now calls `await refreshBrand();` after a successful
save, identical to the full edit-form's Save handler.
Regression test: none added (no frontend component test infrastructure
exists in this codebase, same gap noted for BUG-FE-002/003/004) - verified
live in a real browser session: flipped the plan Free -> confirmed the AI
widget shows the gated message with no page reload -> flipped to Max ->
confirmed the widget immediately shows the real chat input, still with no
reload.
**Lesson**: every future write path that changes tenant-chrome state
(brand, avatar, subscription tier, anything `AppChromeProvider` exposes)
must call the matching `refresh*()`/`bump*Version()` function - this is now
the second time a new call site skipped it. Worth grep-ing for
`settingsService.update(` and `brandService.` call sites whenever
`AppChromeProvider`'s shape changes, rather than relying on each author to
remember.

**BUG-SEC-002 — `GET /v1/security-audit-logs` 500'd on every single call, for every tenant**
Layer: Backend / Database. Found live-clicking the Security log page as a
signed-in OWNER (a full sidebar crawl looking for exactly this class of
defect) - the page has apparently never been exercised end-to-end since it
shipped, despite existing since CR-013. Reported through the application's
own "Contact admin" chat feature during the same session, as a live test of
that pathway - confirmed landing in `notification_log`
(`LOGGED_ONLY`, subject "[Support] ... Security log page fails to load
(500 error)") before being fixed here.

`SecurityAuditLogRepository.search()`'s `:action`/`:from`/`:to` filters used
the bare `(:param is null or ...)` pattern with no cast on the null-check
side - the exact BUG-SUP-004/BUG-PAY-001 class of defect. PostgreSQL's
prepared-statement parameter type inference cannot resolve `:from`/`:to`
(`LocalDateTime`) or `:action` (an enum) from a bare `? is null` check
alone, so every call failed with `could not determine data type of
parameter $6` - and since the frontend always sends these three parameters
(defaulting to "All time"/"All events" when the filter dropdowns are left
alone), this broke the page unconditionally, not just on some filter
combinations.

Root cause: the query predates BUG-SUP-004's discovery (it shipped in
CR-013, long before that lesson existed) and was never revisited when the
BUG-SEC-001 fix (CR-028) touched this same file - that fix added a tenant
scoping subquery but left the pre-existing untyped-parameter defect
untouched, since it wasn't the bug being fixed at the time.

Fix: `cast(:action as string)`, `cast(:from as timestamp)`,
`cast(:to as timestamp)` added to every null-check, matching
`PaymentRepository`'s established fix exactly.

Regression test: none added at the repository/PostgreSQL level (would need
a Testcontainers-backed test, blocked by BUG-ENV-002 in this environment) -
verified live: `GET /v1/security-audit-logs` returns 200 with real data
instead of a 500, both at the "All time"/"All events" default and with an
explicit action filter applied, confirmed via a real browser session.

---

The four bugs below were found during an adversarial security-testing pass
requested explicitly by the user against the newly-shipped Purchase /
Supplier Bill Import module (CR-035): multi-tenant isolation probing with a
second real tenant, RBAC probing with a real STAFF-role user at API +
route-guard + sidebar layers, and a battery of malformed/malicious file
uploads (missing file, null bytes, control characters, spoofed magic bytes,
spoofed Content-Type, header-injection filenames). Multi-tenant isolation
and RBAC enforcement were both confirmed clean (zero leaks, zero bypasses)
across every resource type tested, including this module's new document
endpoints - see PROJECT_SKILLS.md for the testing method. The four defects
below were all upload/file-handling gaps, not access-control gaps.

**BUG-PUR-001 — Missing/malformed multipart upload to Purchase Import crashed with a raw 500 for any user**
Layer: Backend.
`POST /v1/purchases/import/preview` (and `/confirm`) with no file attached,
or a request whose Content-Type didn't match `multipart/form-data`, returned
an unhandled 500 regardless of the caller's permissions - confirmed this
was not an auth bypass (a real file correctly got 403 for a STAFF user
lacking `PURCHASE_MANAGE`; it was the missing-file case specifically that
crashed for everyone, OWNER included).
Root cause: `HttpMediaTypeNotSupportedException` (thrown at handler-mapping
lookup, before `@PreAuthorize` or argument binding run, when no multipart
Content-Type is present at all) had no registered handler in
`GlobalExceptionHandler`. A related but distinct exception,
`MissingServletRequestPartException` (thrown during argument binding when a
well-formed multipart request is missing a named part), also had none.
Fix: `GlobalExceptionHandler` gained handlers for
`HttpMediaTypeNotSupportedException`, `MissingServletRequestPartException`,
`MaxUploadSizeExceededException` and `MultipartException`, each mapped to a
clean 400/413 `ErrorResponse` instead of a stack trace.
Regression test: none added (would require a `@WebMvcTest`/full context to
exercise Spring's handler-mapping layer, which is where this exception is
actually thrown) - verified live: the same request now returns
`400 MALFORMED_REQUEST` with a clean message instead of a 500.

**BUG-PUR-002 — A null byte or other control character in an uploaded field crashed a read-only preview query with a database error**
Layer: Backend / Database.
A CSV with a literal null byte embedded in the product-name field returned
`409 DATA_CONFLICT` on preview - reproduced twice to rule out a fluke.
Root cause: PostgreSQL rejects a literal null byte in a `text`/`varchar`
value outright ("invalid byte sequence for encoding UTF8"). The unsanitized
field value was used directly in a
`productRepository.findByTenantIdAndProductNameIgnoreCase()` lookup during
preview matching, and the resulting database-level error was caught by the
existing (correct for genuine conflicts, wrong for this) broad
`DataIntegrityViolationException -> 409` handler, even though nothing was
being written and there was no real conflict.
Fix: `RowParsing.sanitize()` strips Unicode control characters
(`Character.isISOControl`, excluding space/tab) from every extracted text
field - product name, brand, category, SKU, unit - before any value reaches
a database query, in the one shared place both the CSV and Excel extraction
paths already run through.
Regression test:
`CsvDocumentExtractionServiceTest.stripsControlCharactersFromTextFieldsSoTheyNeverReachADatabaseQuery`.

**BUG-PUR-003 — New products created via Supplier Bill Import silently discarded the bill's own SKU/part number**
Layer: Backend.
Not a crash - a data-completeness gap noticed while constructing a
"duplicate SKU, different name spelling" adversarial test file: `
ImportConfirmRow` had no field to carry a SKU at all, and
`createProductForImport()` always passed `null` as the new product's code,
so every product created through import got an auto-generated `PRD-XXXXXX`
code regardless of what part number was printed on the real bill - breaking
future "exact code match" detection on re-orders of the same item.
Fix: added `newProductSku` to `ImportConfirmRow` (backend DTO, frontend
`ImportConfirmRow` TS interface, and the import dialog's row-mapping),
threaded through as the `ProductRequest.productCode` argument. The
within-transaction dedup map (added earlier this session so two rows
naming the same new product don't attempt two `create()` calls against
data the first just committed) was extended to check by SKU first, then by
name - SKU being the stronger identity signal, since a real part number
should never be split across two products just because a name was typed
slightly differently on two lines.
Regression test:
`PurchaseImportServiceImplTest.twoNewProductRowsSharingTheSameSkuAreMergedIntoOneCreatedProduct`,
`PurchaseImportServiceImplTest.newProductTakesTheBillsSkuAsItsProductCode`.

**BUG-PUR-004 — A supplier bill's document could be uploaded with a spoofed Content-Type and served back as live HTML (stored XSS), and its filename was not checked for header-injection characters**
Layer: Backend / Security. Severity: CRITICAL - a real, confirmed-exploitable
stored-XSS primitive, not a theoretical one.
The multipart `Content-Type` header on an uploaded file is entirely
attacker-controlled and was never validated - only the file *extension*
and (for `.xlsx`) magic bytes were checked. `PurchaseImportServiceImpl`
stored the client-declared value verbatim (`file.getContentType()`), and
`GET /v1/purchases/{id}/document` served it back unchanged with
`Content-Disposition: inline`. Confirmed live: a `.csv` file containing a
`<script>` tag, uploaded with the multipart part declared as
`Content-Type: text/html`, was accepted (it is a genuinely valid CSV, so
the extension and content checks both passed), stored, and later served
back as `Content-Type: text/html` with `Content-Disposition: inline` -
meaning any tenant user with `PURCHASE_VIEW` opening "Original bill" on
that purchase would have the attacker's script execute in the app's own
origin. Separately, `DocumentUploadValidation` rejected `/` and `\` in a
filename (path-traversal guard) but not `"`, `\r` or `\n` - and the
filename is echoed unescaped into the `Content-Disposition` response
header, so a crafted filename could break out of the quoted parameter or
inject additional header content. curl itself refuses to send these raw
(it percent-encodes them), so this was only reproducible with a
hand-built multipart request body - a real attacker's HTTP client has no
such restraint.
Root cause: two related trust failures - (1) a client-declared header was
persisted and later replayed as the authority for how a response gets
rendered, and (2) a filename that flows into a response header had a
path-traversal check but no header-injection check.
Fix: `DocumentUploadValidation.safeContentType(extension)` derives the
served content type solely from the extension this class already validates
(`csv` -> `text/csv`, `xlsx` -> the real xlsx MIME type) - the client's
declared Content-Type is no longer stored or trusted anywhere.
`PurchaseController.document()` derives the extension from the filename
itself and calls `safeContentType()` at *serve* time, ignoring the stored
`contentType` column entirely, so already-poisoned rows written before this
fix are neutralized too, with no data migration needed (verified: the
document created during exploitation testing now serves as `text/csv`
without being touched). `DocumentUploadValidation.validateAndGetExtension()`
now also rejects a filename containing `"`, `\r` or `\n`.
Regression test:
`DocumentUploadValidationTest.rejectsAFilenameContainingADoubleQuote`,
`DocumentUploadValidationTest.rejectsAFilenameContainingCarriageReturnOrLineFeed`,
`DocumentUploadValidationTest.safeContentTypeIsDerivedFromExtensionNeverFromClientInput`.

**Lesson**: this is now the *third* occurrence of the untyped-parameter-type
class of bug (BUG-SUP-004, BUG-PAY-001, now this) - each time in a
different repository, each time only caught by actually clicking the page
with real data rather than by code review or compile/unit tests. Any
`@Query` with a bare `(:param is null or ...)` null-check on a
non-trivially-inferred type (`LocalDateTime`, an enum, sometimes even
`String`) needs the `cast(:param as ...)` treatment as a standing rule
applied while writing the query, not discovered after shipping.
**Codebase-wide audit run after this fix**: grepped every `(:param is null
or ...)` occurrence across all 10 repositories that use the pattern. Every
bare `:status`/`:action` enum comparison elsewhere (`User`, `Coupon`,
`Supplier`, `Customer`, `Product`, `Invoice`, `Quotation` repositories) is
left uncast deliberately - these have been live-tested extensively all
session with no failure, consistent with BUG-PAY-001's own finding that
enum-mapped-as-string comparisons infer correctly while
timestamp/date-range comparisons are the actual risk. `InvoiceRepository`
and `QuotationRepository`'s `:fromDate`/`:toDate` filters remain uncast too
- both are `LocalDate` (not `LocalDateTime`), which BUG-PAY-001 already
established PostgreSQL infers correctly, and both have been live-verified
working (Invoice's Period filter, CR-022). No other latent instance of the
actual defect (`LocalDateTime` or a similarly ambiguous type left uncast)
was found.

**BUG-PROJ-001 — Adding a project material/expense/payment always 500'd, shown to the user as a false "duplicate record" error**
Layer: Backend / Database. Found live-testing the new Project Management
module (CR-029) immediately after building it - the very first "Add
material" attempt in a real browser session failed.

`ProjectMaterial`, `ProjectExpense` and `ProjectPayment` do not extend
`BaseEntity` (a deliberate choice - these are lightweight child/ledger
records, not independently manageable master data, so the full
`@CreatedDate`/`@CreatedBy`/`@LastModifiedDate`/`@LastModifiedBy` JPA
auditing quartet was judged unnecessary, matching `InvoiceItem`'s
precedent of no audit columns at all). But unlike `InvoiceItem`, these
three entities were given plain `createdAt`/`updatedAt` fields backed by
real `NOT NULL` database columns - and because they don't extend
`BaseEntity`, nothing populates those fields automatically. Every one of
`ProjectMaterialServiceImpl.add()`, `ProjectExpenseServiceImpl.add()` and
`ProjectPaymentServiceImpl.add()` built its entity via `.builder()...
.build()` without ever setting `createdAt`, so every insert violated the
`NOT NULL` constraint on `created_at` and PostgreSQL rejected it -
Hibernate surfaced this as a `DataIntegrityViolationException`, which
`GlobalExceptionHandler`'s generic handler renders as "This record
conflicts with existing data. It may already exist or be in use." - a
correct-sounding message for the wrong problem, which sent the live
debugging session looking for a duplicate-key issue that didn't exist
before the real backend log (`null value in column "created_at" ...
violates not-null constraint`) revealed the actual cause.

Root cause: copying the "plain fields, no BaseEntity" shape from
`InvoiceItem` without also copying the fact that `InvoiceItem` has *no*
`created_at` column at all - these three entities added the column but not
the population mechanism a column like that needs.

Fix: `createdAt` is now set explicitly (`LocalDateTime.now()`) in each of
the three `add()` methods, and `updatedAt` similarly in
`ProjectMaterialServiceImpl.update()`.

Regression test: `ProjectChildRecordCreatedAtTest` (new, 3 tests) - one per
service, each captures the entity passed to `repository.save()` via
`ArgumentCaptor` and asserts `createdAt` is non-null, so a future edit that
drops the line again fails a fast unit test instead of only surfacing live.
Verified live: restarted the backend, re-ran the full material/expense/
payment flow in a real browser session end to end - all three now save
correctly and the project's financial summary (material cost, total cost,
net profit, margin, balance receivable) reflects them correctly.

**Lesson**: when a new entity is modelled after an existing one specifically
*because* that existing entity omits something (here: `InvoiceItem`'s lack
of audit columns), copying the entity shape without copying the *reason*
for that shape is how this class of bug happens - adding `createdAt` back
in without also adding back the mechanism that populates it. Any entity
with a plain (non-`BaseEntity`) timestamp column needs that column set
explicitly at the exact point the entity is built, verified by a test that
inspects the entity actually passed to `save()`, not just that `save()` was
called.

**BUG-INV-001 — Selling more than a product's stock on hand was allowed silently, leaving negative on-hand quantity**
Layer: Backend. Found live-testing during a general re-crawl of Dashboard/
Stock after the CR-030 round - the Stock page showed a real product
("Hammer - Anti-Slip (1 inch)", `PRD-004972`) at **-1 ROLL** on hand, a
physically impossible value for a shop's stock count.

`StockServiceImpl.applyMovement()` - the single shared method every stock
change goes through (`SALE` on invoice creation, `SALE_REVERSAL` on
cancel, manual `ADJUSTMENT`, and the future `PROJECT_CONSUMPTION`) -
computed `newBalance = quantityOnHand + quantityChange` and saved it
unconditionally, with no check that the result stays >= 0. Confirmed by
tracing the call from `InvoiceServiceImpl.create()` (line ~151), which
applies a negated `SALE` movement per invoice line with no prior
sufficiency check anywhere above it. A codebase grep for
`INSUFFICIENT_STOCK`/"insufficient stock" found nothing, and no registry
or `PROJECT_SKILLS.md` entry documented overselling as an intentional
choice - this was a genuine gap, not a deliberate backorder feature.

Fix: `applyMovement()` now throws `BusinessException("...", 422,
"INSUFFICIENT_STOCK")` with the product name, current on-hand quantity and
requested quantity in the message, before writing anything, whenever the
resulting balance would go negative. This protects every caller uniformly
(invoice sale today; project-material consumption and any future caller
automatically, once wired) rather than adding a one-off check inside
`InvoiceServiceImpl` alone.

Regression test: `StockServiceImplTest` (new, 2 tests) -
`rejectsMovementThatWouldGoNegative` (3 on hand, sell 5, expects
`BusinessException` with code `INSUFFICIENT_STOCK`) and
`allowsMovementThatExactlyReachesZero` (5 on hand, sell 5, expects success
with balance exactly 0 - the boundary is inclusive, a shop can legitimately
sell its very last unit). Full backend suite re-run: 178/180 (same 2
pre-existing BUG-ENV-002 Docker failures, +2 new).

**Lesson**: `applyMovement()` is the one place every stock mutation in the
system passes through, by design (CR-021/PROJECT_SKILLS) - which made it
exactly the right, and only necessary, place to add this guard once found,
rather than duplicating a sufficiency check in every caller. When a single
choke-point method like this exists, a missing invariant there is worse
than an isolated bug: it silently affects every present and future caller
at once. Found by testing a live dashboard number for physical plausibility
(can a shop have negative units of a hammer?), not by code review or a
targeted feature test - a reminder that "does this number make real-world
sense" is a testing technique in its own right, independent of the feature
being explicitly tested.

**BUG-FE-006 — the subscription-coupon redeem success message was set, then destroyed, twice, before a user could ever see it**
Layer: Frontend. Found live-testing CR-032 (subscription trial coupons)
immediately after building it - the "Coupon redeemed" toast appeared
correctly, but the more informative inline message ("You're now on Max
until 22 Sept 2026") never showed up in any screenshot, live or automated.

Two separate causes, found one after fixing the other:

1. `SubscriptionCouponsCard.handleRedeem()` called `window.location
   .reload()` immediately after `setRedeemResult(...)` - a hard browser
   reload destroys all React state, including the message just set, before
   the next paint.
2. After removing the hard reload in favour of a parent callback, the
   message *still* didn't survive: the callback was wired to
   `ShopSettingsPage`'s own `reload()`, which sets `loading = true` for the
   whole page - and `ShopSettingsPage` early-returns a bare spinner while
   `loading` is true, unmounting `SubscriptionCouponsCard` (and its local
   `redeemResult` state) along with everything else on the page.

Fix: `onRedeemed` now passes the redemption result (`grantedTier`,
`trialExpiresAt`) up to `ShopSettingsPage`, which patches `settings`
directly via `setSettings` - no reload, no loading-flag flip, no
remount. `SubscriptionCouponsCard` keeps its own lightweight `reload()`
for its own coupon list only (refreshing "times used"), which never
touches the parent's `loading` state.

Live-verified after the fix: redeemed a real coupon, confirmed the
message ("You're now on Max until 22 Sept 2026") rendered and *stayed
visible* in a screenshot taken after redemption, confirmed the
Subscription plan card's own "Trial" badge and reversion date persisted
too (driven by the same patched `settings` state, so it benefited from
the same fix).

**Lesson**: a component's own local state surviving a parent action isn't
guaranteed just because that action "isn't a full page reload" - a
parent-level loading flag that gates an early return can unmount a
child exactly as completely as `location.reload()` does, just less
obviously. When a success message needs to persist across a
success-triggered refetch, either lift the data needed into a form the
parent can patch without an unmount-causing loading state, or make sure
the message lives somewhere (a toast, global state) that survives
remounts by design - not in the state of the component that is about to
be torn down as a side effect of its own success handler.

---

**BUG-INV-002 — Emailing an invoice PDF crashed with a LazyInitializationException**
Layer: Backend.
Found live-testing the new "Share -> Email" action (CR-036) immediately
after building it: `POST /v1/invoices/{id}/share/email` returned a raw 500
regardless of the invoice or recipient.

Root cause: `InvoiceEmailServiceImpl.emailInvoicePdf()` has no
`@Transactional` of its own - it calls two already-transactional
`InvoiceService` methods (`get()`, `generatePdf()`), each opening and
closing its own Hibernate session, then called
`tenantRepository.getReferenceById(tenantId).getName()` to build the email
subject. `getReferenceById()` returns a lazy proxy that defers its SELECT
until a field is actually accessed - by the time `.getName()` ran, no
Hibernate session was open at all (this method's own scope has none, and
the two calls before it had already closed theirs), so the lazy load had
nothing to attach to.
Fix: `tenantRepository.findById(...)` instead of `getReferenceById(...)` -
`findById` resolves eagerly within its own self-contained repository-level
transaction, so it never depends on a session still being open in the
caller.
Regression test: none added (this is a wiring bug specific to one
service's lack of a transactional boundary, not business logic worth a
unit test) - verified live: re-tested the identical request after the fix,
got a clean `"LOGGED_ONLY"` response (no SMTP configured in this dev
environment) instead of a 500, with the correct log line recording the
invoice number and recipient.

**Lesson**: `Repository.getReferenceById()` is only safe to dereference
(`.getX()`) from inside a live transaction on the *same* call stack that
created the proxy - calling it from a plain `@Service` method with no
`@Transactional` of its own, even one that calls other transactional
methods just before it, is a `LazyInitializationException` waiting to
happen. Prefer `findById()` whenever the caller's own transactional
status isn't certain; reserve `getReferenceById()` for genuinely
transactional contexts where avoiding an unnecessary SELECT actually
matters (e.g., setting a `@ManyToOne` FK on a new entity before save).

---

**BUG-EXP-001 — Expense ledger's running total 500'd whenever no date filter was given (the default, most common case)**
Layer: Backend / Database.
Found live-testing `GET /v1/expenses/total` with no `fromDate`/`toDate`
immediately after building the Expense module (CR-036 phase 3) - exactly
the request the ledger page fires on first load, before the owner has
touched either date filter.

Root cause: `(:fromDate is null or e.expenseDate >= :fromDate)` with a
bare, uncast `LocalDate` parameter - the exact BUG-SUP-004/BUG-PAY-001/
BUG-SEC-002 class of defect (PostgreSQL's prepared-statement type
inference has nothing to go on from a bare `? is null` check alone),
reintroduced here despite being a known, already-documented lesson,
because the new query was written without first checking this file for
the pattern.

First fix attempt was itself wrong in an instructive way: wrapping *both*
occurrences of the parameter -
`(cast(:fromDate as date) is null or e.expenseDate >= cast(:fromDate as date))`
- looks more "consistent" but throws a completely different error,
`ERROR: cannot cast type bytea to date`. Comparing against
`SecurityAuditLogRepository`'s own already-working fix for the identical
problem (`cast(:from as timestamp) is null or a.createdAt >= :from`)
showed the real rule: cast **only** the `is null` occurrence: Hibernate/
PostgreSQL can infer the parameter's type fine from the second,
comparison occurrence (it's compared directly against a typed entity
attribute) - casting that occurrence too is what breaks it.

Fix: `(cast(:fromDate as date) is null or e.expenseDate >= :fromDate)` in
both `BusinessExpenseRepository.search()` and `.totalAmountPaise()`.
Regression test: none added at the repository/PostgreSQL level (would
need a Testcontainers-backed test, blocked by BUG-ENV-002 in this
environment) - verified live: re-tested `/expenses/total` and
`/expenses` both with and without date filters after the fix, all four
combinations returning correct results instead of a 500.

**Lesson (reinforcing, not new)**: before writing any JPQL
`(:param is null or ...)` null-check, search this file for
BUG-SUP-004/BUG-PAY-001/BUG-SEC-002/BUG-EXP-001 first rather than
rediscovering the same defect a fourth time. And when applying the fix:
cast only the `is null` side, never the comparison side too - the
"more consistent-looking" version is the broken one.

---

**BUG-FE-007 — Inline "Add new category" silently lost the just-created selection in both Product and Expense forms**
Layer: Frontend. Severity: MEDIUM - a real, previously-undiscovered
defect in a *shipped* feature (CR-024's Product inline category/brand
creation), found while live-testing the brand-new Expense module's
identical pattern (CR-036 phase 3), then confirmed to affect Product
too by re-testing it directly rather than assuming.

Symptom (Expense): creating a new category inline from "Add expense" (via
the Select's "+ Add new category" item) correctly created the category
server-side, but the Select reverted to "Select a category" instead of
showing the new one - submitting then failed client-side validation
("Choose a category"). Symptom (Product, the older, already-shipped
code): the same flow silently set `categoryId`/`brandId` to `0` instead
of the new id (or `null`) - submitting the product then failed server-
side with a confusing "not found" error instead of visibly re-prompting
for a category.

Root cause, found via direct instrumentation (temporary `console.log` on
the Controller's `field.value` and on `onValueChange`'s raw argument,
captured through a Playwright console listener) rather than guessed:
Radix Select fires a **second, spurious** `onValueChange('')` immediately
after `setValue('categoryId', created.id, ...)` sets the real value. At
the exact render where the newly-set id first appears, the Select's own
`categories`/`brands` prop (passed down from the parent list page's own
state) has not yet been updated to include the just-created option, so
Radix sees a controlled `value` with no matching `SelectItem` in its
current children and "self-corrects" by re-firing `onValueChange` with an
empty string - one render before the parent's updated prop arrives and
would have made it match. `Number('')` evaluates to `0`, and Product's
`value === NONE ? null : Number(value)` mapping turned that into `0`, not
`null` - explaining why Product's specific failure mode was "sent 0 to
the server" rather than "showed a validation error" like Expense's.

Fix: both `onValueChange` handlers (`ProductForm`'s Category and Brand
Selects, `ExpenseForm`'s Category Select) now return early on
`value === ''` before doing anything else - no real `SelectItem` in
either list is ever the empty string, so an empty value is never a
legitimate user pick, only this spurious Radix re-fire.
Regression test: none added (no frontend test framework in this repo -
verification follows this session's established Playwright-against-a-
live-backend pattern) - verified live for both forms: created a new
category inline, confirmed the Select now correctly shows the just-
created category's name (not reverting to empty/None), and confirmed via
a direct API check afterward that the saved product/expense actually
references the real new category id, not `0` or nothing.

**Lesson**: a Select whose options list is built from a parent-owned,
asynchronously-updated array (an inline "add new X" pattern) can receive
a value from `setValue()` one render *before* its own options prop
catches up - Radix (and likely other headless-UI Select implementations
with the same "controlled value must match a child" contract) may react
to that one-render mismatch by clearing itself, silently undoing the
`setValue()` call. Guard `onValueChange` against the specific "empty/
unmatched" sentinel the library uses for this self-correction, or restructure
so the new option is guaranteed to exist in the options array in the same
render/commit that sets the value.

---

**BUG-LAB-001 — OWNER never received the new LABOUR_VIEW/LABOUR_MANAGE permissions after V25 applied**
Layer: Database (Flyway migration). Severity: HIGH - OWNER, the role that
must never be locked out of anything, was silently unable to use a module
it had just been given (403 on every `/v1/workers`, `/v1/attendance`,
`/v1/worker-payments` call), found live-testing immediately after V25
first applied successfully - the login response's `permissions` array
simply did not contain either new code.

Root cause: `V25__labour_module.sql`'s original comment reasoned "OWNER
already gets every permission via V1's CROSS JOIN grant - no extra row
needed here." That is wrong. V1's `role_permission` grant for OWNER is a
one-time `INSERT ... SELECT ... FROM role r CROSS JOIN permission p WHERE
r.role_code = 'OWNER'` - it ran once, against whatever `permission` rows
existed *at that moment*, and is not re-evaluated when a later migration
adds new rows. Every other phase of CR-036 (and Purchase before it)
avoided this because their permission codes were speculatively pre-seeded
in V1 itself, so they were already covered by that one-time grant. Labour
Monitor was the first CR-036 phase to add a genuinely new permission code
after V1 (confirmed via grep beforehand - correctly), which exposed the
gap. The correct precedent already existed in `V18__project_management.sql`
(Project's own migration), which explicitly re-grants OWNER for its new
`PROJECT_*` codes in the same migration - this session's V25 just didn't
follow it.

A second, related mistake compounded this during the fix: V25 had
already been applied successfully to the local dev database (tables
created, MANAGER/ACCOUNTANT granted, OWNER not) before the missing-OWNER-
grant bug was noticed. Editing `V25__labour_module.sql` in place and
restarting hit Flyway's checksum-mismatch validation error ("Migration
checksum mismatch for migration version 25") - the exact protection
CLAUDE.md's "never edit an applied migration" rule exists for. Since this
was a solo, uncommitted, local-only migration with no other environment
depending on the old checksum, the correct resolution here (not a
precedent for shared/committed migrations) was to manually undo V25's
effects on the local dev database - drop `worker`/`worker_attendance`/
`worker_payment`, delete the `LABOUR_*` permission and role_permission
rows, delete the `flyway_schema_history` row for version 25 - then let
the corrected file reapply cleanly on restart. A migration already
committed and shared would instead need a new `V26` migration adding the
missing grant, never an in-place edit.

Fix: added an explicit
`INSERT INTO role_permission ... WHERE r.role_code = 'OWNER'` for
`LABOUR_VIEW`/`LABOUR_MANAGE` in `V25__labour_module.sql`, matching V18's
pattern. Regression test: none added at the migration level (no
Testcontainers in this environment, BUG-ENV-002) - verified live: logged
in as the seeded OWNER user after the corrected migration applied fresh,
confirmed `LABOUR_VIEW`/`LABOUR_MANAGE` now appear in the login
response's `permissions` array, and confirmed `POST /v1/workers` no
longer 403s for OWNER.

**Lesson**: OWNER's blanket permission grant is a **snapshot taken at V1
seed time, not a standing rule**. Any migration that introduces a
genuinely new permission code (not one speculatively pre-seeded earlier)
must explicitly grant OWNER in that same migration - check `V18` as the
reference pattern before writing a new one. Also: a migration that has
already run against your own local dev database, even mid-session and
never committed, is still "applied" for Flyway's checksum purposes - fix
it by rolling back its effects on that one database and letting the
corrected file reapply, not by pretending the edit-in-place rule doesn't
apply to solo local work.

---

**BUG-LAB-002 — Attendance page created a full day's wage for every unmarked worker**
Layer: Frontend. Severity: HIGH - silently fabricated money owed.
Found by the proactive module audit mandated by CR-037, not by a user
report.

Symptom: `AttendancePage.tsx` defaulted every row's status to `PRESENT`
when the page loaded, and `handleSave` submitted **every active worker**
regardless of whether the user had touched their row. Opening any date
with no existing marks and clicking Save instantly created PRESENT
attendance - and therefore a full day's wage owed - for the entire crew.
Combined with the missing future-date guard (BUG-LAB-003), a supervisor
could open next month, hit Save, and book wages for work nobody had done.
Those wages feed `sumWagePaiseByWorker` (the wage summary the worker is
paid from) and `sumWagePaiseByProject` (a project's labour cost), so the
fabricated number reaches real financial figures immediately.

Fix: `RowState.status` is now `AttendanceStatus | null`, with `null`
meaning "not marked". `handleSave` filters to rows where `status != null`
before building the request, the Save button is disabled and shows a
count (`Save (3)`) while nothing is marked, and clicking an already-
selected status button clears it back to unmarked so a mis-click is
undoable without saving. Regression test: none at the frontend level (no
frontend test framework in this repo) - verified by reading the corrected
submit path and by the backend-side guard added in BUG-LAB-003.

**Lesson**: a "mark the whole crew at once" batch UI must distinguish
*unmarked* from *the most common mark*. Defaulting a bulk form to the
happy-path value is convenient exactly until someone saves without
looking, and for anything that computes money that convenience is a
liability. Default to empty; make the count of what will actually be
submitted visible on the button.

---

**BUG-LAB-003 — Attendance and worker payments accepted future dates**
Layer: Backend. Severity: MEDIUM.

`AttendanceMarkRequest.attendanceDate` and
`WorkerPaymentRequest.paymentDate` were `@NotNull` only. Attendance could
be marked for 2030 and would be counted in the wage summary and in a
project's labour cost as money owed for work not yet done; a payment
could be dated in the future for cash that had not changed hands.
`BusinessExpenseRequest` shares the same omission, but attendance is the
worse case because it *generates* an amount owed rather than recording an
amount already spent.

Fix: `@PastOrPresent` on both. Regression test: covered by the live
verification below rather than a unit test, since Bean Validation runs at
the controller boundary, not in the service the unit tests exercise -
verified live: `POST /v1/attendance` and `POST /v1/worker-payments` with
a 2030 date both return 400 with a field-level message.

---

**BUG-LAB-004 — The same worker twice in one attendance batch returned contradictory data**
Layer: Backend. Severity: MEDIUM.

`AttendanceServiceImpl.mark()` mapped over `request.entries()` one at a
time. For two entries naming the same worker and date, the first `save()`
inserted a row; the second entry's lookup triggered a Hibernate auto-flush,
found that just-inserted row, and updated it - so the unique constraint
never fired and the database ended up correct. The **response** did not:
it contained two elements sharing one id with different statuses, the
first being a stale snapshot taken before the second mutation overwrote
it. `activity_log` also recorded a spurious CREATE followed by
"Attendance corrected to X" for what was one user action.

Fix: `mark()` collapses entries into a `LinkedHashMap` keyed by workerId
before processing, so the last entry for a worker wins and exactly one
row and one response element result. Regression test:
`AttendanceServiceImplTest.duplicateWorkerInOneBatchCollapsesToTheLastEntry`
asserts one response element, the last status, and exactly one `save()`.
Verified live: posting PRESENT and HALF_DAY for the same worker/date in
one call returns a single HALF_DAY element.

---

**BUG-LAB-005 — A mistyped worker payment could never be corrected, and duplicate workers were easy to create**
Layer: Frontend + Backend + Database. Severity: MEDIUM.

Two related gaps, both found by the CR-037 audit:

1. **No way to void a payment.** `WorkerPaymentService` had only
   create/list/wageSummary. A ₹5,000 typed where ₹500 was meant was
   permanently baked into the worker's paid total with no in-app fix -
   only a hand-edit of the database. Tellingly,
   `WorkerPaymentRepository.findByIdAndTenantId` already existed and was
   called by nothing: a leftover from a void path that was never built.
   Fixed by `V26__worker_payment_status.sql` (a `status` column plus
   CHECK constraint) and a `POST /v1/worker-payments/{id}/cancel`
   endpoint, mirroring `BusinessExpenseServiceImpl.cancel` exactly - a
   soft cancel, never a hard DELETE, since a payment is a financial
   record. `sumAmountByWorker` now filters to ACTIVE, so a cancelled
   payment stays visible in history (struck through, badged) but stops
   counting towards what the worker has been paid.
2. **No duplicate detection on workers.** Customer and Supplier both
   reject duplicates; Worker rejected nothing, so the same person entered
   twice produced two rows and attendance could go to the wrong one.
   Fixed - but keyed on **mobile number, not name**: two workers called
   "Ramesh" on one crew is ordinary and must stay allowed, whereas the
   same mobile number twice is almost always the same person. Deliberately
   *not* a copy of Supplier's stricter name-uniqueness rule, which does
   not fit this entity.

Regression tests:
`WorkerPaymentServiceImplTest.cancelSoftDeletesRatherThanRemovingTheRow`,
`.cancellingAnAlreadyCancelledPaymentIsRejected`,
`.cancellingAnotherTenantsPaymentThrowsNotFound`,
`WorkerServiceImplTest.rejectsASecondWorkerWithTheSameMobileNumber`,
`.allowsTwoWorkersSharingANameWhenMobileNumbersDiffer`. Verified live:
recorded a ₹5,000 typo (paid total went ₹200 → ₹5,200), cancelled it
(paid total back to ₹200, balance restored), confirmed the row remains in
the history list with `status: CANCELLED`, confirmed a second cancel
returns 422, a STAFF user gets 403, and a second tenant gets 404.

**Lesson**: when a module records money, "how does the user undo a typo?"
is part of the feature, not a follow-up. A repository method that nothing
calls is a reliable smell that an operation was designed and then never
built - grep for unused `findByIdAndTenantId` when auditing a new module.

---

**BUG-LAB-006 — Shops registered after V25 got MANAGER/ACCOUNTANT roles with no labour access**
Layer: Tenant provisioning / Security. Severity: HIGH - a permission gap
silently baked into every new customer's account.

Found while producing a role/permission report, not by a user report: the
default grants had **two independent sources of truth** that had drifted.
`V25__labour_module.sql` granted `LABOUR_VIEW`/`LABOUR_MANAGE` by
UPDATEing the `role` rows that existed *at migration time*. Every shop
created afterwards through `POST /v1/tenants/register` builds its four
roles from a hardcoded `ROLE_PERMISSIONS` map in
`TenantRegistrationServiceImpl`, which was never updated - so its MANAGER
and ACCOUNTANT roles had no labour permission at all. Confirmed in the dev
database: tenants 1-6 (pre-V25) had the grant, tenants 7-8 (registered
after) did not.

OWNER masked the problem completely. That service assigns OWNER from the
live `permission` table rather than from the map, so OWNER always looked
correct - and OWNER is the account anyone testing a new shop logs in as.
The gap only surfaced for a role nobody had yet created a user for.

Fix, three parts:
1. `LABOUR_VIEW`/`LABOUR_MANAGE` added to the MANAGER and ACCOUNTANT maps
   (deliberately **not** STAFF - a daily rate is cost-like data, same
   reasoning that excludes `PRODUCT_VIEW_COST`).
2. `V27__backfill_labour_grants_for_registered_tenants.sql` repairs shops
   already created with the gap. Written as an anti-join so it is safe to
   re-run and cannot create a duplicate `role_permission` row.
3. `RoleGrantDriftTest` (5 tests) makes the failure mode impossible to
   reintroduce silently: it reflects over every constant in
   `PermissionCode` and fails the build unless each one is either granted
   to a role in `ROLE_PERMISSIONS` or named in that role's explicit
   `WITHHELD_FROM_*` set with a comment saying why. A new permission code
   now forces the decision to be made and written down.

Verified live: after V27, zero MANAGER/ACCOUNTANT roles lack the grant;
registering a brand-new shop produced OWNER/MANAGER/ACCOUNTANT with both
labour codes and STAFF with neither.

**Lesson**: a hardcoded map that mirrors seed data is a second source of
truth, and it *will* drift the first time a migration adds a row to the
table it mirrors. Either derive it at runtime from the real table, or -
where a literal is genuinely wanted (here, so a new shop gets the
canonical grants regardless of what other tenants have customised) - guard
it with a test that fails when the two disagree. Also: when one role is
assigned dynamically and the rest from a literal, the dynamic one hides
the drift from every manual test, because it is the role you log in as.

---

**BUG-UI-001 — Mobile navigation was a centred modal in drawer costume; tall dialogs hid their own buttons**
Layer: Frontend (shared layout + dialog primitive). Severity: MEDIUM -
affected every dialog in the app and all navigation below 1024px.

Three separate defects in the shared UI, reported together:

1. **The mobile/tablet sidebar was a `<Dialog>` forced to the left edge**
   with `left-0 top-0 translate-x-0 translate-y-0`. Those overrides fought
   `DialogContent`'s own `-translate-x-1/2 -translate-y-1/2` centring, so
   the panel zoom-faded open from the middle of the screen instead of
   sliding in from the side. It also had `overflow-y-auto` on the whole
   panel, so brand and footer scrolled away with the nav list - unlike the
   desktop rail, where only the list scrolls.
2. **Dialogs barely animated** - overlay and content had `fade-in-0`
   only, no scale or movement, so they blinked into place.
3. **A dialog taller than the viewport hid its own footer.**
   `DialogContent` carried `max-h-[92dvh] overflow-y-auto`, so the panel
   itself was the scroll container: `DialogHeader`, `DialogFooter` **and
   the absolutely-positioned close button** all scrolled out of view. On a
   long form (Product, Invoice wizard, Import preview) the Save/Cancel row
   sat below the fold, which on a phone reads as "this dialog has no
   buttons".

Fixes:
1. New `shared/components/ui/sheet.tsx` - a side-drawer primitive on the
   same Radix Dialog root (so focus trap, scroll lock and Escape are
   identical), with real `slide-in-from-left` / `slide-out-to-left`
   animation and `h-dvh` (not `h-screen`, which mobile browser chrome
   pushes the footer under). `AppLayout` now uses it, with brand and
   footer pinned and only the nav list scrolling - matching the desktop
   rail.
2. Canonical entrance animation restored on `Dialog`: fade + `zoom-95` +
   the `slide-in-from-left-1/2` / `slide-in-from-top-[48%]` pair that
   cancels the centring transform (without those the panel flies in from
   the viewport's top-left corner).
3. `DialogContent` is now `flex flex-col … overflow-hidden` and the scroll
   moved to an inner wrapper, so the close button - a child of the
   non-scrolling panel - stays anchored. `DialogHeader` is `sticky top-0`
   and `DialogFooter` `sticky bottom-0`, both painted with a new
   `.surface-sticky-bar` class derived from the panel's own `--card`
   token at boosted opacity plus blur, so scrolled content is occluded in
   the translucent glass design styles as well as the flat ones (a
   hardcoded `bg-card` would have pasted an opaque band across a glass
   panel - CR-034 exists precisely so components do not hardcode past the
   theme).

Also: sidebar rows get a 44px minimum height below `lg` (they were ~36px,
under the touch-target minimum) scoped by media query so the pointer-driven
desktop rail keeps its density; the three dialogs that passed their own
`max-h-*/overflow-y-auto` had those overrides removed, since they would
otherwise put the scroll back on the panel and re-break the sticky footer.

Regression test: none - no frontend test framework in this repo, and no
browser automation available in this environment. Verified by `tsc -b
--force` and `vite build` only. **The visual result has not been confirmed
in a browser**; that is the main residual risk of this fix.

**Lesson**: a centred modal and an edge drawer are different primitives.
Overriding a centring transform with positioning utilities leaves the
animation keyframes still targeting the centred layout, so the thing opens
from the wrong place - and the CSS looks correct while doing it. Likewise,
putting `overflow` on the same element that positions a dialog silently
turns its header, footer and close button into scrolling content.

---

**BUG-ENV-003 — /actuator/health returned 503 on a healthy application, which would restart-loop the production deploy**
Layer: Ops / configuration. Severity: HIGH — not visible locally, fatal on
the documented hosting setup.

Found while starting the app for an unrelated task: `/api/actuator/health`
answered `{"status":"DOWN"}` with HTTP 503 while login, queries and every
other endpoint worked perfectly. The cause was Spring Boot's mail health
indicator: the Gmail credentials in `.env` are rejected
(`535-5.7.8 Username and Password not accepted`, repeating in the startup
log), and Boot folds that indicator into the aggregate status.
`management.endpoint.health.show-details: never` — correct for not leaking
internals publicly — meant nothing on the response said which component
had failed.

Harmless in development, but `docs/DEPLOYMENT_FREE_HOSTING.md` tells the
operator to set Render's health check path to `/api/actuator/health`, and
Render restarts any container answering non-2xx there. A wrong mail
password would therefore have restart-looped a perfectly good deploy, with
the logs showing only repeated startups and nothing pointing at mail. The
guide's own instructions and this default were in direct conflict.

Fix: `management.health.mail.enabled: false`. An unreachable SMTP server
genuinely does not mean the application is down — it means one optional
feature is degraded, which is what `NotificationStatus`
(SENT / LOGGED_ONLY / FAILED) already exists to express. To keep mail
failures discoverable rather than merely silent, the same change added
`POST /v1/settings/mail/test` (`SETTINGS_MANAGE`), which sends one message
and returns the mail server's own rejection text.

Verified live: health went from `503 {"status":"DOWN"}` to
`200 {"status":"UP"}` with the mail credentials left exactly as broken as
before.

**Lesson**: a liveness probe must answer "can this process serve requests",
not "is every optional integration configured". Folding a third-party
dependency into it hands that third party the power to kill your
deployment. Check what an aggregate health endpoint actually aggregates
before pointing a platform's restart policy at it.

---

## BUG-AUTH-014 — refresh-token reuse detection was untested and reported as a failure

**Module:** Authentication / Testing
**Severity:** MEDIUM (test-only; production code was correct throughout)
**Layer:** BACKEND ONLY — `AuthServiceImplTest`, no production file touched
**Status:** Fixed (CR-045)
**Found:** 2026-08-26, running `mvn clean verify` for the first time against the
Version 1 baseline. `AuthServiceImplTest$Refresh.reuseDetection` failed, and had
been failing at the baseline commit too — it was not introduced by CR-045.

### Symptom

```
Expecting com.hardware.erp.common.exception.AuthException: Invalid refresh token
to have a property "code" with value "TOKEN_REUSE" but value was "INVALID_REFRESH_TOKEN"
```

298 tests, 1 failure. `mvn clean verify` could not go green.

### Root cause

The test, not the application. BUG-AUTH-009's fix made the theft path in
`AuthServiceImpl.refresh` re-read the user through
`userRepository.findById(userId)`, because `stored.getUser()` is a lazy proxy
that `revokeAllForUser`'s `@Modifying(clearAutomatically = true)` detaches.

`AuthServiceImplTest` was never updated. `findById` was unstubbed, so Mockito
returned an empty `Optional`, the `orElseThrow` on that line threw
`INVALID_REFRESH_TOKEN`, and the method returned before revoking anything.

The production behaviour was correct the whole time. What the failure actually
meant was that reuse detection — revoke every session and bump `token_version`
when a rotated refresh token is replayed — had **no working test coverage**
since BUG-AUTH-009, which for a stolen-token response is the coverage that
matters most.

### Fix

Stub `userRepository.findById(10L)` and `userRepository.saveAndFlush(...)` in
`reuseDetection`, with a comment naming BUG-AUTH-009 so the coupling is visible
to whoever changes that path next.

### Regression test

`AuthServiceImplTest$Refresh.reuseDetection` itself — now genuinely exercising
the path it always claimed to. It asserts the `TOKEN_REUSE` code, that
`revokeAllForUser` is called with `REUSE_DETECTED`, and that `tokenVersion` is
bumped to 1.

### Lesson

A test that fails for a stubbing reason looks identical to a test that fails for
a behaviour reason, and both get read as "known broken". This one had gone
unnoticed because the suite had never been run end to end. `mvn clean verify`
belongs in CI, which CR-045 adds.
