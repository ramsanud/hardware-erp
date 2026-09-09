/**
 * Regression cover for the product grid and the dialog shell.
 *
 * Pins CR-063's status colours, BUG-FE-026 (the phone card had no price) and
 * BUG-FE-028 (the scrolling form showed above the pinned dialog header).
 */
import { BASE, suite, withBrowser, newPage, hasHorizontalScroll } from '../support/harness.mjs';
import { signedInApi } from '../support/fixtures.mjs';

/** Computed colour of the badge whose text is exactly `word`, in the row containing `rowText`. */
function badgeColour(page, rowText, word) {
  return page.locator(`table tbody tr:has-text("${rowText}")`)
    .locator('span', { hasText: new RegExp(`^${word}$`) })
    .first()
    .evaluate((el) => getComputedStyle(el).color);
}

const rgb = (value) => (/rgba?\((\d+), ?(\d+), ?(\d+)/.exec(value) ?? []).slice(1).map(Number);

export default async function run() {
  const s = suite('products');

  await withBrowser(async (browser) => {
    // ---------------------------- desktop ----------------------------
    const desktop = await newPage(browser, {
      viewport: { width: 1440, height: 900 }, api: signedInApi(),
    });
    await desktop.goto(`${BASE}/products`, { waitUntil: 'networkidle' });
    await desktop.waitForSelector('table tbody tr', { timeout: 15000 });

    s.check('the product list renders its rows',
      (await desktop.locator('table tbody tr').count()) === 3);

    const active = await badgeColour(desktop, 'CPVC Elbow', 'Active');
    const inactive = await badgeColour(desktop, 'Brass Ball Valve', 'Inactive');
    const [ar, ag, ab] = rgb(active);
    const [ir, ig, ib] = rgb(inactive);
    s.check('Active is green', ag > ar && ag > ab, active);
    s.check('Inactive is red, not the old neutral grey', ir > ig + 30 && ir > ib + 30, inactive);
    s.check('the two states are not the same colour', active !== inactive);
    s.check('status is never colour alone - the word is present',
      (await desktop.locator('table tbody tr:has-text("Brass Ball Valve")').innerText()).includes('Inactive'));
    s.check('the desktop grid shows the price',
      (await desktop.locator('table tbody tr:has-text("CPVC Elbow")').innerText()).includes('1,250.00'));
    s.check('no horizontal page scroll on desktop', !(await hasHorizontalScroll(desktop)));

    await desktop.click('text=Add product');
    await desktop.waitForSelector('[role="dialog"]');
    const dialog = await desktop.locator('[role="dialog"]').boundingBox();
    s.check('the desktop dialog stays a centred panel, not full-bleed',
      dialog.width < 1440 * 0.75, `${dialog.width.toFixed(0)}px wide`);
    s.check('the desktop dialog fits the viewport height',
      dialog.height <= 900, `${dialog.height.toFixed(0)}px tall`);
    await desktop.keyboard.press('Escape');

    // ---------------------------- mobile ----------------------------
    const mobile = await newPage(browser, {
      viewport: { width: 390, height: 844 }, mobile: true, api: signedInApi(),
    });
    await mobile.goto(`${BASE}/products`, { waitUntil: 'networkidle' });
    await mobile.waitForSelector('table tbody tr', { timeout: 15000 });

    const card = await mobile.locator('table tbody tr:has-text("CPVC Elbow")').innerText();
    s.check('the mobile card shows the product name', card.includes('CPVC Elbow 25mm'));
    // BUG-FE-026: this is the assertion that was false - a price list with no prices.
    s.check('the mobile card shows the PRICE', card.includes('1,250.00'),
      JSON.stringify(card.replace(/\n/g, ' | ')));
    s.check('the mobile card shows the status', card.includes('Active'));
    s.check('the table header row is hidden - cards, not a shrunken table',
      !(await mobile.locator('table thead').first().isVisible()));
    s.check('no horizontal page scroll at 390px', !(await hasHorizontalScroll(mobile)));

    const long = await mobile.locator('table tbody tr:has-text("PVC Pipe 4 inch")').boundingBox();
    s.check('a long product name does not overflow the card',
      long.width <= 390, `${long.width.toFixed(0)}px`);

    const actions = mobile.locator('table tbody tr:has-text("CPVC Elbow") button[aria-label^="Actions for"]');
    const ab2 = await actions.boundingBox();
    s.check('the row actions button is reachable inside the viewport',
      (await actions.count()) === 1 && ab2.x >= 0 && ab2.x + ab2.width <= 390);

    // ------------------- dialog scrolling architecture -------------------
    await mobile.click('text=Add product');
    await mobile.waitForSelector('[role="dialog"]');
    await mobile.waitForTimeout(500);

    // Measured in-page: boundingBox() reports document coords under mobile
    // emulation and would misreport a fixed sheet entirely.
    const geom = () => mobile.evaluate(() => {
      const d = document.querySelector('[role="dialog"]');
      const scroller = d.querySelector('.dialog-scroll');
      const header = d.querySelector('.dialog-sticky-header');
      const footer = d.querySelector('.dialog-sticky-footer');
      const r = (el) => { const b = el.getBoundingClientRect(); return { top: +b.top.toFixed(1), bottom: +b.bottom.toFixed(1), left: +b.left.toFixed(1), right: +b.right.toFixed(1) }; };
      const box = d.getBoundingClientRect();
      return {
        vh: window.innerHeight, vw: window.innerWidth,
        dialog: r(d), scroller: r(scroller), header: r(header), footer: r(footer),
        scrollTop: scroller.scrollTop, scrollHeight: scroller.scrollHeight, clientHeight: scroller.clientHeight,
        topPixelOwner: String(document.elementFromPoint(box.left + 60, box.top + 3)?.className ?? ''),
      };
    });

    let g = await geom();
    s.check('the mobile dialog is a full-width bottom sheet',
      g.dialog.left <= 1 && g.dialog.right >= g.vw - 1);
    s.check('the mobile dialog sits inside the viewport',
      g.dialog.top >= 0 && g.dialog.bottom <= g.vh + 1,
      `top ${g.dialog.top}, bottom ${g.dialog.bottom}, vh ${g.vh}`);
    s.check('Save/Cancel are on screen without scrolling the dialog',
      g.footer.bottom <= g.vh + 1, `footer bottom ${g.footer.bottom}`);
    // BUG-FE-028: this gap was 16px, and the form scrolled through it.
    s.check('the sticky header is flush with the panel top - no bleed strip',
      Math.abs(g.header.top - g.scroller.top) < 1,
      `gap ${(g.header.top - g.scroller.top).toFixed(1)}px`);
    s.check('the form is taller than the panel, so this is a real scroll case',
      g.scrollHeight > g.clientHeight + 100, `${g.scrollHeight} > ${g.clientHeight}`);

    const headerBefore = g.header.top;
    const footerBefore = g.footer.top;
    await mobile.locator('[role="dialog"] .dialog-scroll').evaluate((el) => { el.scrollTop = 500; });
    await mobile.waitForTimeout(300);
    g = await geom();

    s.check('the dialog body actually scrolled', g.scrollTop >= 400, `scrollTop ${g.scrollTop}`);
    s.check('the header stays pinned while the body scrolls',
      Math.abs(g.header.top - headerBefore) < 1, `${headerBefore} → ${g.header.top}`);
    s.check('the footer stays pinned while the body scrolls',
      Math.abs(g.footer.top - footerBefore) < 1, `${footerBefore} → ${g.footer.top}`);
    s.check('the header still occludes the panel top edge after scrolling',
      g.topPixelOwner.includes('dialog-sticky-header'), g.topPixelOwner.slice(0, 40));
    s.check('the page behind the dialog is scroll-locked',
      await mobile.evaluate(() => getComputedStyle(document.body).overflow === 'hidden'
        || document.body.hasAttribute('data-scroll-locked')
        || document.body.style.pointerEvents === 'none'));

    await mobile.keyboard.press('Escape');
    await mobile.waitForTimeout(400);
    s.check('Escape closes the dialog', (await mobile.locator('[role="dialog"]').count()) === 0);

    s.check('no uncaught errors on desktop or mobile',
      desktop.__errors.length === 0 && mobile.__errors.length === 0,
      [...desktop.__errors, ...mobile.__errors].slice(0, 2).join(' | '));
  });

  return s;
}
