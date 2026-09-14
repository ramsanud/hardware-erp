/**
 * CR-082 — the approved dashboard and app shell.
 *
 * Asserts the things a person would notice if they regressed: the counter-
 * staff titles (a card that says "Invoices" again is a menu entry, not a
 * widget), the eight-card grid, the sparklines drawn ONLY where a real series
 * exists, and the rail's new shape - one Overview row, groups that fold, the
 * shop card, the person at the bottom.
 */
import { BASE, suite, withBrowser, newPage, envelope } from '../support/harness.mjs';
import { signedInApi, OWNER } from '../support/fixtures.mjs';

const day = (i) => new Date(Date.now() - (13 - i) * 86400000).toISOString().slice(0, 10);
const SERIES = [0, 0, 1200, 800, 0, 2500, 1800, 900, 3100, 0, 4200, 2600, 3900, 5100]
  .map((v, i) => ({ bucket: day(i), revenuePaise: v * 100, revenueDisplay: v.toFixed(2), invoiceCount: v ? 1 : 0 }));

/** The signed-in stub plus the dashboard's own endpoints, with figures and a real 14-day series. */
function dashboardApi({ trend = true } = {}) {
  const base = signedInApi();
  return (url, route) => {
    if (url.includes('/v1/dashboard/sales-summary')) {
      return envelope({
        totalSalesDisplay: '26,100.00', todaySalesDisplay: '5,100.00', outstandingCustomerBalanceDisplay: '8,450.00',
        todaySalesPaise: 510000, yesterdaySalesPaise: 390000,
      });
    }
    if (url.includes('/v1/analytics/revenue-trend')) {
      return envelope({ period: { from: day(0), to: day(13), granularity: 'day' }, points: trend ? SERIES : [], summary: 'stub' });
    }
    if (url.includes('/v1/analytics/sales-by-category')) return envelope({ period: {}, slices: [], summary: '' });
    if (url.includes('/v1/activity-log')) {
      return envelope({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0, first: true, last: true });
    }
    return base(url, route);
  };
}

