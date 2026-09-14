/**
 * CR-083 - the quotations list: KPI cards, status pills, the row menu, the
 * empty state with its two buttons, and the phone layout. The API is stubbed
 * at the network edge; what is asserted is what a person sees and can press.
 */
import { BASE, suite, withBrowser, newPage, envelope, pageOf, hasHorizontalScroll } from '../support/harness.mjs';
import { signedInApi } from '../support/fixtures.mjs';

const today = new Date();
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const plusDays = (n) => { const d = new Date(today); d.setDate(d.getDate() + n); return iso(d); };

const QUOTATIONS = [
  { id: 1, quotationNumber: 'QUO-000001', customerName: 'Ramesh Traders', customerMobile: '9876500001',
    quotationDate: '2026-09-10', validUntil: plusDays(2), expired: false, totalDisplay: '12,450.00', status: 'DRAFT' },
  { id: 2, quotationNumber: 'QUO-000002', customerName: 'Lakshmi Builders', customerMobile: '9876500002',
    quotationDate: '2026-09-11', validUntil: plusDays(20), expired: false, totalDisplay: '3,000.00', status: 'SENT' },
  { id: 3, quotationNumber: 'QUO-000003', customerName: 'Anand Hardware', customerMobile: '9876500003',
    quotationDate: '2026-09-01', validUntil: '2026-09-05', expired: true, totalDisplay: '900.00', status: 'SENT' },
  { id: 4, quotationNumber: 'QUO-000004', customerName: 'Priya Interiors', customerMobile: '9876500004',
    quotationDate: '2026-09-02', validUntil: plusDays(30), expired: false, totalDisplay: '48,000.00', status: 'CONVERTED' },
  { id: 5, quotationNumber: 'QUO-000005', customerName: 'Selvam Constructions', customerMobile: '9876500005',
    quotationDate: '2026-09-03', validUntil: plusDays(12), expired: false, totalDisplay: '1,25,000.00', status: 'ACCEPTED' },
];

const STATS = {
  totalCount: 5, totalValueDisplay: '1,89,350.00',
  pendingCount: 2, pendingValueDisplay: '15,450.00',
  approvedCount: 2, approvedValueDisplay: '1,73,000.00',
  closedCount: 1, closedValueDisplay: '900.00',
};

/** Records every quotation call so the spec can assert what the page asked for. */
function quotationApi(calls) {
  const base = signedInApi();
  return (url) => {
    if (url.includes('/v1/quotations/stats')) { calls.push(url); return envelope(STATS); }
    if (url.includes('/v1/quotations?') || url.endsWith('/v1/quotations')) {
      calls.push(url);
      const status = new URL(url).searchParams.get('status');
      const rows = status
        ? QUOTATIONS.filter((q) => (status === 'EXPIRED' ? q.expired : q.status === status && !q.expired))
        : QUOTATIONS;
      return pageOf(rows);
    }
    if (/\/v1\/quotations\/\d+$/.test(url)) return envelope({ ...QUOTATIONS[0], items: [] });
    return base(url);
  };
}

