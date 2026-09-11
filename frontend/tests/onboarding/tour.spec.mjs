/**
 * CR-075 — the first-visit tour.
 *
 * The three things that actually matter to a shop, in order:
 *   1. a new user is offered it without asking,
 *   2. they can get out of it in one click, and
 *   3. it stays out once dismissed.
 *
 * (3) is the one worth a test. A welcome dialog that reappears on every page
 * load is worse than no welcome dialog at all, and it is the exact failure
 * that a hand-check ("it showed up, looks nice") never catches.
 *
 * Role-awareness is asserted through permissions rather than role codes,
 * because that is how the tour filters - see tourSteps.ts.
 */
import { BASE, suite, withBrowser, newPage } from '../support/harness.mjs';
import { signedInApi, OWNER } from '../support/fixtures.mjs';

const DIALOG = '[role="dialog"]';

/**
 * Radix keeps the dialog mounted through its exit animation, so "has it
 * closed?" is a wait, not an instant read - a fixed sleep here is exactly the
 * kind of flake that gets muted later.
 */
async function tourGone(page) {
  try {
    await page.waitForSelector(DIALOG, { state: 'detached', timeout: 5000 });
    return true;
  } catch {
    return false;
  }
}

/** The tour dialog, identified by its step counter rather than by copy. */
async function tourState(page) {
  return page.evaluate((sel) => {
    const dialog = document.querySelector(sel);
    if (!dialog) return null;
    const text = dialog.innerText;
    const counter = text.match(/Step (\d+) of (\d+)/);
    return {
      text,
      step: counter ? Number(counter[1]) : null,
      total: counter ? Number(counter[2]) : null,
      heading: dialog.querySelector('h2, [id$="-title"]')?.innerText?.trim() ?? null,
    };
  }, DIALOG);
}

