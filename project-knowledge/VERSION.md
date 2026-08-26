# VERSION HISTORY

## v0.4.0-dev — 2026-08-13 — PostgreSQL migration

**Database changes (breaking, pre-deployment)**
- CR-014: migrated from MySQL 8 to PostgreSQL 16. PostgreSQL is now the single
  source of truth for development, testing, Docker, seed data, documentation
  and deployment.
- `V1__auth_schema.sql` rewritten: identity columns, `TIMESTAMP(3)`, named
  constraints, `set_updated_at()` trigger, partial indexes.
- `V900__seed_dev_data.sql` rewritten for PostgreSQL date arithmetic.
- Added functional unique indexes on `lower(email)` and `lower(role_name)`.

**Bugs fixed**
- BUG-AUTH-009: case-insensitive uniqueness on email and role name was lost in
  the engine change and has been restored explicitly.

**Added**
- `docker-compose.yml` for local PostgreSQL 16.
- `.env.example` documenting every required environment variable.
- CR-013: `GET /api/v1/security-audit-logs` — the `AUDIT_VIEW` permission and
  `SecurityAuditLogRepository.search()` both existed but nothing exposed them.
- `registry/static_check.py` now fails on MySQL syntax in migrations.

**Breaking changes**
- `jdbc:mysql://…:3306` → `jdbc:postgresql://…:5432`.
- `DB_USER` default changes from `root` to `hardware_erp`.
- Existing local MySQL databases are not migrated. Drop them and run Flyway
  against a fresh PostgreSQL instance.

---

## v0.3.0-dev — 2026-08-13 — Module 1 test suite
- 11 test classes, 150 `@Test` methods, Testcontainers integration tests.
- BUG-AUTH-008: security filters were registered twice.

## v0.2.0-dev — 2026-08-13 — Module 1 backend corrections
- CR-003 audit: minimal JWT claims, logout vs logout-all, hardened bootstrap,
  rate limiting, correlation ids, standard response envelopes.
- BUG-AUTH-001 … BUG-AUTH-007 fixed.
- CR-007/008/009/010/011/012 approved and applied.

## v0.1.0-dev — 2026-08-13 — Module 1 backend
- Authentication, users, roles, permissions, JWT, refresh tokens.
- CR-001: SaaS design dropped in favour of a single-shop monolith.
- CR-002: naming registry locked.
