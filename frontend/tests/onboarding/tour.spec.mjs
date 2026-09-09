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
  });

  return s;
}
