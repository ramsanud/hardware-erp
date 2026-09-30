/**
 * BUG-FE-036 — the sidebar active state must actually render.
 *
 * The styling for it existed, with a comment explaining the accent bar, and
 * had never once applied: `.sidebar-link[data-active='true']` needs the class
 * and the attribute on ONE element, and the component put the class on the
 * anchor and data-active on a span inside it. Every route drew a transparent,
 * muted, unmarked "active" row.
 *
 * A CSS rule that silently matches nothing is invisible to a typecheck, a
 * build and a render test alike - the page still renders, it just renders
 * wrong - so this asserts computed style, which is the only level at which
 * the defect is observable.
 */
import { BASE, suite, withBrowser, newPage, hasHorizontalScroll } from '../support/harness.mjs';
import { signedInApi } from '../support/fixtures.mjs';

const TRANSPARENT = ['rgba(0, 0, 0, 0)', 'transparent'];

export default async function run() {
  const s = suite('sidebar');

  await withBrowser(async (browser) => {
    // lg and up: the rail only exists above the mobile tab-bar breakpoint.
    const page = await newPage(browser, { viewport: { width: 1440, height: 900 }, api: signedInApi() });

    for (const [route, label] of [['/products', 'Products'], ['/invoices', 'Invoices'], ['/suppliers', 'Suppliers']]) {
      await page.goto(BASE + route, { waitUntil: 'networkidle', timeout: 20000 });
      await page.waitForTimeout(200);

      const active = await page.evaluate(() => {
        const link = document.querySelector('aside a[aria-current="page"]');
        if (!link) return null;
        const cs = getComputedStyle(link);
        const pill = getComputedStyle(link, '::before');
        return {
          href: link.getAttribute('href'),
          text: link.innerText.trim(),
          background: cs.backgroundColor,
          color: cs.color,
          fontWeight: Number(cs.fontWeight),
          pillContent: pill.content,
          pillWidth: pill.width,
        };
      });

      s.check(`${route}: exactly one link is marked current`,
        active !== null, active ? `${active.text}` : 'no aria-current link in the rail');
      if (!active) continue;

      s.check(`${route}: the current link is the matching one`,
        active.href === route && active.text === label, `${active.text} (${active.href})`);

      // The three properties the defect suppressed, asserted separately so a
      // failure says which half of the styling went missing.
      s.check(`${route}: the active row has a filled background`,
        !TRANSPARENT.includes(active.background), active.background);

      s.check(`${route}: the active row is emphasised`,
        active.fontWeight >= 600, `font-weight ${active.fontWeight}`);

      s.check(`${route}: the left accent pill is drawn`,
        active.pillContent !== 'none' && active.pillWidth !== 'auto',
        `content ${active.pillContent}, width ${active.pillWidth}`);
    }

    // Requirement from the redesign brief, and the thing most likely to break
    // when a rail animates between two widths.
    for (const collapse of [true, false]) {
      const button = page.getByRole('button', { name: collapse ? 'Collapse sidebar' : 'Expand sidebar' }).first();
      await button.click();
      await page.waitForTimeout(450);
      const rail = await page.evaluate(() => {
        const aside = document.querySelector('aside');
        return {
          width: Math.round(aside.getBoundingClientRect().width),
          sideways: aside.scrollWidth > aside.clientWidth,
          border: getComputedStyle(aside).borderRightWidth,
        };
      });
      const state = collapse ? 'collapsed' : 'expanded';
      s.check(`${state}: the rail is at its ${collapse ? 'mini' : 'full'} width`,
        collapse ? rail.width < 100 : rail.width > 200, `${rail.width}px`);
      s.check(`${state}: the rail does not scroll sideways`, !rail.sideways, `scrollWidth vs clientWidth`);
      s.check(`${state}: the rail keeps its separator border`,
        rail.border !== '0px', `border-right ${rail.border}`);
      s.check(`${state}: no horizontal page scroll`,
        !(await hasHorizontalScroll(page)), 'document within viewport');
    }

    await page.context().close();

    // BUG-FE-039. The fixture user has no avatar (hasAvatar: false on /me),
    // so no view of the shell - rail footer, top bar, profile page - may ask
    // /me/avatar for one. Before the fix every page load fired that GET and
    // took a 404 for it.
    {
      const avatarPage = await newPage(browser, { viewport: { width: 1440, height: 900 }, api: signedInApi() });
      const avatarRequests = [];
      avatarPage.on('request', (r) => { if (r.url().includes('/v1/auth/me/avatar')) avatarRequests.push(r.method()); });
      for (const route of ['/dashboard', '/products', '/profile']) {
        await avatarPage.goto(BASE + route, { waitUntil: 'networkidle', timeout: 20000 });
      }
      s.check('no avatar request is made for an account that has no avatar',
        avatarRequests.length === 0, `${avatarRequests.length} request(s) to /me/avatar`);
      await avatarPage.context().close();
    }
  });

  return s;
}
