// Builds docs/how-to-start/Hardware-ERP-How-to-Start.pdf.
//
//   node docs/how-to-start/build.mjs          (from the repo root)
//
// template.html is the guide; {{LOGIN_SCREENSHOT}} is replaced with
// login-page.png inlined as a data URI so the PDF is self-contained.
// Rendering uses the Chromium that Playwright already installs for the
// frontend tests - the same arrangement docs/project-overview/build.mjs
// uses, so nothing new is installed. The source used to live only in a
// session's temp folder, which is why it is here now.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const OUT_PDF = path.join(here, 'Hardware-ERP-How-to-Start.pdf');
const OUT_HTML = path.join(here, '.final.html'); // ignored: 600 KB of inlined PNG, regenerated every run

const template = fs.readFileSync(path.join(here, 'template.html'), 'utf8');
const png = fs.readFileSync(path.join(here, 'login-page.png')).toString('base64');
fs.writeFileSync(OUT_HTML, template.replace('{{LOGIN_SCREENSHOT}}', 'data:image/png;base64,' + png));

const pw = createRequire(path.join(root, 'frontend', 'package.json'))('playwright');
const browser = await pw.chromium.launch();
try {
  const page = await browser.newPage();
  await page.goto(pathToFileURL(OUT_HTML).href, { waitUntil: 'load' });
  await page.pdf({ path: OUT_PDF, format: 'A4', printBackground: true,
    margin: { top: '0mm', bottom: '0mm', left: '0mm', right: '0mm' } });
} finally {
  await browser.close();
}
fs.unlinkSync(OUT_HTML);
console.log(`wrote ${path.relative(root, OUT_PDF)} - ${Math.round(fs.statSync(OUT_PDF).size / 1024)} KB`);
