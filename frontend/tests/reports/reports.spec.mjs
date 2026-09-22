/**
 * CR-086 / CR-087 — the Reports screen.
 *
 * Every figure on these pages is a server display string, so the things to
 * assert are the ones the page itself decides: which tabs a role sees, that
 * the report in the URL is the one rendered, that the filters drive the
 * request, that the totals row and the empty state appear when they should,
 * that a download asks for the same filters as the table, and that a
 * manager without REPORT_FINANCIAL never sees GSTR-1.
 */
import { BASE, suite, withBrowser, newPage, envelope } from '../support/harness.mjs';
import { signedInApi, OWNER } from '../support/fixtures.mjs';

const MANAGER = { ...OWNER, id: 2, fullName: 'Meena Manager', roleCode: 'MANAGER', roleName: 'Manager',
  permissions: OWNER.permissions.filter((p) => p !== 'REPORT_FINANCIAL') };

const money = (paise) => ({ paise, display: (paise / 100).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) });

function dayBook(from, to, empty = false) {
  const entries = empty ? [] : [
    { date: from, kind: 'SALE', reference: 'INV-2026-0042', party: 'Ravi Builders', detail: 'Unpaid', amountPaise: 129800, amountDisplay: '1,298.00' },
    { date: from, kind: 'RECEIPT', reference: 'INV-2026-0042', party: 'Ravi Builders', detail: 'Cash', amountPaise: 30000, amountDisplay: '300.00' },
    { date: to, kind: 'EXPENSE', reference: 'EXP-7', party: 'Tea & snacks', detail: 'Cash', amountPaise: 12000, amountDisplay: '120.00' },
  ];
  return { period: { from, to }, entries, totals: {
    salesPaise: 129800, salesDisplay: '1,298.00', receiptsPaise: 30000, receiptsDisplay: '300.00',
    creditNotesPaise: 0, creditNotesDisplay: '0.00', purchasesPaise: 0, purchasesDisplay: '0.00',
    expensesPaise: 12000, expensesDisplay: '120.00', netCashPaise: 18000, netCashDisplay: '180.00' } };
}

const AGEING = { asOf: '2026-09-15', rows: [
  { customerId: 7, customerName: 'Ravi Builders', mobileNo: '9898989898', current0To30Paise: 99800, current0To30Display: '998.00',
    days31To60Paise: 0, days31To60Display: '0.00', days61To90Paise: 0, days61To90Display: '0.00', over90Paise: 450000, over90Display: '4,500.00',
    totalPaise: 549800, totalDisplay: '5,498.00', openInvoices: 2 },
], totals: { current0To30Paise: 99800, current0To30Display: '998.00', days31To60Paise: 0, days31To60Display: '0.00',
  days61To90Paise: 0, days61To90Display: '0.00', over90Paise: 450000, over90Display: '4,500.00', totalPaise: 549800, totalDisplay: '5,498.00' } };

const STOCK = { asOf: '2026-09-15', rows: [
  { productId: 1, productCode: 'PRD-000010', productName: 'Godrej Duplex Lock 70mm', categoryName: 'Locks', unit: 'PCS', quantityOnHand: 12,
    purchasePricePaise: 35000, purchasePriceDisplay: '350.00', sellingPricePaise: 55000, sellingPriceDisplay: '550.00',
    costValuePaise: 420000, costValueDisplay: '4,200.00', sellingValuePaise: 660000, sellingValueDisplay: '6,600.00' },
], totals: { products: 1, costValuePaise: 420000, costValueDisplay: '4,200.00', sellingValuePaise: 660000, sellingValueDisplay: '6,600.00' } };

const GST = (from, to) => {
  const row = (rate, taxable, tax) => ({ ratePercent: rate, ...withTax(taxable, tax) });
  const withTax = (taxable, tax) => ({ taxablePaise: taxable, taxableDisplay: money(taxable).display,
    cgstPaise: tax / 2, cgstDisplay: money(tax / 2).display, sgstPaise: tax / 2, sgstDisplay: money(tax / 2).display,
    igstPaise: 0, igstDisplay: '0.00', totalTaxPaise: tax, totalTaxDisplay: money(tax).display });
  return { period: { from, to },
    outward: { title: 'Outward supplies (sales)', rows: [row(18, 110000, 19800)], totals: { ratePercent: null, ...withTax(110000, 19800) } },
    creditNotes: { title: 'Credit notes issued', rows: [], totals: { ratePercent: null, ...withTax(0, 0) } },
    inward: { title: 'Inward supplies (purchases)', rows: [row(18, 50000, 9000)], totals: { ratePercent: null, ...withTax(50000, 9000) } },
    netCgstPaise: 5400, netCgstDisplay: '54.00', netSgstPaise: 5400, netSgstDisplay: '54.00', netIgstPaise: 0, netIgstDisplay: '0.00',
    netTaxPaise: 10800, netTaxDisplay: '108.00' };
};

