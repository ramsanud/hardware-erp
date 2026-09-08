/**
 * The runner. `npm run test:e2e`.
 *
 * Builds nothing and assumes nothing: it starts `vite preview` against
 * whatever is in dist/, runs every spec, and exits non-zero if any assertion
 * failed. `npm run build` first, or pass E2E_BASE_URL to point at a server you
 * are already running (a deployed preview, for instance).
 */
import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';
import { BASE } from './support/harness.mjs';

const SPECS = [
  ['auth', './auth/auth-flows.spec.mjs'],
  ['products', './products/product-grid.spec.mjs'],
  ['navigation', './navigation/responsive.spec.mjs'],
];

const external = Boolean(process.env.E2E_BASE_URL);
let server = null;

async function waitForServer(url, attempts = 40) {
  for (let i = 0; i < attempts; i += 1) {
    try {
      const response = await fetch(url);
      if (response.ok) return true;
    } catch {
      // not up yet
    }
    await sleep(500);
  }
  return false;
}

async function startPreview() {
  const port = new URL(BASE).port || '4173';
  server = spawn(process.execPath,
    ['./node_modules/vite/bin/vite.js', 'preview', '--port', port, '--strictPort'],
    { stdio: 'ignore' });

  if (!(await waitForServer(`${BASE}/login`))) {
    throw new Error(
      `vite preview did not come up on ${BASE}. Run "npm run build" first — the suite `
      + 'runs against the production bundle, not the dev server.');
  }
}

function stopPreview() {
  if (server && !server.killed) server.kill();
}

const only = process.argv[2];

try {
  if (external) {
    console.log(`Using E2E_BASE_URL=${BASE} (not starting a preview server)\n`);
  } else {
    await startPreview();
  }

  const suites = [];
  for (const [name, path] of SPECS) {
    if (only && name !== only) continue;
    console.log(`\n── ${name} ${'─'.repeat(Math.max(0, 60 - name.length))}`);
    const { default: run } = await import(path);
    suites.push(await run());
  }

  if (suites.length === 0) {
    console.error(`\nNo suite named "${only}". Known: ${SPECS.map(([n]) => n).join(', ')}`);
    process.exitCode = 1;
  } else {
    const total = suites.reduce((n, s) => n + s.results.length, 0);
    const failed = suites.flatMap((s) => s.failed);

    console.log(`\n${'═'.repeat(64)}`);
    console.log(`${total - failed.length}/${total} assertions passed`);
    if (failed.length) {
      console.log('\nFailed:');
      failed.forEach((f) => console.log(`  - ${f.label}${f.detail ? ` :: ${f.detail}` : ''}`));
    }
    process.exitCode = failed.length ? 1 : 0;
  }
} catch (error) {
  console.error(`\n${error.message}`);
  process.exitCode = 1;
} finally {
  stopPreview();
}
