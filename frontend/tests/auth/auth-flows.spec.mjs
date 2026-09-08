/**
 * Regression cover for the password/session flows.
 *
 * Pins BUG-FE-024, BUG-FE-025 (both halves) and CR-063's clearable inputs.
 * Every assertion here failed before those fixes; several of them are the only
 * automated evidence those bugs stay fixed.
 */
import { BASE, suite, withBrowser, newPage, hasHorizontalScroll, rectOf } from '../support/harness.mjs';
import { signedOutApi } from '../support/fixtures.mjs';

export default async function run() {
  const s = suite('auth');

  await withBrowser(async (browser) => {
    const page = await newPage(browser, {
      viewport: { width: 390, height: 844 }, mobile: true, api: signedOutApi(),
    });

    // ---------------- clearable inputs (CR-063) ----------------
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
    s.check('login page renders', await page.locator('#identifier').isVisible());

    s.check('no clear button on an empty field',
      (await page.locator('button[aria-label="Clear"]').count()) === 0);

    await page.fill('#identifier', '9876543210');
    s.check('clear button appears once the field has text',
      (await page.locator('button[aria-label="Clear"]').count()) >= 1);

    await page.locator('button[aria-label="Clear"]').first().click();
    s.check('clicking x empties the field', (await page.inputValue('#identifier')) === '');
    s.check('focus stays in the field, so the user can retype immediately',
      (await page.evaluate(() => document.activeElement?.id)) === 'identifier');

    await page.fill('#password', 'Secret123');
    const clear = page.locator('div:has(> #password) button[aria-label="Clear"]');
    const eye = page.locator('button[aria-label="Show password"], button[aria-label="Hide password"]');
    s.check('password field has both a clear button and a show/hide toggle',
      (await clear.count()) === 1 && (await eye.count()) === 1);

    const cb = await clear.boundingBox();
    const eb = await eye.boundingBox();
    s.check('clear and eye do not overlap',
      cb.x + cb.width <= eb.x + 1 || eb.x + eb.width <= cb.x + 1,
      `clear ${cb.x.toFixed(0)}–${(cb.x + cb.width).toFixed(0)}, eye ${eb.x.toFixed(0)}–${(eb.x + eb.width).toFixed(0)}`);
    s.check('both touch targets are at least 40px',
      cb.width >= 40 && cb.height >= 40 && eb.width >= 40 && eb.height >= 40,
      `clear ${cb.width}×${cb.height}, eye ${eb.width}×${eb.height}`);

    await eye.click();
    const revealed = (await page.getAttribute('#password', 'type')) === 'text';
    await eye.click();
    const remasked = (await page.getAttribute('#password', 'type')) === 'password';
    s.check('show/hide still works and keeps the value', revealed && remasked
      && (await page.inputValue('#password')) === 'Secret123');

    s.check('no horizontal page scroll at 390px', !(await hasHorizontalScroll(page)));

    // ---------------- BUG-FE-025: nothing stale survives ----------------
    await page.fill('#identifier', 'someone@example.com');
    await page.fill('#password', 'Secret123');
    await page.click('text=Forgot password?');
    await page.waitForURL('**/forgot-password');
    await page.click('text=Back to sign in');
    await page.waitForURL('**/login');
    s.check('returning to login shows an empty form',
      (await page.inputValue('#identifier')) === '' && (await page.inputValue('#password')) === '');

    await page.goBack();
    await page.waitForTimeout(300);
    s.check('browser Back does not re-enter the forgot-password flow',
      !page.url().includes('/forgot-password'), page.url());

    // ---------------- BUG-FE-025b: the reset token ----------------
    await page.goto(`${BASE}/reset-password?token=SECRET-RESET-TOKEN-123`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(400);
    s.check('the reset token is stripped from the address bar',
      !page.url().includes('SECRET-RESET-TOKEN'), page.url());
    s.check('the reset form still renders after the token is stripped',
      (await page.locator('#newPassword').count()) === 1);

    await page.goto(`${BASE}/login`);
    await page.waitForTimeout(200);
    await page.goBack();
    await page.waitForTimeout(400);
    s.check('Back from login never restores a tokened reset URL',
      !page.url().includes('SECRET-RESET-TOKEN'), page.url());

    await page.goto(`${BASE}/reset-password`, { waitUntil: 'networkidle' });
    s.check('a tokenless reset link explains itself and offers a way forward',
      (await page.locator('text=This link is not valid').count()) === 1
      && (await page.locator('text=Request a new link').count()) === 1);

    s.check('no uncaught errors across the auth flows',
      page.__errors.length === 0, page.__errors.slice(0, 2).join(' | '));
  });

  return s;
}
