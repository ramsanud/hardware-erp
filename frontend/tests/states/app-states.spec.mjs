/**
 * CR-100 — application states and the landing page.
 *
 * Each state is provoked the way it happens in production: a stubbed 403,
 * a stubbed 500 on three of the dashboard's calls, the browser context taken
 * offline, a 401 on a click after a good sign-in. The assertions are the
 * things the page decides — which words, which data attribute, which URL —
 * not the server's message.
 */
import { BASE, suite, withBrowser, newPage, hasHorizontalScroll } from '../support/harness.mjs';
import { signedInApi, signedOutApi, OWNER } from '../support/fixtures.mjs';

const failure = (status, code) => ({
  status, contentType: 'application/json',
  body: JSON.stringify({ success: false, message: 'Stubbed failure', code, requestId: 'req-test-1' }),
});

export default async function run() {
  const s = suite('states');

  await withBrowser(async (browser) => {
    // -- Landing page, signed out, at desktop and phone width -----------------
    for (const vp of [{ name: 'desktop', width: 1440, height: 900, mobile: false }, { name: 'phone', width: 390, height: 844, mobile: true }]) {
      const page = await newPage(browser, { viewport: vp, mobile: vp.mobile, api: signedOutApi() });
      await page.goto(`${BASE}/`);
      await page.waitForSelector('[data-landing]');
      // The tiles fade in on a stagger; wait for the last one rather than a fixed sleep.
      await page.waitForFunction(() => {
        const last = document.querySelectorAll('[data-integration-visual] > div')[6];
        return last && getComputedStyle(last).opacity === '1';
      });

      s.check(`landing (${vp.name}): renders signed out at /`, page.url() === `${BASE}/`, page.url());
      s.check(`landing (${vp.name}): no page errors`, page.__errors.length === 0, page.__errors.join(' | '));
      s.check(`landing (${vp.name}): no horizontal scroll`, !(await hasHorizontalScroll(page)));

      const registerLinks = await page.locator('a[href="/register"]').count();
      const loginLinks = await page.locator('a[href="/login"]').count();
      s.check(`landing (${vp.name}): register and sign-in links present`, registerLinks >= 2 && loginLinks >= 1, `${registerLinks} register, ${loginLinks} sign in`);

      const tiles = await page.locator('[data-integration-visual] > div').count();
      // One centre tile plus six integrations.
      s.check(`landing (${vp.name}): six integration tiles drawn`, tiles === 7, `${tiles} tiles`);
      const labels = await page.locator('[data-integration-visual]').innerText();
      s.check(`landing (${vp.name}): tiles name shipped channels`, /WhatsApp/.test(labels) && /GSTR-1/.test(labels) && /Tally/.test(labels), labels.replace(/\s+/g, ' ').trim());

      const h1 = await page.locator('h1').first().innerText();
      s.check(`landing (${vp.name}): hero headline`, /one place/.test(h1), h1.replace(/\s+/g, ' '));
      await page.context().close();
    }

    // -- Landing page never shows to a signed-in user -------------------------
    {
      const page = await newPage(browser, { api: signedInApi() });
      await page.goto(`${BASE}/`);
      await page.waitForURL('**/dashboard');
      s.check('landing: signed-in visitor to / lands on the dashboard', page.url().endsWith('/dashboard'), page.url());
      await page.context().close();
    }

    // -- Partial data on the dashboard, then Retry ----------------------------
    {
      const base = signedInApi();
      let analyticsDown = true;
      const page = await newPage(browser, {
        viewport: { width: 1440, height: 900 },
        api: (url) => (analyticsDown && url.includes('/v1/analytics/') ? failure(500, 'INTERNAL_ERROR') : base(url)),
      });
      await page.goto(`${BASE}/dashboard`);
      const notice = page.locator('[data-partial-data]');
      await notice.waitFor();
      const text = await notice.innerText();
      s.check('partial data: notice names the failed sections',
        /3 sections/.test(text) && /Sales growth/.test(text) && /Pending payments/.test(text) && /Low stock trend/.test(text),
        text.replace(/\s+/g, ' '));
      s.check('partial data: the rest of the page still rendered', (await page.locator('h1').innerText()).includes('Welcome back'));

      analyticsDown = false;
      await page.getByRole('button', { name: 'Retry' }).click();
      await notice.waitFor({ state: 'detached' });
      s.check('partial data: Retry clears the notice once the calls succeed', (await notice.count()) === 0);

      // -- Offline banner, on the same page -----------------------------------
      await page.context().setOffline(true);
      const offline = page.locator('[data-offline-banner="offline"]');
      await offline.waitFor();
      s.check('offline: banner appears when the browser goes offline', /offline/i.test(await offline.innerText()));
      await page.context().setOffline(false);
      await page.locator('[data-offline-banner="recovered"]').waitFor();
      s.check('offline: banner says "back online" on recovery, then folds away',
        await page.locator('[data-offline-banner]').waitFor({ state: 'detached', timeout: 6000 }).then(() => true).catch(() => false));
      s.check('dashboard: no page errors through partial, retry and offline', page.__errors.length === 0, page.__errors.join(' | '));
      await page.context().close();
    }

    // -- ErrorState keyed to the error code -----------------------------------
    {
      const base = signedInApi();
      const page = await newPage(browser, {
        viewport: { width: 1440, height: 900 },
        api: (url) => (url.includes('/v1/products') ? failure(403, 'ACCESS_DENIED') : base(url)),
      });
      await page.goto(`${BASE}/products`);
      const state = page.locator('[data-error-code="ACCESS_DENIED"]');
      await state.waitFor();
      const text = await state.innerText();
      s.check('error state: a 403 is drawn as a permission problem, not a crash', /do not have access/.test(text) && /shop owner/.test(text), text.replace(/\s+/g, ' '));
      s.check('error state: the request id is shown for support', /req-test-1/.test(text));
      await page.context().close();
    }

    // -- Session expiry mid-session -------------------------------------------
    {
      const base = signedInApi();
      let expired = false;
      const page = await newPage(browser, {
        viewport: { width: 1440, height: 900 },
        api: (url) => (expired ? failure(401, 'UNAUTHENTICATED') : base(url)),
      });
      await page.goto(`${BASE}/dashboard`);
      await page.waitForSelector('h1');
      expired = true;
      await page.click('a[href="/products"]');
      const notice = page.locator('[data-session-expired]');
      await notice.waitFor();
      s.check('session expiry: bounced to /login', page.url().endsWith('/login'), page.url());
      s.check('session expiry: the sign-in card says why', /session has expired/i.test(await notice.innerText()));
      await page.context().close();
    }

    // -- A first visit is not an expiry ---------------------------------------
    {
      const page = await newPage(browser, { api: signedOutApi() });
      await page.goto(`${BASE}/login`);
      await page.waitForSelector('form');
      s.check('session expiry: a plain signed-out visit shows no expiry notice', (await page.locator('[data-session-expired]').count()) === 0);
      await page.context().close();
    }

    // -- Route-level permission denial has a way out --------------------------
    {
      const user = { ...OWNER, permissions: OWNER.permissions.filter((p) => !p.startsWith('PRODUCT')) };
      const page = await newPage(browser, { api: signedInApi({ user }) });
      await page.goto(`${BASE}/products`);
      await page.waitForSelector('text=You do not have access to this page');
      s.check('permission denied: offers a link back to the dashboard', (await page.locator('a[href="/dashboard"]', { hasText: 'Back to dashboard' }).count()) === 1);
      await page.context().close();
    }
  });

  return s;
}
