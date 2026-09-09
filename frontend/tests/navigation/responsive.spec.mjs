/**
 * The broad sweep: every routed screen, at every breakpoint the project
 * supports, asserting the three things that must never be true anywhere.
 *
 * It deliberately does NOT assert what each page contains - that is the job of
 * a page's own spec. This one answers "does the application still render
 * everywhere", which is the question a refactor of a shared primitive
 * (Table, Dialog, StatusBadge, AppLayout) actually needs answered, and the one
 * that would otherwise be answered by clicking 125 times.
 */
import { BASE, suite, withBrowser, newPage, hasHorizontalScroll } from '../support/harness.mjs';
import { signedInApi, VIEWPORTS } from '../support/fixtures.mjs';

const ROUTES = [
  '/dashboard',
  '/products', '/products/categories', '/products/brands',
  '/suppliers', '/customers',
  '/invoices', '/quotations', '/purchases',
  '/stock', '/payments', '/expenses', '/coupons',
  '/projects', '/labour/workers', '/labour/attendance',
  '/users', '/roles', '/permissions', '/security-audit-log', '/activity-log',
  '/profile', '/profile/appearance',
  '/settings/shop', '/tools/gst-calculator', '/support',
];

export default async function run() {
  const s = suite('navigation');

  await withBrowser(async (browser) => {
    for (const vp of VIEWPORTS) {
      const page = await newPage(browser, {
        viewport: { width: vp.width, height: vp.height },
        mobile: vp.mobile,
        api: signedInApi(),
      });

      const overflowed = [];
      const broken = [];

      for (const route of ROUTES) {
        try {
          await page.goto(BASE + route, { waitUntil: 'networkidle', timeout: 20000 });
          await page.waitForTimeout(120);
          const state = await page.evaluate(() => ({
            notFound: document.body.innerText.includes('Page not found'),
            blank: document.body.innerText.trim().length < 20,
            width: document.documentElement.scrollWidth,
          }));
          if (await hasHorizontalScroll(page)) overflowed.push(`${route} (${state.width}px)`);
          if (state.notFound) broken.push(`${route} [404]`);
          else if (state.blank) broken.push(`${route} [blank]`);
        } catch (e) {
          broken.push(`${route} [${String(e.message).slice(0, 40)}]`);
        }
      }

      s.check(`${vp.name} ${vp.width}px: every route renders`,
        broken.length === 0, broken.join(', '));
      s.check(`${vp.name} ${vp.width}px: no horizontal page scroll anywhere`,
        overflowed.length === 0, overflowed.join(', '));
      s.check(`${vp.name} ${vp.width}px: no uncaught errors`,
        page.__errors.length === 0, [...new Set(page.__errors)].slice(0, 2).join(' | '));

      await page.context().close();
    }
  });

  return s;
}