export default async function run() {
  const s = suite('quotations');

  await withBrowser(async (browser) => {
    // ---------------------------- desktop ----------------------------
    const calls = [];
    const desktop = await newPage(browser, { viewport: { width: 1440, height: 900 }, api: quotationApi(calls) });
    await desktop.goto(`${BASE}/quotations`, { waitUntil: 'networkidle' });
    await desktop.waitForSelector('table tbody tr', { timeout: 15000 });

    // KPI cards
    s.check('four KPI cards render above the toolbar',
      (await desktop.locator('[data-testid="quotation-kpis"] > *').count()) === 4);
    s.check('Total shows the count and the rupee value from /stats, not the page',
      (await desktop.locator('[data-testid="kpi-totalCount"]').innerText()).replace(/\s+/g, ' ').includes('5')
      && (await desktop.locator('[data-testid="kpi-totalCount"]').innerText()).includes('₹1,89,350.00'));
    s.check('Pending / Approved / Closed cards carry their counts',
      (await desktop.locator('[data-testid="kpi-pendingCount-count"]').innerText()) === '2'
      && (await desktop.locator('[data-testid="kpi-approvedCount-count"]').innerText()) === '2'
      && (await desktop.locator('[data-testid="kpi-closedCount-count"]').innerText()) === '1');
    s.check('the stats call never carries the status pill',
      calls.filter((u) => u.includes('/stats')).every((u) => !u.includes('status=')));

    // Table content
    const first = await desktop.locator('table tbody tr:has-text("QUO-000001")').innerText();
    s.check('a row shows number, formatted date, customer with +91 mobile and the amount',
      first.includes('QUO-000001') && first.includes('10 Sep 2026') && first.includes('Ramesh Traders')
      && first.includes('+91 9876500001') && first.includes('₹12,450.00'), JSON.stringify(first.replace(/\n/g, ' | ')));
    s.check('a live quotation expiring within 3 days carries the alert badge',
      (await desktop.locator('table tbody tr:has-text("QUO-000001") [data-testid="expiring-soon"]').count()) === 1
      && (await desktop.locator('table tbody tr:has-text("QUO-000002") [data-testid="expiring-soon"]').count()) === 0);
    s.check('an expired SENT quotation is badged Expired, not Sent',
      (await desktop.locator('table tbody tr:has-text("QUO-000003")').innerText()).includes('Expired'));

    // Status badge palette - five distinct hues, each with its word
    const colourOf = async (rowText, word) => desktop.locator(`table tbody tr:has-text("${rowText}")`)
      .locator('span', { hasText: new RegExp(`^${word}$`) }).first().evaluate((el) => getComputedStyle(el).color);
    const draft = await colourOf('QUO-000001', 'Draft');
    const sent = await colourOf('QUO-000002', 'Sent');
    const expired = await colourOf('QUO-000003', 'Expired');
    const converted = await colourOf('QUO-000004', 'Converted');
    const accepted = await colourOf('QUO-000005', 'Accepted');
    // Five, and Accepted is in the set on purpose: on the Emerald theme a Sent
    // badge on --primary was the same green as Accepted on --success.
    s.check('Draft, Sent, Accepted, Expired and Converted are five different colours',
      new Set([draft, sent, accepted, expired, converted]).size === 5, [draft, sent, accepted, expired, converted].join(' / '));

    // Status pills drive the list
    await desktop.getByRole('tab', { name: 'Expired' }).click();
    await desktop.waitForFunction(() => document.querySelectorAll('table tbody tr').length === 1);
    s.check('the Expired pill asks the API for status=EXPIRED and lists only the expired quote',
      calls.some((u) => u.includes('status=EXPIRED'))
      && (await desktop.locator('table tbody tr').innerText()).includes('QUO-000003'));
    await desktop.getByRole('tab', { name: 'All' }).click();
    await desktop.waitForFunction(() => document.querySelectorAll('table tbody tr').length === 5);

    // Row menu - a draft offers everything; a converted quote offers only PDF and WhatsApp
    await desktop.getByRole('button', { name: 'Actions for QUO-000001' }).click();
    const menu = desktop.getByRole('menu');
    const draftItems = (await menu.getByRole('menuitem').allInnerTexts()).map((t) => t.trim());
    s.check('a draft row offers PDF, WhatsApp, Convert, Edit and Delete',
      ['View / Print PDF', 'Send via WhatsApp', 'Convert to invoice', 'Edit quote', 'Delete draft']
        .every((item) => draftItems.includes(item)), draftItems.join(', '));
    await desktop.keyboard.press('Escape');
    await desktop.getByRole('button', { name: 'Actions for QUO-000004' }).click();
    const convertedItems = (await desktop.getByRole('menu').getByRole('menuitem').allInnerTexts()).map((t) => t.trim());
    s.check('a converted row offers neither Convert, Edit nor Delete',
      convertedItems.includes('View / Print PDF') && !convertedItems.some((t) => /Convert|Edit|Delete/.test(t)),
      convertedItems.join(', '));
    await desktop.keyboard.press('Escape');

    // Delete asks first, then calls DELETE and refreshes
    await desktop.getByRole('button', { name: 'Actions for QUO-000001' }).click();
    await desktop.getByRole('menuitem', { name: 'Delete draft' }).click();
    const confirm = desktop.getByRole('alertdialog').or(desktop.getByRole('dialog'));
    s.check('Delete opens a confirmation naming the draft',
      (await confirm.innerText()).includes('QUO-000001'));
    await desktop.keyboard.press('Escape');
    // While a Radix dialog is open (or still closing) the rest of the page is aria-hidden.
    await confirm.waitFor({ state: 'hidden', timeout: 5000 });

    // Toolbar buttons
    const toolbar = {
      columns: await desktop.getByRole('button', { name: /^Columns/ }).count(),
      exportCsv: await desktop.getByRole('button', { name: 'Export CSV' }).count(),
      create: await desktop.getByRole('button', { name: 'New quotation' }).count(),
    };
    s.check('Columns, Export CSV and New quotation sit on the toolbar',
      toolbar.columns >= 1 && toolbar.exportCsv === 1 && toolbar.create === 1, JSON.stringify(toolbar));
    s.check('no horizontal page scroll on desktop', !(await hasHorizontalScroll(desktop)));
    s.check('desktop: no page errors', desktop.__errors.length === 0, desktop.__errors.join(' | '));

    // ---------------------------- empty state ----------------------------
    const empty = await newPage(browser, {
      viewport: { width: 1440, height: 900 },
      api: (url) => {
        if (url.includes('/v1/quotations/stats')) return envelope({ ...STATS, totalCount: 0, totalValueDisplay: '0.00', pendingCount: 0, approvedCount: 0, closedCount: 0 });
        if (url.includes('/v1/quotations')) return pageOf([]);
        return signedInApi()(url);
      },
    });
    await empty.goto(`${BASE}/quotations`, { waitUntil: 'networkidle' });
    await empty.waitForSelector('[data-testid="quotation-empty"]', { timeout: 15000 });
    s.check('an unfiltered empty list offers Create but not Clear filters',
      (await empty.getByRole('button', { name: 'Create new quotation' }).count()) === 1
      && (await empty.getByRole('button', { name: 'Clear filters' }).count()) === 0);
    await empty.getByRole('tab', { name: 'Sent' }).click();
    // The pill sets `filtered` before the refetch starts, so Clear filters
    // paints, vanishes under the skeleton, then returns. Wait for the fetch
    // to settle rather than catch the first paint and count during the gap.
    await empty.waitForLoadState('networkidle');
    await empty.getByRole('button', { name: 'Clear filters' }).waitFor({ timeout: 5000 });
    s.check('with a filter set, the empty state adds Clear filters',
      (await empty.getByRole('button', { name: 'Clear filters' }).count()) === 1);
    s.check('the empty state draws its illustration',
      (await empty.locator('[data-testid="quotation-empty"] > svg').count()) === 1);
    await empty.getByRole('button', { name: 'Clear filters' }).click();
    s.check('Clear filters returns the pills to All',
      (await empty.getByRole('tab', { name: 'All' }).getAttribute('aria-selected')) === 'true');

    // ---------------------------- phone ----------------------------
    const phone = await newPage(browser, { viewport: { width: 390, height: 844 }, mobile: true, api: quotationApi([]) });
    await phone.goto(`${BASE}/quotations`, { waitUntil: 'networkidle' });
    await phone.waitForSelector('table tbody tr', { timeout: 15000 });
    s.check('phone: KPI cards sit two per row',
      await phone.locator('[data-testid="quotation-kpis"]').evaluate((el) => getComputedStyle(el).gridTemplateColumns.split(' ').length === 2));
    const card = await phone.locator('table tbody tr:has-text("QUO-000001")').innerText();
    s.check('phone: the stacked card carries number, customer, amount and status',
      card.includes('QUO-000001') && card.includes('Ramesh Traders') && card.includes('₹12,450.00') && card.includes('Draft'),
      JSON.stringify(card.replace(/\n/g, ' | ')));
    s.check('phone: the row menu is reachable',
      await phone.getByRole('button', { name: 'Actions for QUO-000001' }).isVisible());
    s.check('phone: the pills scroll inside their strip, the page does not',
      !(await hasHorizontalScroll(phone)));
    s.check('phone: no page errors', phone.__errors.length === 0, phone.__errors.join(' | '));
  });

  return s;
}
