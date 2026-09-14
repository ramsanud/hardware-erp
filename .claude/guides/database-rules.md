# Database rules

Load this when: adding or changing a table, column, constraint, migration,
seed row, or any JPA entity.

## Hard rules

1. **Never edit an applied Flyway migration.** Add a new version
   `V{n}__description.sql`. Flyway checksums the whole file, comments
   included — CR-068 could not even correct a comment in V54.
2. **Hibernate is `ddl-auto: validate`.** Never `update`. An entity that
   disagrees with its migration fails startup, on purpose.
3. **PostgreSQL only.** No MySQL syntax; `static_check.py` fails the build on
   it (it cannot run on this machine — write PostgreSQL anyway).
4. **Seed data lives in `db/seed/`** (`V9xx__`), loaded by dev and test
   profiles only. Twelve dev-only product rows are the only product seed;
   never describe them as a catalogue.

## Tenant column

- Every tenant-owned table carries `tenant_id`, filtered on every read from
  `SecurityUtils.currentTenantId()`.
- **Nullable where an owner cannot be honestly recovered.** `activity_log`
  (V55) is nullable: rows written by a scheduled job have no user and no
  tenant, and `NULL` never equals anything in SQL, so an unattributable row
  is invisible to every tenant rather than visible to all. `NOT NULL` would
  have forced the migration to invent an owner for an audit row.

## Naming and types (the database half of the naming law)

| Rule | Value |
|---|---|
| Table names | singular `snake_case`; `app_user` not `user` |
| Primary key | `<table>_id`; Java field `id` |
| Timestamps | `TIMESTAMP(3)`, suffix `_at` |
| Booleans | no `is_` prefix |
| Status columns | `VARCHAR(20)` + CHECK constraint — never TINYINT, never an ENUM type |
| Money | `BIGINT` paise |
| Unit rates | `DECIMAL(18,6)` |

## Patterns that exist — use them

- **Soft delete**: status columns (`ACTIVE`/`INACTIVE`/`CANCELLED`). Never a
  hard `DELETE` on a record another table references; users and suppliers
  are referenced by financial records forever.
- **Document numbering**: `document_sequence` with `SELECT … FOR UPDATE`
  (CR-041). Do not generate numbers in application memory.
- **Idempotency**: `IdempotencyService` for retried writes.
- **Activity log**: every business write funnels through one
  `ActivityLogWriter.write(...)` that stamps the tenant once (CR-072). A
  `REQUIRES_NEW` annotation must sit on the method reached from outside the
  bean, not on a self-invoked private one (BUG-BE-002).
- **Sorting**: an unknown `?sort=` field is a 400 with the field named, via
  `GlobalExceptionHandler` (BUG-BE-004) — do not add per-controller
  whitelists that 500 on a typo.

## Registries to update

`project-knowledge/DATABASE_REGISTRY.md` for every table, column and
constraint; the CR entry names the migration version. Run
`registry/check_registry.py` where python exists to detect drift.
