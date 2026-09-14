/**
 * CR-080 - the manual WhatsApp button.
 *
 * The contract under test: one click fetches a wa.me link from the server and
 * opens it in a new tab - and does nothing else. No POST, no message sent by
 * the application, nothing stored. `window.open` is replaced by a recorder
 * before the app loads, so what the button tries to open is observed exactly.
 */
import { BASE, envelope, suite, withBrowser, newPage } from '../support/harness.mjs';
import { signedInApi, UNPAID_INVOICE } from '../support/fixtures.mjs';

const MESSAGE = 'Hello Bug Fix Test 👋\n\nInvoice No: INV-000053\nTotal: ₹1,180.00';
const LINK = {
  url: `https://wa.me/919123456700?text=${encodeURIComponent(MESSAGE)}`,
  toMobileNo: '9123456700',
  message: MESSAGE,
};

/** signedInApi plus the CR-080 link endpoints, recording every request method so "no POST" is provable. */
function apiWithLinks(requests, { invoiceOverride, linkStatus = 200 } = {}) {
  const base = signedInApi();
  return (url, route) => {
    requests.push(`${route.request().method()} ${new URL(url).pathname}`);
    if (/\/v1\/whatsapp\/links\/invoices\/\d+\/reminder$/.test(url)) {
      return envelope({ ...LINK, message: 'Reminder: ₹1,180.00 is due on INV-000053' });
    }
    if (/\/v1\/whatsapp\/links\/invoices\/\d+$/.test(url)) {
      if (linkStatus !== 200) {
        return {
          status: linkStatus, contentType: 'application/json',
          body: JSON.stringify({ success: false, message: 'Please add a valid customer WhatsApp number.', code: 'BUSINESS_RULE', timestamp: new Date().toISOString() }),
        };
      }
      return envelope(LINK);
    }
    if (invoiceOverride && /\/v1\/invoices\/\d+$/.test(url)) return envelope(invoiceOverride);
    return base(url);
  };
}

/** Records every window.open call instead of opening anything. Installed before any app script runs. */
async function recordWindowOpen(page) {
  await page.addInitScript(() => {
    window.__opened = [];
    window.open = (url, target, features) => {
      window.__opened.push({ url: String(url), target, features });
      return null; // exactly what a real noopener open returns
    };
  });
}