const GSTR1 = { gstin: '33AABCS1429B1Z1', fp: '092026',
  b2b: [{ ctin: '33AACCK7821M1ZD', inv: [{}, {}] }, { ctin: '27AAPFU0939F1ZV', inv: [{}] }],
  b2cl: [], b2cs: [{}, {}, {}], cdnr: [], cdnur: [], hsn: { data: [{}, {}, {}, {}] } };

function reportsApi({ user = OWNER, emptyDayBook = false, requests } = {}) {
  const base = signedInApi({ user });
  return (url, route) => {
    const u = new URL(url);
    if (requests) requests.push(u.pathname + u.search);
    const from = u.searchParams.get('from') ?? '2026-09-01';
    const to = u.searchParams.get('to') ?? '2026-09-15';
    if (u.pathname.endsWith('/export') || u.pathname.endsWith('/gstr1/download')) {
      return { status: 200, contentType: u.pathname.endsWith('.json') ? 'application/json' : 'application/pdf', body: '%PDF-stub' };
    }
    if (u.pathname.endsWith('/v1/reports/day-book')) return envelope(dayBook(from, to, emptyDayBook));
    if (u.pathname.endsWith('/v1/reports/receivables-ageing')) return envelope(AGEING);
    if (u.pathname.endsWith('/v1/reports/stock-valuation')) return envelope(STOCK);
    if (u.pathname.endsWith('/v1/reports/purchase-register')) {
      return envelope({ period: { from, to }, rows: [], totals: { bills: 0, taxablePaise: 0, taxableDisplay: '0.00', cgstPaise: 0, cgstDisplay: '0.00',
        sgstPaise: 0, sgstDisplay: '0.00', igstPaise: 0, igstDisplay: '0.00', totalPaise: 0, totalDisplay: '0.00', paidPaise: 0, paidDisplay: '0.00', balancePaise: 0, balanceDisplay: '0.00' } });
    }
    if (u.pathname.endsWith('/v1/reports/gst-summary')) return envelope(GST(from, to));
    if (u.pathname.endsWith('/v1/reports/gstr1')) return envelope(GSTR1);
    return base(url, route);
  };
}

