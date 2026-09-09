# Frontend regression tests

```bash
npm run build      # the suite runs against dist/, not the dev server
npm run test:e2e   # all suites
node tests/run.mjs products   # one suite: auth | products | navigation
```

Under Git Bash the `npm run` shim fails with `'"node"' is not recognized` — a
known quirk of this machine (see CLAUDE.md). Use `node tests/run.mjs`, or run
the npm script from PowerShell.

To point at a server you are already running (a deployed preview, say):

```bash
E2E_BASE_URL=https://example.invalid node tests/run.mjs
```

## Why it is shaped like this

This project had no frontend test runner at all, and every entry in
`BUG_REGISTRY.md` is recorded as *"no regression test"*. The smallest thing
that changes that is a plain Node script driving the built app in a real
browser — not a framework with its own config, plugins, fixtures and
vocabulary to keep working. Playwright was already in `node_modules` and does
the hard part (a real Chromium); `support/harness.mjs` is about 120 lines and
is the entire rest of it.

**It runs against the production bundle** (`vite preview`), deliberately. Two
of the defects covered here live in generated CSS — BUG-FE-026's Tailwind
class and BUG-FE-028's `:has()` rule — and a dev-server run would not have
exercised the file that actually ships.

**The API is stubbed, not mocked away.** `support/fixtures.mjs` mirrors the
real DTOs (`ProductSummaryResponse`, `UserResponse`, `PageResponse`). Where a
stub drifts from its DTO the test stops testing the application and starts
testing the stub — an early version of the navigation sweep "found" three
crashes that were entirely its own fault.

## Layout

| Path | Covers |
|---|---|
| `auth/auth-flows.spec.mjs` | BUG-FE-024, BUG-FE-025a/b, CR-063 clear buttons and touch targets |
| `products/product-grid.spec.mjs` | CR-063 status colours, BUG-FE-026 (phone card price), BUG-FE-028 (sticky dialog header) |
| `navigation/responsive.spec.mjs` | 25 routes × 5 viewports: renders, no horizontal scroll, no uncaught errors |

A spec exports one `async function` returning a `suite()`; `run.mjs` starts
the preview server, runs each in turn, and exits non-zero if any assertion
failed.

## Two traps worth knowing before adding tests

**Playwright's `boundingBox()` returns *document* coordinates under mobile
emulation.** It made a correctly positioned fixed bottom sheet look as though
it hung 400px below the fold, and produced four confident false failures.
Anything `position: fixed` must be measured with `getBoundingClientRect()`
inside `page.evaluate` — `rectOf()` in the harness does this.

**Playwright matches routes in reverse registration order.** A broad
`**/v1/auth/**` stub silently shadows a specific `/refresh` one and the app
never signs in. The harness therefore takes exactly one route handler and
switches on the URL inside it.

## What this is not

It is not a substitute for the backend suite, and it does not assert business
rules — a stubbed API will happily agree with whatever the frontend believes.
It answers "does the application still render and behave everywhere", which is
the question a change to a shared primitive (`Table`, `Dialog`, `StatusBadge`,
`AppLayout`) actually needs answered.
