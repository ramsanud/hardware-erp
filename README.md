# Hardware ERP

ERP for hardware shops in India. One Spring Boot application, one React
application, one PostgreSQL database, **multiple shops (tenants)** sharing that
one schema through a `tenant_id` discriminator column.

Java 21 · Spring Boot 3.4 · PostgreSQL 16 · Flyway · JWT ·
React 18 + TypeScript + Vite 6 + Tailwind + shadcn/ui

Not microservices. Not database-per-tenant. Module folders express boundaries,
not deployment units. Every tenant-owned table carries `tenant_id`, and every
query against one filters by it, taken from the JWT via
`SecurityUtils.currentTenantId()` — never from a request parameter.

```
hardware-erp/
├── backend/            Java 21, Spring Boot 3.4.2, PostgreSQL 16, Flyway
├── frontend/           React 18, TypeScript, Vite 6, Tailwind, shadcn/ui
├── docs/               per-module documentation and Postman collections
├── project-knowledge/  the registries — the source of truth
├── registry/           static_check.py — run before every commit
└── .github/workflows/  CI
```

`project-knowledge/` is authoritative for the database schema, the API surface,
the security design, and every approved change request. Read it before writing
code; `CLAUDE.md` says the same thing at more length.

---

## Environments

Four, expressed as **Spring profiles**, not as Git branches. Branches describe
work in progress; profiles describe where the software is running. Conflating
the two is how a repository ends up with a `production` branch nobody dares
merge into.

| Profile | Where | Seed data | API browser | Developer inspection |
|---|---|---|---|---|
| `local` | one developer's machine | yes | yes | yes, with the permission |
| `dev` | shared development server | yes | yes | yes, with the permission |
| `test` | QA deployment | no | yes | yes, with the permission |
| `prod` | real shops | no | **disabled** | **never** |

Selected with `SPRING_PROFILES_ACTIVE`. Defaults to `dev` when unset.

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Config lives in `backend/src/main/resources/application-{profile}.yml`, with the
shared defaults in `application.yml`. Each file carries a header explaining what
that environment is allowed to disclose.

> `backend/src/test/resources/application-test.yml` is a **different file** from
> the deployable `application-test.yml`, and shadows it during `mvn test`
> because `target/test-classes` precedes `target/classes` on the classpath. The
> automated suite and the QA deployment are the same environment conceptually,
> and only one of them is ever loaded at a time.

### Configuration

Every secret comes from the environment. Copy `.env.example` to `.env` and fill
it in; `.env` is ignored by Git and must stay that way.

The frontend reads only `VITE_`-prefixed variables — see
`frontend/.env.example`. Anything Vite can read ends up in the shipped bundle,
so no backend secret may ever be given a `VITE_` name.

---

## Running it

```bash
# 1. Database
docker compose up -d

# 2. A real JWT secret. The placeholder in application.yml is refused
#    outright under the prod profile - see JwtSecretGuard.
export JWT_SECRET=$(openssl rand -base64 32)

# 3. Backend
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. Frontend
cd frontend && npm ci && npm run dev
```

Flyway owns the schema; Hibernate runs with `ddl-auto: validate` and never
creates a table. Under `local` and `dev` the `db/seed` migrations also load a
sample shop with an owner account. Production never loads them (CR-009).

Swagger UI at `http://localhost:8080/api/swagger-ui.html` — in `local`, `dev`
and `test` only. It is disabled in production, where a complete map of every
endpoint, parameter and DTO is of no use to a shop owner.

### Verification

```bash
cd backend  && mvn clean verify      # compile + unit + integration tests (needs Docker)
cd frontend && npm run typecheck     # tsc -b --force
cd frontend && npm run build         # production build
python3 registry/static_check.py     # structure, entity/migration agreement
```

---

## Branch strategy

```
main                    production-ready code only; tagged releases
  └── develop           integration branch for active development
        ├── feature/*   new functionality
        ├── bugfix/*    ordinary bug fixes
        └── hotfix/*    urgent production fixes, branched from main
```

No feature work happens directly on `main` or `develop`.

```bash
git switch develop && git pull
git switch -c feature/thermal-printing
# ... commits ...
git switch develop && git merge --no-ff feature/thermal-printing
```

Examples: `feature/product-import`, `feature/offline-mode`,
`bugfix/invoice-total-calculation`, `hotfix/payment-validation`.

Release: `develop` → validate → merge into `main` → tag.
Urgent production fix: branch `hotfix/*` from `main`, merge back into **both**
`main` and `develop`, then tag a patch release.

There are deliberately no `production`, `development` or `testing` branches.
Environments are profiles, as above.

---

## Commit convention

