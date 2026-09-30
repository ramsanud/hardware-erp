/**
 * CR-101 — the share sheet on a report, driven by the background job queue.
 *
 * The contract: clicking a channel queues a job in the chosen format (one
 * POST), polls its status until COMPLETED, then acts — a download, or a
 * download plus the server's wa.me link opened in a new window, or an email
 * POST. Every server figure is stubbed; the things asserted are the ones the
 * page itself decides: which format code was queued, that polling stopped
 * once the job finished, what was downloaded, and what was opened.
 */
import { BASE, suite, withBrowser, newPage, envelope } from '../support/harness.mjs';
import { signedInApi } from '../support/fixtures.mjs';

const DAY_BOOK = {
  period: { from: '2026-09-01', to: '2026-09-20' }, entries: [], totals: {
    salesPaise: 0, salesDisplay: '0.00', receiptsPaise: 0, receiptsDisplay: '0.00', creditNotesPaise: 0, creditNotesDisplay: '0.00',
    purchasesPaise: 0, purchasesDisplay: '0.00', expensesPaise: 0, expensesDisplay: '0.00', netCashPaise: 0, netCashDisplay: '0.00' },
};

const WA_LINK = { url: 'https://wa.me/?text=Please%20find%20the%20file', toMobileNo: null, message: 'Please find the file' };

/**
 * A job that is PENDING on creation and COMPLETED on the second status read —
 * so the poller has to run at least once, and stopping it is observable.
 */
function shareApi({ requests, jobs }) {
  const base = signedInApi();
  let nextId = 10;
  return (url, route) => {
    const u = new URL(url);
    const method = route.request().method();
    requests.push(`${method} ${u.pathname}${u.search}`);

    if (u.pathname.endsWith('/v1/reports/day-book')) return envelope(DAY_BOOK);

    if (u.pathname === '/api/v1/documents/jobs' && method === 'POST') {
      const body = route.request().postDataJSON();
      const job = { id: nextId++, reportType: body.reportType, format: body.format, status: 'PENDING', createdAt: new Date().toISOString(), polls: 0 };
      jobs.set(job.id, job);
      return { status: 202, contentType: 'application/json', body: JSON.stringify({ success: true, data: strip(job), timestamp: '' }) };
    }
    const status = u.pathname.match(/\/v1\/documents\/jobs\/(\d+)$/);
    if (status) {
      const job = jobs.get(Number(status[1]));
      job.polls += 1;
      if (job.polls >= 2) { job.status = 'COMPLETED'; job.fileName = `${job.reportType.toLowerCase().replace(/_/g, '-')}.${job.format.toLowerCase()}`; job.fileSizeBytes = 1234; }
      return envelope(strip(job));
    }
    if (/\/v1\/documents\/jobs\/\d+\/download$/.test(u.pathname)) {
      return { status: 200, contentType: 'application/octet-stream', body: 'file-bytes' };
    }
    if (/\/share\/whatsapp-link$/.test(u.pathname)) return envelope(WA_LINK);
    if (/\/share\/email$/.test(u.pathname)) return envelope('SENT');
    return base(url, route);
  };
}

function strip(job) {
  const { polls, ...rest } = job;
  return rest;
}

async function recordWindowOpen(page) {
  await page.addInitScript(() => {
    window.__opened = [];
    window.open = (url, target, features) => {
      window.__opened.push({ url: String(url), target, features });
      return null;
    };
  });
}

async function openSheet(page) {
  await page.goto(BASE + '/reports/day-book', { waitUntil: 'networkidle', timeout: 20000 });
  await page.waitForSelector('[data-report-share]', { timeout: 10000 });
  await page.click('[data-report-share]');
  await page.waitForSelector('[data-share-modal]', { timeout: 5000 });
}

