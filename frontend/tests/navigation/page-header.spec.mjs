/**
 * BUG-FE-035 — a busy toolbar must not crush the page title.
 *
 * The responsive sweep next door visits list routes only, because a detail
 * route needs an id. That gap is exactly where this defect lived: Invoice
 * detail carries eight actions and every caller hands PageHeader its buttons
 * already wrapped in a div of its own, so the header row holds ONE flex item
 * 1039px wide. It took the space it asked for, and the title column was left
 * with 69px at 1440px ("INV-000027" rendered as "I...", the customer line
 * wrapping one word per line) and 0px at 768px, where the toolbar also ran
 * off the page - the BUG-FE-034 overflow, back on the pages that fix missed.
 *
 * These assertions fail against the old markup and pass against the new, and
 * they are written against measured geometry rather than class names so a
 * future refactor of PageHeader cannot satisfy them by accident.
 */
import { BASE, suite, withBrowser, newPage, hasHorizontalScroll } from '../support/harness.mjs';
import { signedInApi } from '../support/fixtures.mjs';

/** Wide enough to read a document number and its customer line. */
const MIN_TITLE_WIDTH = 200;

export default async function run() {
  const s = suite('page-header');

  await withBrowser(async (browser) => {
    for (const width of [1440, 768]) {
      const page = await newPage(browser, {
        viewport: { width, height: 900 },
        api: signedInApi(),
      });

      await page.goto(`${BASE}/invoices/53`, { waitUntil: 'networkidle', timeout: 20000 });
      await page.waitForSelector('h1', { timeout: 15000 }).catch(() => {});

      const m = await page.evaluate(() => {
        const h1 = document.querySelector('h1');
        if (!h1) return null;
        const titleCol = h1.parentElement;
        const header = titleCol.parentElement;
        const actions = header.children[1];
        return {
          titleWidth: Math.round(titleCol.getBoundingClientRect().width),
          actionsWidth: actions ? Math.round(actions.getBoundingClientRect().width) : 0,
          headerWidth: Math.round(header.getBoundingClientRect().width),
          // scrollWidth beating clientWidth is the browser telling us the
          // text did not fit the box it was given.
          titleClipped: h1.scrollWidth > h1.clientWidth + 1,
          descriptionHeight: titleCol.querySelector('p')
            ? Math.round(titleCol.querySelector('p').getBoundingClientRect().height)
            : 0,
        };
      });

      s.check(`${width}px: the invoice header rendered`, m !== null, m ? 'h1 found' : 'no h1 found');
      if (!m) { await page.context().close(); continue; }

      s.check(`${width}px: the title keeps a readable width`,
        m.titleWidth >= MIN_TITLE_WIDTH, `title column ${m.titleWidth}px (was 69px at 1440, 0px at 768)`);

      s.check(`${width}px: the invoice number is not clipped`,
        !m.titleClipped, m.titleClipped ? 'h1 scrollWidth exceeded clientWidth' : 'renders in full');

      // The sharpest signal the defect gives. "Bug Fix Test · 9123456700 ·
      // 2026-09-01" is short enough to sit on ONE ~20px line at 768px and
      // above; every extra line means the column it was given was too narrow.
      // Measured while broken: 40px at 1440, 60px at 768, 100px on the live
      // page whose eight-action toolbar is wider still.
      s.check(`${width}px: the customer line fits on one line`,
        m.descriptionHeight > 0 && m.descriptionHeight <= 24,
        `description ${m.descriptionHeight}px tall`);

      s.check(`${width}px: the toolbar stays inside the header`,
        m.actionsWidth <= m.headerWidth,
        `toolbar ${m.actionsWidth}px in a ${m.headerWidth}px header`);

      s.check(`${width}px: no horizontal page scroll`,
        !(await hasHorizontalScroll(page)), 'document did not exceed the viewport');

      await page.context().close();
    }
  });

  return s;
}