[Conventional Commits](https://www.conventionalcommits.org/). One concern per
commit.

```
feat:     add product import
fix:      correct invoice total
refactor: extract the document number allocator
docs:     document the git and environment workflow
test:     add invoice integration tests
chore:    update dependencies
security: restrict developer diagnostics to non-production
```

The body should say **why**, not restate the diff. A commit that changes a
security boundary or a default should say what the previous behaviour was.

---

## Release

`MAJOR.MINOR.PATCH`, tagged on `main`:

```bash
git tag -a v1.0.0 -m "Hardware ERP Version 1.0.0"
git push origin v1.0.0
```

A tag is created only after `mvn clean verify`, `npm run typecheck` and
`npm run build` have all passed on the commit being tagged.

---

## Developer inspection

Developer diagnostics live at `/api/v1/dev/inspection/*` and are behind **two
independent gates, both enforced server-side**:

1. **Environment** — `app.developer-inspection.enabled`. False by default.
   `application-prod.yml` sets a hard `false` with no environment-variable
   override, and `DeveloperInspectionService` returns false whenever the `prod`
   profile is active regardless of configuration.
2. **Person** — the `DEVELOPER_INSPECT` permission, which **no default role
   holds, including OWNER**.

Administering a hardware shop and debugging the software that runs it are
different jobs. An admin is not a developer, and granting this permission by
default would put a diagnostics console one stolen owner password away.

`SecurityConfig` denies the whole `/v1/dev` and `/v1/debug` trees where the
environment does not permit inspection, so a new controller added under those
paths is covered by default. Actuator beyond `/actuator/health` requires
`DEVELOPER_INSPECT` too — `env`, `configprops` and `beans` would otherwise print
the datasource password and the JWT signing key. `/actuator/health` stays public
because the hosting platform uses it as a liveness probe.

Hiding the Developer entry in the React sidebar is convenience, never the
control. The API is the boundary.

**Not implemented, on purpose:** blocking F12, right-click or Ctrl+Shift+I, and
any form of DevTools detection. None of that is security — it inconveniences
honest users, makes the application feel broken, and stops nobody who is
actually looking. Production safety comes from authentication, authorization,
server-side access control, sanitized errors and the source-map policy below.

---

## Security

**Never commit a secret.** No API keys, JWT secrets, database passwords, OAuth
secrets, SMTP passwords, private certificates or `.env` files. `.gitignore`
covers `.env`, `*.pem`, `*.key`, `*.p12`, `*.jks` and friends, and CI fails the
build on a tracked `.env`, on a provider key format, and on an assigned
secret-like literal.

Everything else is environment variables, or a secret manager in production.

Other properties this repository maintains:

- **Access tokens are held in memory only**, never `localStorage`. The refresh
  token is an HttpOnly, Secure, SameSite=Strict cookie scoped to
  `/api/v1/auth`, so an XSS bug cannot exfiltrate the long-lived credential.
- **Error responses are sanitized in every environment.** No stack trace, SQL
  text, file path or internal class name reaches a client — only a code, a
  human-readable message and an `X-Request-ID` that ties the response to the
  server-side log.
- **No source maps in the production bundle** (`build.sourcemap: false`,
  asserted by CI). Development builds keep them.
- **Logs never carry passwords, tokens or API keys.** Production logs at
  `WARN`/`INFO`; `local` and `dev` go to `DEBUG`.
- **Authorization is permission-based**, never `hasRole('OWNER')` for a business
  rule.

---

## Design decisions worth knowing

**Permissions, not role names.** `@PreAuthorize` checks `USER_MANAGE`, never
`hasRole('OWNER')`. When the shop owner says "let the accountant do X too", that
becomes a row in `role_permission`, not a code change and a redeploy.

**`STAFF` has no `PRODUCT_VIEW_COST`.** Counter staff must not see purchase
price or margin, and that is enforced server-side rather than by hiding a column
in React.

**Refresh tokens are opaque random strings, stored as SHA-256 hashes.** Not
JWTs. A database dump yields nothing usable. They rotate on every use, and
**reuse of a rotated token revokes every session for that user** — the standard
signal that a token was stolen.

**`token_version` on the user row.** Every access token carries it. Bumping it
invalidates all outstanding tokens instantly, which is how password change, role
change, deactivation and logout-everywhere all work without a blacklist.

**Login accepts mobile or email in one field.** Counter staff will not remember
which one they were registered with.

**Wrong password and unknown user return the identical response.** Same for
`forgot-password`, which always reports success. Otherwise these endpoints
become a way to discover which mobile numbers are registered.

**Account lockout:** 5 failed attempts → 15 minutes. Rate limiting runs *before*
authentication, so credential stuffing never reaches BCrypt, where 250 ms per
attempt would become a denial-of-service lever.

**Sort fields are whitelisted and page size is clamped to 100.** A raw sort
parameter reaching `ORDER BY` is an injection surface; `size=1000000` is a
memory-exhaustion surface.

**Users and suppliers are soft-deleted.** Financial records reference them for
the life of the business, so the row must stay resolvable. The last active owner
cannot be deleted, deactivated, or have their role changed.

**Money is `BIGINT` paise**, never a float or a double. Unit rates are
`DECIMAL(18,6)`.

**Audit writes use `REQUIRES_NEW`** so a logging failure never rolls back the
user's action, and a failed action still leaves the attempt on record. Security
events go to `security_audit_log`; business changes go to `activity_log`.

**BCrypt strength 12** (~250 ms/hash), deliberately slow.

---

## Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request to `main` and
`develop`:

- **Backend** — `mvn clean verify`, which is compile plus the unit suite plus
  the Testcontainers integration suite against real PostgreSQL. Test reports are
  uploaded on failure only.
- **Frontend** — `npm ci`, `npm run typecheck`, `npm run build`, then an
  assertion that the bundle contains no source maps.
- **Secret scan** — assigned secret literals, provider key formats, private
  keys, and any tracked `.env` or certificate file.

There is no automated production deploy. Adding one is a decision to make
deliberately, not a side effect of setting up CI.