export default async function run() {
  const s = suite('onboarding');

  await withBrowser(async (browser) => {
    // ---- A first visit is offered the tour --------------------------------
    const page = await newPage(browser, {
      viewport: { width: 1280, height: 800 },
      api: signedInApi(),
      firstVisit: true,
    });
    await page.goto(BASE + '/dashboard', { waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForTimeout(400);

    let state = await tourState(page);
    s.check('a first-time user is offered the tour unprompted',
      state !== null && state.step === 1, state ? `step ${state.step} of ${state.total}` : 'no dialog');

    s.check('it greets the user by name',
      Boolean(state && state.text.includes(OWNER.fullName.split(' ')[0])),
      state ? 'greeting present' : 'no dialog');

    // The welcome step is the "easy to identify" step: role, shop, and the
    // areas this person can reach, as chips.
    s.check('the welcome names the role',
      Boolean(state && state.text.includes(OWNER.roleName)), state ? 'role present' : 'no dialog');
    const chips = await page.evaluate(() =>
      [...document.querySelectorAll('[role="dialog"] ul[aria-label="Areas you can work with"] li')]
        .map((li) => li.innerText.trim()));
    s.check('the welcome lists the areas an owner can work with',
      chips.length >= 6 && chips.includes('Sales') && chips.includes('People and permissions'),
      chips.join(', ') || 'no chips');

    // The owner fixture holds nearly every permission, so it should see the
    // whole tour - the upper bound that proves filtering is not over-eager.
    s.check('an owner sees the full walkthrough',
      Boolean(state && state.total >= 8), state ? `${state.total} steps` : 'no dialog');

    // ---- Next / Back move through it --------------------------------------
    await page.getByRole('button', { name: 'Next' }).click();
    await page.waitForTimeout(150);
    state = await tourState(page);
    s.check('Next advances a step', state?.step === 2, `step ${state?.step}`);

    await page.getByRole('button', { name: 'Back' }).click();
    await page.waitForTimeout(150);
    state = await tourState(page);
    s.check('Back returns a step', state?.step === 1, `step ${state?.step}`);

    // Nothing to go back to, so the control should not be there to press.
    const backOnFirst = await page.getByRole('button', { name: 'Back' }).count();
    s.check('Back is not offered on the very first step',
      backOnFirst === 0, `${backOnFirst} Back buttons at step 1`);

    // ---- Skip is one click, and it sticks ---------------------------------
    await page.getByRole('button', { name: 'Skip tour' }).click();
    s.check('Skip closes the tour', await tourGone(page), 'dialog detached');

    await page.goto(BASE + '/products', { waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForTimeout(400);
    s.check('a skipped tour does not come back on the next page',
      (await tourState(page)) === null, 'still gone');

    await page.reload({ waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForTimeout(400);
    s.check('a skipped tour survives a full reload',
      (await tourState(page)) === null, 'still gone');

    // ---- It can be reopened on demand -------------------------------------
    await page.getByRole('button', { name: 'How this application works' }).click();
    await page.waitForTimeout(250);
    state = await tourState(page);
    s.check('the help button reopens the tour at step 1',
      state !== null && state.step === 1, state ? `step ${state.step}` : 'no dialog');

    await page.keyboard.press('Escape');
    s.check('Escape dismisses it too', await tourGone(page), 'dialog detached');

    await page.context().close();

    // ---- A returning user is never interrupted ----------------------------
    const returning = await newPage(browser, {
      viewport: { width: 1280, height: 800 },
      api: signedInApi(),
    });
    await returning.goto(BASE + '/dashboard', { waitUntil: 'networkidle', timeout: 20000 });
    await returning.waitForTimeout(400);
    s.check('a returning user is not shown the tour',
      (await tourState(returning)) === null, 'no dialog');
    await returning.context().close();

    // ---- Role awareness: fewer permissions, fewer steps -------------------
    // A storekeeper who cannot see money, people or the audit trail.
    const storekeeper = {
      ...OWNER,
      id: 1,
      fullName: 'Suresh Babu',
      roleCode: 'STOREKEEPER',
      roleName: 'Storekeeper',
      permissions: ['PRODUCT_VIEW', 'INVENTORY_VIEW'],
    };
    const limited = await newPage(browser, {
      viewport: { width: 1280, height: 800 },
      api: signedInApi({ user: storekeeper }),
      firstVisit: true,
    });
    await limited.goto(BASE + '/dashboard', { waitUntil: 'networkidle', timeout: 20000 });
    await limited.waitForTimeout(400);

    const limitedState = await tourState(limited);
    s.check('a storekeeper is offered a shorter, relevant tour',
      limitedState !== null && limitedState.total < 8,
      limitedState ? `${limitedState.total} steps` : 'no dialog');

    // Walk the whole thing and collect the headings, to prove the money and
    // people steps are absent rather than merely fewer.
    const headings = [];
    if (limitedState) {
      for (let i = 0; i < limitedState.total; i += 1) {
        const current = await tourState(limited);
        if (current?.heading) headings.push(current.heading);
        if (i < limitedState.total - 1) {
          await limited.getByRole('button', { name: 'Next' }).click();
          await limited.waitForTimeout(120);
        }
      }
    }
    const joined = headings.join(' | ');
    s.check('the storekeeper tour includes stock', /stock|product/i.test(joined), joined || 'none');
    s.check('the storekeeper tour omits people and permissions',
      !/permission/i.test(joined), joined || 'none');
    s.check('the storekeeper tour omits the audit trail',
      !/disappears quietly/i.test(joined), joined || 'none');

    await limited.context().close();

    // ---- The welcome chips are the role, not the catalogue ----------------
    const limitedAgain = await newPage(browser, {
      viewport: { width: 1280, height: 800 },
      api: signedInApi({ user: storekeeper }),
      firstVisit: true,
    });
    await limitedAgain.goto(BASE + '/dashboard', { waitUntil: 'networkidle', timeout: 20000 });
    await limitedAgain.waitForTimeout(400);
    const skChips = await limitedAgain.evaluate(() =>
      [...document.querySelectorAll('[role="dialog"] ul[aria-label="Areas you can work with"] li')]
        .map((li) => li.innerText.trim()));
    s.check("a storekeeper's welcome lists only stock",
      skChips.length === 1 && skChips[0] === 'Products and stock', skChips.join(', ') || 'no chips');
    await limitedAgain.context().close();

    // ---- Per-page tips: first visit to each screen ------------------------
    const tipsPage = await newPage(browser, {
      viewport: { width: 1280, height: 800 },
      api: signedInApi(),
      firstVisit: true,
    });
    await tipsPage.goto(BASE + '/invoices', { waitUntil: 'networkidle', timeout: 20000 });
    await tipsPage.waitForTimeout(300);
    // Get the tour out of the way first - it is a separate thing.
    await tipsPage.getByRole('button', { name: 'Skip tour' }).click();
    await tourGone(tipsPage);

    const tipOn = (id) => tipsPage.evaluate((sel) => Boolean(document.querySelector(sel)), `[data-page-tip="${id}"]`);
    s.check('the first visit to Invoices shows its tip', await tipOn('invoices'), 'tip present');

    await tipsPage.getByRole('button', { name: 'Got it' }).click();
    await tipsPage.waitForTimeout(150);
    s.check('Got it hides the tip', !(await tipOn('invoices')), 'tip gone');

    await tipsPage.goto(BASE + '/products', { waitUntil: 'networkidle', timeout: 20000 });
    await tipsPage.waitForTimeout(300);
    s.check('a different screen still gets its own tip', await tipOn('products'), 'tip present');

    await tipsPage.goto(BASE + '/invoices', { waitUntil: 'networkidle', timeout: 20000 });
    await tipsPage.waitForTimeout(300);
    s.check('a dismissed tip stays dismissed', !(await tipOn('invoices')), 'still gone');

    await tipsPage.goto(BASE + '/products', { waitUntil: 'networkidle', timeout: 20000 });
    await tipsPage.waitForTimeout(300);
    await tipsPage.getByRole('button', { name: 'Turn off tips' }).click();
    await tipsPage.waitForTimeout(150);
    await tipsPage.goto(BASE + '/customers', { waitUntil: 'networkidle', timeout: 20000 });
    await tipsPage.waitForTimeout(300);
    s.check('Turn off tips silences every screen', !(await tipOn('customers')), 'no tip');

    // Asking for the tour again is the signal that tips are wanted back.
    await tipsPage.getByRole('button', { name: 'How this application works' }).click();
    await tipsPage.waitForTimeout(200);
    await tipsPage.getByRole('button', { name: 'Skip tour' }).click();
    await tourGone(tipsPage);
    await tipsPage.goto(BASE + '/suppliers', { waitUntil: 'networkidle', timeout: 20000 });
    await tipsPage.waitForTimeout(300);
    s.check('replaying the tour brings tips back', await tipOn('suppliers'), 'tip present');

    // Detail routes get the tip of their area, not nothing.
    await tipsPage.goto(BASE + '/labour/attendance', { waitUntil: 'networkidle', timeout: 20000 });
    await tipsPage.waitForTimeout(300);
    s.check("a nested route gets its own tip, not its parent's",
      await tipOn('labour-attendance'), 'attendance tip');
    await tipsPage.context().close();

    // ---- On a phone the tour is a bottom sheet, and still escapable --------
    const phone = await newPage(browser, {
      viewport: { width: 390, height: 844 },
      mobile: true,
      api: signedInApi(),
      firstVisit: true,
    });
    await phone.goto(BASE + '/dashboard', { waitUntil: 'networkidle', timeout: 20000 });
    await phone.waitForTimeout(400);
    const phoneState = await tourState(phone);
    s.check('a phone user is offered the tour', phoneState !== null && phoneState.step === 1,
      phoneState ? `step ${phoneState.step}` : 'no dialog');
    const sheet = await phone.evaluate(() => {
      const d = document.querySelector('[role="dialog"]');
      if (!d) return null;
      const r = d.getBoundingClientRect();
      return { width: Math.round(r.width), right: Math.round(r.right), bottom: Math.round(r.bottom), vw: window.innerWidth, vh: window.innerHeight };
    });
    s.check('the tour fits the phone width',
      Boolean(sheet && sheet.right <= sheet.vw + 1 && sheet.width >= sheet.vw * 0.9),
      sheet ? `${sheet.width}px wide in ${sheet.vw}px` : 'no dialog');
    const skipVisible = await phone.getByRole('button', { name: 'Skip tour' }).isVisible();
    s.check('Skip is visible on a phone without scrolling', skipVisible, 'visible');
    await phone.getByRole('button', { name: 'Skip tour' }).click();
    s.check('Skip works on a phone', await tourGone(phone), 'dialog detached');
    await phone.context().close();
  });

  return s;
}
