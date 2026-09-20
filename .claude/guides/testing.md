# Testing commands

Load this when: verifying anything, before every commit, and whenever a test
fails in a way that does not look like an assertion.

## Commands (Git Bash on this machine)

```bash
# backend — needs Docker for the Testcontainers tier
cd backend && mvn -o clean compile                    # fast sanity
cd backend && mvn -o clean verify                     # 510 unit + 231 integration
cd backend && mvn -o verify -Dit.test=SomeIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false

# frontend — npm shims fail under Git Bash; call the tools directly
cd frontend && node ./node_modules/typescript/bin/tsc -b --force
cd frontend && node ./node_modules/vite/bin/vite.js build
cd frontend && node tests/run.mjs                     # all suites, serves dist/ on 4173
cd frontend && node tests/run.mjs dashboard           # one suite by name

# structure checks — python3 is NOT installed here; report "not executed"
python3 registry/static_check.py
python3 registry/check_registry.py
```

Docker from Git Bash needs `DOCKER_CONFIG` pointing at a directory holding
an empty `config.json` (the credential helper is invisible; images are
public).

## Hard rule 10

**Never claim a build passes without running it.** State "not executed"
instead. A release is tagged only after `mvn clean verify`, typecheck and
build have all passed on the exact commit being tagged.

## Isolate before you believe a failure

Another session's `mvn clean` deletes the class files your suite is loading;
its `vite build` rewrites the `dist/` your preview serves. On 2026-09-09 this
produced three consecutive phantom failures — *Unable to find a
`@SpringBootConfiguration`*, *No qualifying bean of type `TotpService`*, and
101 of 108 ITs failing on a vanished nested class — every one of which passed
on isolated re-run.

**The tell is the shape of the error.** `ClassNotFoundException`, "no
qualifying bean" for a plain `@Service`, a failed `@SpringBootConfiguration`
search, a blank page or `ERR_CONNECTION_REFUSED` mid-suite: these mean *a
file moved under you*, not that code changed. A genuine regression fails an
assertion. **Never "fix" a test that fails this way.**

Isolation, both tiers:

```bash
# backend: verify the exact commit in a detached worktree with its own target/
git worktree add --detach <tmp> <commit> && (cd <tmp>/backend && mvn -o clean verify)
git worktree remove --force <tmp>

# frontend: build to a private dir and serve it on a private port
node ./node_modules/vite/bin/vite.js build --outDir <tmp>/dist-iso --emptyOutDir
node ./node_modules/vite/bin/vite.js preview --outDir <tmp>/dist-iso --port 4199 --strictPort &
E2E_BASE_URL=http://localhost:4199 node tests/run.mjs
```

## Frontend suites (`frontend/tests/`)

Playwright against the **production bundle**, one process, no fixtures
beyond `support/`. Nine suites, 246 assertions as of CR-082's second pass:

| Suite | Covers |
|---|---|
| auth | sign-in flows, reset links |
| products | grid, status colours (never colour alone) |
| navigation | every route at five viewports, no horizontal scroll, no page errors |
| page-header | title/toolbar geometry (BUG-FE-034/035) |
| sidebar | active-row computed style incl. `::before` (BUG-FE-036) |
| onboarding | tour, page tips, pause/resume (CR-075, BUG-FE-037) |
| address-map | Leaflet picker (CR-076) |
| whatsapp | manual `wa.me` links (CR-080) |
| dashboard | titles, figures, sparklines (measured vs baseline), empty-chart canvases, the unanswered-endpoints case, rail folds (CR-082) |
| states | landing page at two widths, partial-data notice + Retry, offline banner, code-keyed ErrorState, session-expiry notice, denied-route exit (CR-100) |

Harness rules that bite:

- `newPage` seeds the tour and page tips as **already seen**. Pass
  `firstVisit: true` to test first-run behaviour. **Any new first-run
  interruption must do the same** — a modal that greets new users greets
  every test.
- Fixtures mirror real DTOs. When a stub drifts from the DTO the suite tests
  the stub (BUG-FE-035's toolbar was two buttons short for that reason).
- Assert **computed style** for anything keyed to an attribute a component
  sets; a selector that matches nothing is invisible to typecheck, build and
  render tests alike.
- Wait for content or detachment, never a fixed sleep: `networkidle` can
  fire before React commits a lazy route; Radix keeps a dialog mounted
  through its exit animation.
- lucide icons are made of `<polyline>`; count sparklines by
  `svg[data-sparkline]`, not by element type; `data-sparkline-empty` marks
  a flat baseline drawn for a figure with no series.
- A page must render when NONE of its endpoints answer usefully. The
  generic `signedInApi()` returns a page object for any URL with a query
  string, so `data.points` is undefined - reading `.length` on it white-
  screened the dashboard on every viewport. Optional-chain every field of
  a response, and keep one "nothing answered" case in each page's suite.
- After a Playwright version bump:
  `node ./node_modules/playwright/cli.js install chromium`.

## Backend suites

- Unit tests run under surefire; integration tests (`*IT`) under failsafe,
  all extending `support/AbstractIntegrationTest` (one reused PostgreSQL
  container, no rollback between methods — tests that reset data register
  their own tenants).
- `RateLimitIT` and `PlatformAdminRefreshCookieIT` carry their own
  `@TestPropertySource` and therefore their own context.
- `LiveMailSmokeTest` runs only with `MAIL_LIVE_TEST=true`.

## Before you say "done"

For anything visible: build it, screenshot it at 1440 and 390, and read the
screenshot against the approved render. That step — not the suite — caught
the blue default theme, the truncated `₹26,1…`, the dangling "signed in to
as", the hidden `?` on phones, and the wrong focus target. Then run the
suite, in isolation, and quote the numbers.
