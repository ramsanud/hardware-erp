/**
 * CR-076 — "Pick on map" fills the address fields.
 *
 * The geocoder and the tile server are stubbed at the network edge, so the
 * spec is deterministic and runs offline: what is under test is that a click
 * on the map becomes a reverse-geocode call, that the answer is previewed,
 * and that "Use this address" writes all five fields - including the GST
 * state code, which is derived from the ISO code rather than typed.
 */
import { BASE, suite, withBrowser, newPage } from '../support/harness.mjs';
import { signedInApi } from '../support/fixtures.mjs';

// The shape Nominatim really returns for central Chennai (checked 2026-09-12):
// the civic body as the city and ward/zone labels as the locality.
const REVERSE = {
  lat: '13.0355', lon: '80.2120', display_name: '12, Anna Salai, Kodambakkam, Chennai, Tamil Nadu, 600083, India',
  address: {
    house_number: '12', road: 'Anna Salai', neighbourhood: 'Ward 132', suburb: 'Zone 10 Kodambakkam',
    city: 'Chennai Corporation', state_district: 'Chennai', state: 'Tamil Nadu',
    'ISO3166-2-lvl4': 'IN-TN', postcode: '600083', country_code: 'in',
  },
};

// A 1x1 transparent PNG stands in for every map tile.
const TILE = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=', 'base64');

export default async function run() {
  const s = suite('address-map');

  await withBrowser(async (browser) => {
    for (const [label, opts] of [
      ['desktop', { viewport: { width: 1280, height: 800 } }],
      ['mobile', { viewport: { width: 390, height: 844 }, mobile: true }],
    ]) {
      const page = await newPage(browser, { ...opts, api: signedInApi() });
      const geocoderCalls = [];
      await page.route('**/tile.openstreetmap.org/**', (route) => route.fulfill({ status: 200, contentType: 'image/png', body: TILE }));
      await page.route('**/nominatim.openstreetmap.org/**', (route) => {
        const url = new URL(route.request().url());
        geocoderCalls.push(url.pathname);
        const body = url.pathname === '/reverse' ? REVERSE : [];
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
      });

      await page.goto(`${BASE}/customers`);
      await page.getByRole('button', { name: 'Add customer' }).first().click();
      await page.getByRole('button', { name: 'Pick on map' }).click();

      const mapDialog = page.getByRole('dialog', { name: 'Choose the address on the map' });
      await mapDialog.waitFor({ timeout: 10_000 });
      s.check(`${label}: map dialog opens with a Leaflet map`,
        await mapDialog.locator('.leaflet-container').count() === 1);

      const useButton = mapDialog.getByRole('button', { name: 'Use this address' });
      s.check(`${label}: nothing is pre-selected`, await useButton.isDisabled());

      // Let the sheet finish animating and Leaflet re-measure before clicking.
      await page.waitForTimeout(400);
      const map = mapDialog.locator('.leaflet-container');
      const box = await map.boundingBox();
      await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);

      await mapDialog.getByText('12, Anna Salai, Kodambakkam').waitFor({ timeout: 5000 });
      s.check(`${label}: the click reverse-geocodes once`, geocoderCalls.filter((p) => p === '/reverse').length === 1,
        geocoderCalls.join(' '));
      s.check(`${label}: a pin is placed`, await mapDialog.locator('.address-map-pin').count() === 1);
      s.check(`${label}: preview shows city, state and pincode`,
        await mapDialog.getByText('Chennai, Tamil Nadu, 600083').count() === 1);

      await useButton.click();
      await mapDialog.waitFor({ state: 'detached', timeout: 5000 });

      const value = (id) => page.locator(`#${id}`).inputValue();
      s.check(`${label}: address line 1 filled`, await value('addressLine1') === '12, Anna Salai');
      s.check(`${label}: address line 2 filled, ward and zone labels dropped`, await value('addressLine2') === 'Kodambakkam');
      s.check(`${label}: city filled without the civic-body suffix`, await value('city') === 'Chennai');
      s.check(`${label}: pincode filled`, await value('pincode') === '600083');
      s.check(`${label}: GST state derived from ISO code`,
        (await page.locator('#stateName').textContent())?.includes('Tamil Nadu') === true);

      s.check(`${label}: no page errors`, page.__errors.length === 0, page.__errors.join(' | '));
      await page.context().close();
    }
  });

  return s;
}
