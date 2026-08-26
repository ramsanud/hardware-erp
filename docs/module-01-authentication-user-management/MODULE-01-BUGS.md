# MODULE-01-BUGS

Defects found and fixed during Module 1, plus open risks. The authoritative
copy is `project-knowledge/BUG_REGISTRY.md`; this file is the module-scoped view.

## Fixed

| ID | Severity | Summary |
|---|---|---|
| BUG-AUTH-001 | HIGH | Name, role and full permission list were inside the JWT payload |
| BUG-AUTH-002 | HIGH | Logout signed the user out of every device |
| BUG-AUTH-003 | MEDIUM | `@Version` on `BaseEntity` forced optimistic locking onto all future entities |
| BUG-AUTH-004 | HIGH | Bootstrap silently created an owner account with a default password |
| BUG-AUTH-005 | MEDIUM | Audit log could not distinguish a successful action from a failed one |
| BUG-AUTH-006 | HIGH | No rate limiting on authentication endpoints |
| BUG-AUTH-007 | MEDIUM | Integration tests ran on H2 while production ran a different engine |
| BUG-AUTH-008 | HIGH | Security filters registered twice, halving every rate limit |
| BUG-AUTH-009 | HIGH | Case-insensitive uniqueness lost in the PostgreSQL migration |

### BUG-AUTH-001 - sensitive data in the JWT
A JWT is signed, not encrypted; anyone holding it reads the payload. Carrying
authorities also meant a revoked permission stayed live until expiry.
**Fix:** claims reduced to `iss`, `sub`, `tv`, `iat`, `exp`. Authorities are
loaded from the database on every request.
**Regression test:** `JwtServiceTest.carriesNoPersonalData`.

### BUG-AUTH-002 - logout killed all devices
Closing the counter terminal signed the owner out on their phone.
**Fix:** `logout` revokes only the presented refresh token and leaves
`token_version` untouched. `logout-all` revokes everything and increments it.
**Regression test:** `AuthControllerIT.logoutDoesNotAffectOtherSessions`.

### BUG-AUTH-004 - silent bootstrap owner
"Create an owner if the user table is empty" fires in production too, with a
default password, and logged the mobile number.
**Fix:** `APP_BOOTSTRAP_ENABLED` defaults to false; the password must pass a
strength check or the application refuses to start; nothing sensitive is logged.

### BUG-AUTH-008 - filters registered twice
`@Component` on a `OncePerRequestFilter` makes Spring Boot register it in the
servlet chain *in addition to* the Security chain. Both filters ran twice per
request; the rate limiter consumed two tokens per call, silently halving every
configured limit.
**Fix:** `@Component` removed; both are declared as `@Bean` in `SecurityConfig`.
`RequestCorrelationFilter` keeps `@Component` deliberately.
**Regression test:** `SecurityFilterRegistrationTest`.

### BUG-AUTH-009 - case-insensitive uniqueness lost
MySQL's `utf8mb4_0900_ai_ci` collation made `UNIQUE (email)` case-insensitive
for free. PostgreSQL compares case-sensitively, so a literal DDL port would have
accepted `Owner@shop.in` alongside `owner@shop.in`, and `findByIdentifier` would
then match two rows and throw.
**Fix:** `CREATE UNIQUE INDEX uk_user_email_lower ON app_user (lower(email));`
and the same for `role_name`.
**Regression test:** `UserControllerIT.duplicateEmailDiffersOnlyByCase`.

## Open

| ID | Severity | Summary | Status |
|---|---|---|---|
| BUG-ENV-001 | INFO | The backend has never been compiled | **Open** |

The authoring environment cannot reach Maven Central or run Docker, so
`mvn clean compile`, `mvn clean verify` and Testcontainers have never executed.
Static checks pass: package/path agreement, brace and parenthesis balance,
entity-to-migration column agreement, interface-to-implementation coverage,
permission constants, seed-to-schema columns. None of that resolves a Java type.
**Close by running `./mvnw clean verify` on a machine with Docker.** Expect a
small number of import-level fixes.

## Known issues

| Issue | Impact | Plan |
|---|---|---|
| Frontend has no unit tests | `src/modules/auth/tests/` is empty | Add Vitest + Testing Library |
| Swagger never opened | Annotations unproven | Verify when the backend first runs |
| 12 of 18 planned PDFs unwritten | Documentation incomplete | Continue after Module 2 |
| Rate limit state is in-process | Lost on restart | Acceptable: account lockout is the durable control |

## Risks

**Access token lives in browser memory.** A page reload loses it and the app
silently refreshes using the HttpOnly cookie. If a future change breaks that
refresh, users are logged out on every reload. Covered by
`AuthProvider` bootstrap.

**`security_audit_log` grows without bound.** `app.cleanup.audit-retention-days`
defaults to 0, meaning never delete. Correct for compliance, but the table needs
monitoring once the shop is live.

**No HTTPS in local development.** HSTS headers are set but meaningless over
plain HTTP. Production must terminate TLS.

## Future improvements

- Invite-based user registration (token link instead of a temporary password)
- Two-factor authentication for the OWNER role
- Audit log export to CSV
- Session-per-device naming so a user can label "counter PC"