export default async function run() {
  const s = suite('document-share');

  await withBrowser(async (browser) => {
    // ---- WhatsApp with a PDF: job queued, polled to completion, downloaded, link opened ----
    {
      const requests = []; const jobs = new Map();
      const page = await newPage(browser, { viewport: { width: 1280, height: 800 }, api: shareApi({ requests, jobs }) });
      await recordWindowOpen(page);
      await openSheet(page);

      const formats = await page.$$eval('[data-share-format]', (els) => els.map((e) => e.getAttribute('data-share-format')));
      s.check('the sheet offers PDF, Image and Excel', formats.join(',') === 'PDF,PNG,XLSX', formats.join(','));
      s.check('PDF is the default format', await page.$eval('[data-share-format="PDF"]', (e) => e.getAttribute('aria-checked')) === 'true', 'aria-checked');

      requests.length = 0;
      const [download] = await Promise.all([
        page.waitForEvent('download', { timeout: 10000 }).catch(() => null),
        page.click('[data-share-channel="WHATSAPP"]'),
      ]);
      await page.waitForFunction(() => window.__opened.length > 0, null, { timeout: 10000 }).catch(() => undefined);

      const post = requests.find((r) => r.startsWith('POST /api/v1/documents/jobs'));
      s.check('one job was queued', requests.filter((r) => r.startsWith('POST /api/v1/documents/jobs')).length === 1, post ?? 'no POST');
      const job = [...jobs.values()][0];
      s.check('the job asked for DAY_BOOK as PDF', job?.reportType === 'DAY_BOOK' && job?.format === 'PDF', `${job?.reportType} ${job?.format}`);
      const polls = requests.filter((r) => /GET \/api\/v1\/documents\/jobs\/\d+$/.test(r)).length;
      s.check('status was polled until COMPLETED and then no further', polls === 2, `${polls} polls`);
      s.check('the finished file was downloaded', !!download && download.suggestedFilename() === 'day-book.pdf', download ? download.suggestedFilename() : 'no download');
      const opened = await page.evaluate(() => window.__opened);
      s.check('the server\'s wa.me link was opened, unmodified', opened[0]?.url === WA_LINK.url, opened[0]?.url ?? 'nothing opened');
      s.check('the banner reports the job as ready', await page.$('[data-export-banner][data-export-status="COMPLETED"]') !== null, 'banner');
      s.check('no console errors', page.__errors.length === 0, page.__errors.join(' | ') || 'clean');
      await page.context().close();
    }

    // ---- Image format + Download, then Email through the same sheet ----
    {
      const requests = []; const jobs = new Map();
      const page = await newPage(browser, { viewport: { width: 1280, height: 800 }, api: shareApi({ requests, jobs }) });
      await recordWindowOpen(page);
      await openSheet(page);
      await page.click('[data-share-format="PNG"]');

      const [download] = await Promise.all([
        page.waitForEvent('download', { timeout: 10000 }).catch(() => null),
        page.click('[data-share-channel="DOWNLOAD"]'),
      ]);
      const job = [...jobs.values()][0];
      s.check('Image queues a PNG job', job?.format === 'PNG', job?.format);
      s.check('the PNG was downloaded under its server name', !!download && download.suggestedFilename() === 'day-book.png', download ? download.suggestedFilename() : 'no download');

      // Email: reveals the address field first, then sends against the same finished job.
      await page.click('[data-share-channel="EMAIL"]');
      await page.waitForSelector('[data-share-email] input', { timeout: 3000 });
      await page.fill('[data-share-email] input', 'owner@example.com');
      requests.length = 0;
      await page.click('[data-share-email] button');
      await page.waitForFunction(() => document.body.innerText.includes('Sent to owner@example.com'), null, { timeout: 10000 }).catch(() => undefined);
      const emailPost = requests.find((r) => /POST \/api\/v1\/documents\/jobs\/\d+\/share\/email/.test(r));
      s.check('Email posts to the job\'s share/email endpoint', !!emailPost, emailPost ?? 'no POST');
      s.check('no second job was queued for the same format', !requests.some((r) => r.startsWith('POST /api/v1/documents/jobs?') || r === 'POST /api/v1/documents/jobs'), 'reused');
      s.check('the toast confirms the recipient', (await page.evaluate(() => document.body.innerText)).includes('Sent to owner@example.com'), 'toast');
      s.check('no console errors', page.__errors.length === 0, page.__errors.join(' | ') || 'clean');
      await page.context().close();
    }

    // ---- Phone: the sheet fits ----
    {
      const requests = []; const jobs = new Map();
      const phone = await newPage(browser, { viewport: { width: 390, height: 844 }, mobile: true, api: shareApi({ requests, jobs }) });
      await openSheet(phone);
      const overflow = await phone.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);
      s.check('no horizontal scroll with the sheet open on a phone', !overflow, 'within viewport');
      const box = await phone.$eval('[data-share-modal]', (e) => e.getBoundingClientRect().width);
      s.check('the sheet is narrower than the phone', box <= 390, `${Math.round(box)}px`);
      await phone.context().close();
    }
  });

  return s;
}
