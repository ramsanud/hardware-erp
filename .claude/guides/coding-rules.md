# Coding rules

Load this when: writing or changing any code — backend or frontend. It is
the shortest guide and the one most often violated.

## Naming law — locked, never rename

One concept, one name, across database → entity → DTO → JSON → TypeScript.
The only permitted transformation is `snake_case` → `camelCase`.

| Rule | Value |
|---|---|
| Base package | `com.hardware.erp` |
| Table names | singular `snake_case`; `app_user` not `user` (reserved) |
| Primary key column | `<table>_id`; Java field always `id` |
| Timestamps | `TIMESTAMP(3)` / `LocalDateTime`, suffix `_at` |
| Booleans | no `is_` prefix |
| Status columns | `VARCHAR(20)` + CHECK constraint, never TINYINT, never ENUM type |
| Money | `BIGINT` paise. Never float, never double |
| Unit rates | `DECIMAL(18,6)` |
| API paths | `/api/v1/<plural-kebab-noun>` |

Renaming anything already generated requires a Change Request first.
Persisted identifiers (column ids in CR-068, widget ids, tour step ids) fall
under this law too — renaming one silently discards every user's stored
choice.

## Style

- Comment **why**, not what. Explain non-obvious decisions and trade-offs;
  name the CR or BUG that motivated them.
- Match the surrounding code; do not introduce a new pattern for one file.
- Small, reviewable commits. One concern per commit.
- If an instruction conflicts with something in `project-knowledge/`,
  **stop and raise a Change Request** rather than silently choosing one.
- Do not overengineer. Proactive means thorough investigation and honest
  reporting, not unbounded implementation.

## Frontend conventions

- **Never hardcode a colour.** No hex, no `bg-emerald-600`, no
  `text-slate-900`. Eleven themes × light/dark exist; a literal matches
  nothing on ten of them. Use `--primary`, `--sidebar*`, `--chart-1..5`,
  `--success`, `--warning`, `--destructive`, `--muted-foreground`, `--border`.
  Tailwind rewrites `hsl(var(--x))` for opacity modifiers, so `bg-primary/10`
  works. The one exception is an image standing in for a photograph
  (`AuthHeroBackdrop`), and even then the overlay on top is token-driven.
- Every guarded call is re-checked server-side; `PermissionGate` and
  `PERMISSIONS` only hide UI the server would refuse. Gate navigation, tour
  steps, quick actions and cards by **permission, never role code** —
  roles are rows the owner edits (CR-008).
- Per-user presentation state (theme, column choice, tour seen, rail folds)
  is client-side, scoped by user id through `theme/themeScope.ts`
  (`readScoped`/`writeScoped`). "It must follow me to another device" is a
  new CR and a real table, not a bug.
- Inline "create a related entity without losing the form": the "+ Add new
  X" pattern (CR-024/CR-036). Watch for BUG-FE-007's Radix Select quirk.
- A dialog on a list page opens from a link with `?new=1`
  (`PRODUCT_ROUTES.create`, `CUSTOMER_ROUTES.create`, CR-082).
- Loading a Google Font on a self-hosted ERP's sign-in page is not
  acceptable; use the system stack or a system cursive.
- Icons are lucide, stroke-based; never emoji.
- Do not draw data that does not exist: no sparkline without a series, no
  "0%" where the comparison window was empty (show "—"), no invented social
  proof or product claims. Check a claim against shipped code before
  writing it into copy.

## Backend conventions

- Entity → repository → DTO → mapper → service → controller, in that order.
- Every repository read on a tenant-owned table is `findByIdAndTenantId(...)`
  or equivalent, with the tenant from `SecurityUtils.requireCurrentTenantId()`.
- `@PreAuthorize` on every endpoint; the permission code lives in
  `PermissionCode.java` and is mirrored in `frontend/.../auth/constants`.
- A provider interface gains capability via a **default method**, not a new
  parameter on every implementer (CR-073's attachment; SMS never changed).
- Before swapping the implementation behind a provider interface, grep for
  direct users of the underlying client (`JavaMailSender`, `HttpClient`) —
  the ones that never went through the interface will not move with it
  (CR-074 found three).

## Already covered by existing architecture — extend it, never rebuild it

| Concern | Existing mechanism |
|---|---|
| Role-based navigation/pages | `PermissionGate` (frontend), `PermissionCode` + `@PreAuthorize` (backend) |
| Tenant isolation | `SecurityUtils.requireCurrentTenantId()` + `findByIdAndTenantId(...)` — CR-016 |
| Audit trail | `security_audit_log` (security events) + `activity_log` (business changes, before/after) — CR-015, readable since CR-072 |
| Soft delete / never break history | status columns — never a hard `DELETE` on a referenced record |
| Inline "create related entity" | the "+ Add new X" pattern (CR-024, CR-036) |
| File/document import safety | preview → confirm → import, never upload → auto-insert |
| Document numbering | `document_sequence`, `SELECT … FOR UPDATE` (CR-041) |
| Per-user presentation state | `themeScope` (CR-034, CR-068, CR-075, CR-082) |
| First-run help | CR-075 tour + page tips; any new first-run interruption must seed itself as seen in the test harness |