export default async function run() {
  const s = suite('whatsapp');

  await withBrowser(async (browser) => {
    for (const [label, opts] of [
      ['desktop', { viewport: { width: 1280, height: 800 } }],
      ['mobile', { viewport: { width: 390, height: 844 }, mobile: true }],
    ]) {
      // --- happy path: click opens the server's link, and only a GET happened ---
      {
        const requests = [];
        const page = await newPage(browser, { ...opts, api: apiWithLinks(requests) });
        await recordWindowOpen(page);
        await page.goto(`${BASE}/invoices/53`, { waitUntil: 'networkidle', timeout: 20000 });

        const button = page.getByRole('button', { name: 'Open WhatsApp for Bug Fix Test' }).first();
        s.check(`${label}: the WhatsApp button renders with an accessible name`, await button.count() === 1,
          `count=${await button.count()}`);
        s.check(`${label}: it is enabled - the fixture customer has a mobile`, !(await button.isDisabled()));

        const box = await button.boundingBox();
        s.check(`${label}: it is a real tap target (>= 36px tall)`, box !== null && box.height >= 36, `h=${box?.height}`);

        await button.click();
        await page.waitForFunction(() => (window.__opened ?? []).length > 0, null, { timeout: 5000 }).catch(() => undefined);
        const opened = await page.evaluate(() => window.__opened);

        s.check(`${label}: exactly one window was opened`, opened.length === 1, `opened=${opened.length}`);
        s.check(`${label}: it is the server's wa.me link, unmodified`, opened[0]?.url === LINK.url, opened[0]?.url);
        s.check(`${label}: opened in a new tab with noopener,noreferrer`,
          opened[0]?.target === '_blank' && /noopener/.test(opened[0]?.features ?? '') && /noreferrer/.test(opened[0]?.features ?? ''),
          `${opened[0]?.target} ${opened[0]?.features}`);

        const decoded = decodeURIComponent(new URL(opened[0]?.url ?? LINK.url).search.slice('?text='.length));
        s.check(`${label}: the customer's name is in the message`, decoded.includes('Bug Fix Test'));
        s.check(`${label}: the invoice number is in the message`, decoded.includes('INV-000053'));
        s.check(`${label}: the amount is in the message`, decoded.includes('₹1,180.00'));
        s.check(`${label}: the number dialled is the customer's, in E.164 without '+'`,
          new URL(opened[0]?.url ?? '').pathname === '/919123456700');

        const linkCalls = requests.filter((r) => r.includes('/v1/whatsapp/links/'));
        s.check(`${label}: the link was fetched with GET and nothing was POSTed - the app sent no message`,
          linkCalls.length >= 1 && linkCalls.every((r) => r.startsWith('GET ')) && !requests.some((r) => r.startsWith('POST ') && r.includes('whatsapp')),
          linkCalls.join(', '));

        // Second click must fetch afresh - a balance can change between clicks.
        await button.click();
        await page.waitForFunction(() => (window.__opened ?? []).length > 1, null, { timeout: 5000 }).catch(() => undefined);
        s.check(`${label}: a second click opens again`, (await page.evaluate(() => window.__opened.length)) === 2);

        s.check(`${label}: no page errors`, page.__errors.length === 0, page.__errors.join(' | '));
        await page.context().close();
      }

      // --- the reminder button is the manual link too, on an outstanding invoice ---
      {
        const requests = [];
        const page = await newPage(browser, { ...opts, api: apiWithLinks(requests) });
        await recordWindowOpen(page);
        await page.goto(`${BASE}/invoices/53`, { waitUntil: 'networkidle', timeout: 20000 });
        const reminder = page.getByRole('button', { name: 'Open WhatsApp reminder for Bug Fix Test' }).first();
        s.check(`${label}: an UNPAID invoice shows the WhatsApp reminder`, await reminder.count() === 1);
        await reminder.click();
        await page.waitForFunction(() => (window.__opened ?? []).length > 0, null, { timeout: 5000 }).catch(() => undefined);
        s.check(`${label}: the reminder fetched the reminder link`, requests.some((r) => /GET .*\/reminder$/.test(r)), requests.filter((r) => r.includes('whatsapp')).join(', '));
        await page.context().close();
      }

      // --- a customer with no number: the button is disabled and says why ---
      {
        const page = await newPage(browser, { ...opts, api: apiWithLinks([], { invoiceOverride: { ...UNPAID_INVOICE, customerMobile: '' } }) });
        await recordWindowOpen(page);
        await page.goto(`${BASE}/invoices/53`, { waitUntil: 'networkidle', timeout: 20000 });
        const button = page.getByRole('button', { name: 'Open WhatsApp for Bug Fix Test' }).first();
        s.check(`${label}: with no mobile the button is disabled`, await button.isDisabled());
        s.check(`${label}: and its tooltip says why`, (await button.getAttribute('title')) === 'This customer does not have a phone number.',
          await button.getAttribute('title'));
        await button.click({ force: true }).catch(() => undefined);
        s.check(`${label}: a disabled button opens nothing`, (await page.evaluate(() => window.__opened.length)) === 0);
        await page.context().close();
      }

      // --- an invalid number: the server refuses, the user sees the reason, nothing opens ---
      {
        const page = await newPage(browser, { ...opts, api: apiWithLinks([], { linkStatus: 422 }) });
        await recordWindowOpen(page);
        await page.goto(`${BASE}/invoices/53`, { waitUntil: 'networkidle', timeout: 20000 });
        await page.getByRole('button', { name: 'Open WhatsApp for Bug Fix Test' }).first().click();
        const toast = page.getByText('Please add a valid customer WhatsApp number.');
        await toast.waitFor({ timeout: 5000 }).catch(() => undefined);
        s.check(`${label}: the server's own reason is shown as a toast`, await toast.count() >= 1);
        s.check(`${label}: nothing was opened`, (await page.evaluate(() => window.__opened.length)) === 0);
        await page.context().close();
      }
    }
  });

  return s;
}
