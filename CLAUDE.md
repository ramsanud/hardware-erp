# CLAUDE.md

Read automatically every session. Deliberately short: it names the rules
that apply to *every* task and points at the guide for the rest. Load a
guide only when the task needs it — that is what keeps context small.

---

## What this is

Hardware ERP for hardware shops in India. One Spring Boot app, one React
app, one PostgreSQL database, many tenants (shops) in one schema separated
by `tenant_id` — enforced server-side from the JWT, never from client input.
This project is in **maintenance and extension**; never treat it as
greenfield.

```text
backend/   Java 21, Spring Boot 3.4.2, PostgreSQL 16, Flyway
frontend/  React 18, TypeScript, Vite 6, Tailwind, shadcn/ui, Playwright
project-knowledge/  8 registries — the source of truth
.claude/guides/     topic guides — load per task, see below
```

---

## Which guide to load

| Task | Load |
|---|---|
| Any code change | `.claude/guides/coding-rules.md` |
| Bug report, feature ask, "is this done?" | `.claude/guides/business-rules.md` |
| Table, column, migration, entity, seed | `.claude/guides/database-rules.md` |
| Auth, permissions, tenant scope, uploads, messaging, logs | `.claude/guides/security-rules.md` |
| Verifying, committing, a failing test that is not an assertion | `.claude/guides/testing.md` |
| New module, layouts/shell, git branches, what exists today | `.claude/guides/architecture.md` |

Then the registries in `project-knowledge/`, only the ones the task touches:
`PROJECT_REGISTRY` (stack, module status) · `DATABASE_REGISTRY` ·
`API_REGISTRY` · `SECURITY_REGISTRY` · `CHANGE_REQUEST_REGISTRY` (CR-001…) ·
`BUG_REGISTRY` · `PROJECT_SKILLS` (lessons) · `FEATURE_REGISTRY`. And
`RESUME_POINT.md` for where the last session stopped. Never ask the user to
re-explain a decision recorded in one of these.

---

## Hard rules — apply to every task

1. Never edit an applied Flyway migration; add a new version.
2. Hibernate is `ddl-auto: validate`. Never `update`.
3. PostgreSQL only. No MySQL syntax.
4. Seed data lives in `db/seed/`, dev and test profiles only.
5. No self-registration endpoint. The owner creates accounts.
6. Authorization is permission-based. Never `hasRole('OWNER')` for a rule.
7. Users and suppliers are soft-deleted. Financial records reference them forever.
8. Security events → `security_audit_log`. Business changes → `activity_log`.
9. Access token in memory only. Never `localStorage`.
10. **Never claim a build passes without running it.** Say "not executed".

Plus three that earned their place the hard way:

- **11 — Never hardcode a colour.** Eleven themes × light/dark; use tokens.
- **12 — Never draw data that does not exist** — no invented sparklines,
  counts, deltas or product claims.
- **13 — A visible redesign is approved as a render first, then coded exactly.**

---

## The shape of a task

1. Say which layer is at fault before fixing (`SCOPE: FRONTEND ONLY` /
   `BACKEND ONLY` / `BOTH`) — see business-rules.
2. Claim the CR or BUG number in the registry **before** writing code; grep
   for it first, other sessions allocate numbers too.
3. Fix same-root-cause defects found on the way, in the same commit;
   propose anything bigger as its own CR and wait.
4. Verify in isolation (see testing) and quote the numbers.
5. Registry entry with both index row and body; `RESUME_POINT.md`; commit
   with Conventional Commits, one concern, the body saying *why*.

---

## Verification

```bash
cd backend  && mvn -o clean verify                              # needs Docker
cd frontend && node ./node_modules/typescript/bin/tsc -b --force
cd frontend && node ./node_modules/vite/bin/vite.js build
cd frontend && node tests/run.mjs                               # 11 suites, 297 assertions
python3 registry/static_check.py                                # NOT installable here — report "not executed"
```

Green as of 2026-09-15: **599 unit + 251 integration tests** (`ebafec6`,
detached worktree), frontend **297/297**, verified on isolated builds.

---

## Two things that will waste a day if forgotten

**Other Claude sessions edit this checkout at the same time.** `git status`
will list files you never touched. Stage by explicit pathspec, in the same
command as the commit; build shared files (registries, `RESUME_POINT.md`,
`tests/run.mjs`) from `HEAD` plus your hunks when both of you have edited
them. Their `mvn clean` and `vite build` also delete the class files and
`dist/` your tests are reading — so **a failure shaped like "a file
vanished" is not a bug**; re-run in a detached worktree / private `dist`
before touching anything (testing.md).

**This machine has no `python3`**, npm script shims fail under Git Bash
(call `node ./node_modules/...` directly), and `docker` needs an isolated
`DOCKER_CONFIG` from Git Bash. Full list in architecture.md.

---

## Git

`main` (tagged releases) ← `develop` ← `feature/*`, `bugfix/*`; `hotfix/*`
from `main` back into both. Never commit feature work to `main` or `develop`
directly; merge with `--no-ff`. Environments are Spring profiles, never
branches. Commit only when asked; never push unless asked.
