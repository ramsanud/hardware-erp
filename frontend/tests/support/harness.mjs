/**
 * The whole test harness. Deliberately about 120 lines.
 *
 * This project had no frontend test runner at all (CLAUDE.md, "Not present"),
 * and every fix in BUG_REGISTRY.md is recorded as "no regression test". The
 * smallest thing that changes that is a plain Node script driving the built
 * app in a real browser - not a test framework with its own config, plugins,
 * fixtures and vocabulary to keep working. Playwright is already in
 * node_modules and does the hard part (a real Chromium); everything else here
 * is a list of assertions and an exit code.
 *
 * The suites run against `vite preview`, i.e. the PRODUCTION build, not the
 * dev server. Two of the defects these tests cover (BUG-FE-026's Tailwind
 * class, BUG-FE-028's :has() rule) live in generated CSS, and a dev-server run
 * would not have exercised the file that actually ships.
 */
import { chromium } from 'playwright';

export const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4173';

/** Collects results for one spec file. */
export function suite(name) {
  const results = [];
  return {
    name,
    results,
    /** `detail` is printed on pass as well as fail - a passing assertion that shows its measured value is worth ten that just say PASS. */
    check(label, passed, detail = '') {
      results.push({ label, passed, detail });
      console.log(`  ${passed ? 'PASS' : 'FAIL'}  ${label}${detail ? `  — ${detail}` : ''}`);
      return passed;
    },
    get failed() {
      return results.filter((r) => !r.passed);
    },
  };
}

export async function withBrowser(fn) {
  const browser = await chromium.launch();
  try {
    return await fn(browser);
  } finally {
    await browser.close();
  }
}

/**
 * A page with the API stubbed and a recorder for anything the app throws.
 *
 * ONE route handler, not several. Playwright matches routes in REVERSE
 * registration order, so a broad `**\/v1/auth/**` stub silently shadows a
 * specific `/refresh` one and the app never signs in - which cost an hour the
 * first time and is exactly the kind of thing this comment exists to prevent.
 */
export async function newPage(browser, { viewport, mobile = false, api = () => null } = {}) {
  const context = await browser.newContext({
    viewport: viewport ?? { width: 1280, height: 800 },
    isMobile: mobile,
    hasTouch: mobile,
  });
  const page = await context.newPage();

  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e.message)));
  page.on('console', (m) => {
    if (m.type() !== 'error') return;
    const text = m.text();
    // Network noise from stubbed-away endpoints is not an application error.
    if (/Failed to load resource|net::ERR|favicon|Download the React DevTools/i.test(text)) return;
    errors.push(`console: ${text}`);
  });

  await page.route('**/api/**', (route) => {
    const url = route.request().url();
    const body = api(url, route);
    return route.fulfill(body ?? envelope(null));
  });

  page.__errors = errors;
  return page;
}

/** The ApiResponse envelope every endpoint returns (common/dto/ApiResponse.java). */
export function envelope(data) {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data, timestamp: new Date().toISOString() }),
  };
}

/** PageResponse (common/dto/PageResponse.java) with the given rows. */
export function pageOf(rows) {
  return envelope({
    content: rows,
    page: 0,
    size: 20,
    totalElements: rows.length,
    totalPages: rows.length ? 1 : 0,
    first: true,
    last: true,
  });
}

/** True when the document is wider than the viewport - i.e. the page scrolls sideways. */
export function hasHorizontalScroll(page) {
  return page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
}

/**
 * Viewport-relative geometry, read inside the page.
 *
 * NOT Playwright's boundingBox(): under mobile emulation that returns DOCUMENT
 * coordinates, which made a correctly positioned fixed bottom sheet look as
 * though it hung 400px below the fold and produced four confident false
 * failures. Anything position:fixed must be measured this way.
 */
export function rectOf(page, selector) {
  return page.evaluate((sel) => {
    const el = document.querySelector(sel);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return {
      top: +r.top.toFixed(1), bottom: +r.bottom.toFixed(1),
      left: +r.left.toFixed(1), right: +r.right.toFixed(1),
      width: +r.width.toFixed(1), height: +r.height.toFixed(1),
      vh: window.innerHeight, vw: window.innerWidth,
    };
  }, selector);
}