export default async function run() {
  const s = suite('dashboard');

  await withBrowser(async (browser) => {
    const page = await newPage(browser, { viewport: { width: 1440, height: 900 }, api: dashboardApi() });
    await page.goto(BASE + '/dashboard', { waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForTimeout(600);
    const text = await page.evaluate(() => document.body.innerText);

    // ---- Header ------------------------------------------------------------
    s.check('the greeting eyebrow is present and time-of-day based',
      /GOOD (MORNING|AFTERNOON|EVENING),/.test(text), (text.match(/GOOD \w+,/) || ['none'])[0]);
    s.check('the title greets by first name',
      text.includes(`Welcome back, ${OWNER.fullName.split(' ')[0]}`), 'present');
    s.check('the shop-time chip shows a date and a clock',
      /Shop Time: \d{1,2}:\d{2}/.test(text), (text.match(/Shop Time: [^\n]+/) || ['none'])[0]);

    // ---- Widget titles are the counter-staff names, not the menu names ------
    for (const title of ['Total Sales', "Today's Earnings", 'Pending Payments', 'Low Stock Alerts',
      'Items Catalog', 'Wholesalers & Dealers', 'Bills Raised', 'Customer List',
      'Sales Growth', 'Top Selling Categories', 'Recent Actions', 'Recent Bills', 'Pending Estimates']) {
      s.check(`widget titled "${title}"`, text.includes(title), 'present');
    }
    s.check('no card still uses the old "Outstanding customer balance" label',
      !text.includes('Outstanding customer balance'), 'gone');

    // ---- Figures never truncate --------------------------------------------
    s.check('the total-sales figure renders in full', text.includes('₹26,100.00'), '₹26,100.00');
    s.check("today's figure renders in full", text.includes('₹5,100.00'), '₹5,100.00');

    // ---- Deltas and sparklines only where a real series exists --------------
    s.check('week-over-week delta is computed from the series',
      /\d+(\.\d)?% vs last week/.test(text), (text.match(/[\d.]+% vs last week/) || ['none'])[0]);
    s.check('day-over-day delta is computed from the summary',
      text.includes('30.8% vs yesterday'), '30.8%');
    const sparklines = await page.locator('svg[data-sparkline]').count();
    s.check('exactly two sparklines: Total Sales and Today\'s Earnings, none invented for the other two',
      sparklines === 2, `${sparklines} sparklines`);

    // ---- Quick actions ------------------------------------------------------
    for (const label of ['New quotation', 'New invoice', 'Add product', 'Add customer']) {
      const n = await page.getByRole('link', { name: label }).count();
      s.check(`quick action "${label}" is a real link`, n >= 1, `${n}`);
    }
    const addProduct = await page.getByRole('link', { name: 'Add product' }).first().getAttribute('href');
    s.check('Add product opens the create dialog, not just the list', addProduct === '/products?new=1', addProduct);

    // ---- The rail -------------------------------------------------------------
    const rail = await page.evaluate(() => {
      const aside = document.querySelector('aside');
      const active = aside?.querySelector('a[aria-current="page"]');
      const groups = [...aside?.querySelectorAll('button[aria-expanded]') ?? []].map((b) => b.innerText.trim());
      return {
        active: active?.innerText.trim() ?? null,
        groups,
        shopCard: aside?.innerText.includes('Hardware shop') ?? false,
        footer: aside?.innerText.includes('Owner') ?? false,
        help: aside?.innerText.includes('Help & Support') ?? false,
      };
    });
    s.check('Overview is the active rail row on /dashboard', rail.active === 'Overview', rail.active ?? 'none');
    s.check('the groups are collapsible rows',
      ['Sales', 'Projects', 'Purchase', 'Inventory', 'Accounting', 'Administration'].every((g) => rail.groups.includes(g)),
      rail.groups.join(', '));
    s.check('the shop identity card is in the rail', rail.shopCard, 'present');
    s.check('the signed-in person is at the foot of the rail', rail.footer, 'present');
    s.check('Help & Support is in the utility list', rail.help, 'present');

    // Folding a group hides its rows and the choice survives a reload.
    await page.getByRole('button', { name: /^Sales/ }).click();
    await page.waitForTimeout(150);
    let quotations = await page.locator('aside a[href="/quotations"]').count();
    s.check('folding Sales hides its rows', quotations === 0, `${quotations} visible`);
    await page.reload({ waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForTimeout(500);
    quotations = await page.locator('aside a[href="/quotations"]').count();
    s.check('the folded state survives a reload', quotations === 0, `${quotations} visible`);
    await page.getByRole('button', { name: /^Sales/ }).click();
    await page.waitForTimeout(150);
    quotations = await page.locator('aside a[href="/quotations"]').count();
    s.check('unfolding brings them back', quotations === 1, `${quotations} visible`);

    await page.context().close();

    // ---- With no series at all, no sparkline and an honest "—" delta --------
    const bare = await newPage(browser, { viewport: { width: 1440, height: 900 }, api: dashboardApi({ trend: false }) });
    await bare.goto(BASE + '/dashboard', { waitUntil: 'networkidle', timeout: 20000 });
    await bare.waitForTimeout(600);
    const bareSparklines = await bare.locator('svg[data-sparkline]').count();
    s.check('an empty series draws no sparkline', bareSparklines === 0, `${bareSparklines} sparklines`);
    await bare.context().close();

    // ---- Phone: one column, nothing clipped ----------------------------------
    const phone = await newPage(browser, { viewport: { width: 390, height: 844 }, mobile: true, api: dashboardApi() });
    await phone.goto(BASE + '/dashboard', { waitUntil: 'networkidle', timeout: 20000 });
    await phone.waitForTimeout(600);
    const overflow = await phone.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
    s.check('no horizontal scroll on a phone', !overflow, 'within viewport');
    const phoneText = await phone.evaluate(() => document.body.innerText);
    s.check('the phone shows the full figure too', phoneText.includes('₹26,100.00'), '₹26,100.00');
    await phone.context().close();
  });

  return s;
}