export default async function run() {
  const s = suite('reports');

  await withBrowser(async (browser) => {
    // ---- Owner: every tab, day book by default ------------------------------
    const requests = [];
    const page = await newPage(browser, { viewport: { width: 1440, height: 900 }, api: reportsApi({ requests }) });
    await page.goto(BASE + '/reports', { waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForTimeout(400);

    s.check('/reports lands on the day book', page.url().endsWith('/reports/day-book'), page.url());
    const tabs = await page.$$eval('[data-report-tab]', (els) => els.map((e) => e.getAttribute('data-report-tab')));
    s.check('the owner sees all six tabs incl. GSTR-1', tabs.join(',') === 'day-book,receivables-ageing,stock-valuation,purchase-register,gst-summary,gstr1', tabs.join(','));

    let text = await page.evaluate(() => document.body.innerText);
    s.check('day book rows carry the voucher type and the server figure', text.includes('Sale') && text.includes('Receipt') && text.includes('₹1,298.00'), '₹1,298.00');
    s.check('the totals strip shows net cash', text.includes('Net cash') && text.includes('₹180.00'), '₹180.00');
    s.check('the sidebar Reports entry is live', await page.$('aside a[href="/reports"]') !== null, 'link present');

    // ---- Presets drive the request --------------------------------------------
    requests.length = 0;
    await page.click('[data-range-preset="today"]');
    await page.waitForTimeout(400);
    // BUG-FE-041: the page builds "today" from the browser's LOCAL date (a shop in
    // India means IST), so the expectation must too - toISOString() is UTC and
    // disagrees with it between 00:00 and 05:30 IST, which is when this failed.
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    const dayReq = requests.find((r) => r.startsWith('/api/v1/reports/day-book'));
    s.check('"Today" preset requests from=to=today', !!dayReq && dayReq.includes(`from=${today}`) && dayReq.includes(`to=${today}`), dayReq ?? 'no request');

    // ---- Download asks for the same filters -----------------------------------
    requests.length = 0;
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 5000 }).catch(() => null),
      page.click('[data-report-downloads] button:has-text("PDF")'),
    ]);
    await page.waitForTimeout(300);
    const exportReq = requests.find((r) => r.includes('/day-book/export'));
    s.check('PDF export carries the table\'s own dates and format=pdf', !!exportReq && exportReq.includes(`from=${today}`) && exportReq.includes('format=pdf'), exportReq ?? 'no request');
    s.check('the browser receives a file named after the report', !!download && download.suggestedFilename().startsWith('day-book-'), download ? download.suggestedFilename() : 'no download');

    // ---- Receivables: totals row and the over-90 tone ---------------------------
    await page.click('[data-report-tab="receivables-ageing"]');
    await page.waitForTimeout(400);
    s.check('the URL follows the tab', page.url().endsWith('/reports/receivables-ageing'), page.url());
    text = await page.evaluate(() => document.body.innerText);
    s.check('ageing shows the customer and a totals row', text.includes('Ravi Builders') && (await page.$('[data-report-totals]')) !== null, 'totals row');
    s.check('the over-90 figure is drawn in the destructive tone',
      await page.$eval('[data-report-table] tbody tr:first-child', (tr) => !!tr.querySelector('.text-destructive')), 'text-destructive');

    // ---- Stock valuation and GST summary render their sections -------------------
    await page.click('[data-report-tab="stock-valuation"]');
    await page.waitForTimeout(400);
    text = await page.evaluate(() => document.body.innerText);
    s.check('stock valuation states its basis honestly', text.includes('current purchase price'), 'note present');
    s.check('stock valuation totals the cost value', text.includes('₹4,200.00'), '₹4,200.00');

    await page.click('[data-report-tab="gst-summary"]');
    await page.waitForTimeout(400);
    const sections = await page.$$eval('[data-gst-section]', (els) => els.map((e) => e.getAttribute('data-gst-section')));
    s.check('GST summary renders outward, credit notes and inward sections', sections.length === 3 && sections[0].startsWith('Outward'), sections.join(' | '));
    text = await page.evaluate(() => document.body.innerText);
    s.check('the net payable is shown', text.includes('Net payable') && text.includes('₹108.00'), '₹108.00');
    s.check('an empty section says so instead of drawing a bare header', text.includes('Nothing in this period'), 'empty copy');

    // ---- GSTR-1 counts come from the document ----------------------------------
    await page.click('[data-report-tab="gstr1"]');
    await page.waitForTimeout(400);
    text = await page.evaluate(() => document.body.innerText);
    s.check('GSTR-1 shows the GSTIN and period it will file', text.includes('GSTIN 33AABCS1429B1Z1') && text.includes('092026'), 'header');
    s.check('B2B count is invoices across buyers, not buyers', /B2B INVOICES\s+3/i.test(text.replace(/\n/g, ' ')), 'B2B 3');
    s.check('the download button is present and enabled', await page.$eval('[data-gstr1-download]', (b) => !b.disabled), 'enabled');

    s.check('no console errors on any report', page.__errors.length === 0, page.__errors.join(' | ') || 'clean');
    await page.context().close();

    // ---- Manager: no REPORT_FINANCIAL, no GSTR-1 tab, deep link redirects ---------
    const manager = await newPage(browser, { viewport: { width: 1440, height: 900 }, api: reportsApi({ user: MANAGER }) });
    await manager.goto(BASE + '/reports/gstr1', { waitUntil: 'networkidle', timeout: 20000 });
    await manager.waitForTimeout(400);
    const managerTabs = await manager.$$eval('[data-report-tab]', (els) => els.map((e) => e.getAttribute('data-report-tab')));
    s.check('a manager sees five tabs and no GSTR-1', managerTabs.length === 5 && !managerTabs.includes('gstr1'), managerTabs.join(','));
    s.check('a manager deep-linking to GSTR-1 lands on the day book', manager.url().endsWith('/reports/day-book'), manager.url());
    await manager.context().close();

    // ---- Empty period and the phone ---------------------------------------------
    const phone = await newPage(browser, { viewport: { width: 390, height: 844 }, mobile: true, api: reportsApi({ emptyDayBook: true }) });
    await phone.goto(BASE + '/reports/day-book', { waitUntil: 'networkidle', timeout: 20000 });
    await phone.waitForTimeout(400);
    const phoneText = await phone.evaluate(() => document.body.innerText);
    s.check('an empty period shows the empty state, not a blank table', phoneText.includes('No vouchers in this period'), 'empty state');
    const overflow = await phone.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);
    s.check('no horizontal scroll on a phone', !overflow, 'within viewport');
    s.check('the tab strip is reachable on a phone', await phone.$('[data-report-tab="gstr1"]') !== null, 'present');
    await phone.context().close();
  });

  return s;
}
