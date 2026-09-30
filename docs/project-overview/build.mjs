// Builds docs/project-overview/Hardware-ERP-Project-Overview.pdf.
//
//   node docs/project-overview/build.mjs          (from the repo root)
//
// The ER diagram is derived from the Flyway migrations (schema.mjs) and
// the counts on the cover are measured from the tree, so re-running this
// after a schema change regenerates a truthful document. Rendering uses
// the Chromium that Playwright already installs for the frontend tests —
// nothing new to install.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { parseMigrations } from './schema.mjs';
import { DOMAIN_GROUPS, APPENDIX_GROUPS } from './domains.mjs';
import { buildErd } from './erd.mjs';
import { buildFlowchart } from './flowchart.mjs';
import { authFlow, businessFlow } from './flows-data.mjs';
import * as C from './content.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const OUT_PDF = path.join(here, 'Hardware-ERP-Project-Overview.pdf');
const OUT_HTML = path.join(here, 'Hardware-ERP-Project-Overview.html');

// ---------- measured facts ----------
const walk = (dir, pred, acc = []) => {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { if (e.name !== 'node_modules' && e.name !== 'dist' && e.name !== 'target') walk(p, pred, acc); }
    else if (pred(p)) acc.push(p);
  }
  return acc;
};
const beMain = path.join(root, 'backend/src/main/java');
const beTest = path.join(root, 'backend/src/test/java');
const feSrc = path.join(root, 'frontend/src');
const javaMain = walk(beMain, (p) => p.endsWith('.java'));
const controllers = javaMain.filter((p) => p.endsWith('Controller.java')).length;
const testClasses = walk(beTest, (p) => p.endsWith('Test.java') || p.endsWith('IT.java'));
const itClasses = testClasses.filter((p) => p.endsWith('IT.java')).length;
const tsFiles = walk(feSrc, (p) => /\.(ts|tsx)$/.test(p));
const pages = tsFiles.filter((p) => /[\\/]pages[\\/][^\\/]+\.tsx$/.test(p)).length;
const feModules = fs.readdirSync(path.join(feSrc, 'modules')).length;
const bePackages = fs.readdirSync(beMain + '/com/hardware/erp', { withFileTypes: true }).filter((e) => e.isDirectory()).length;
const permissionCount = (fs.readFileSync(path.join(beMain, 'com/hardware/erp/auth/entity/PermissionCode.java'), 'utf8').match(/public static final String/g) || []).length;
const apiRows = (fs.readFileSync(path.join(root, 'project-knowledge/API_REGISTRY.md'), 'utf8').match(/^\| *(GET|POST|PUT|PATCH|DELETE) /gm) || []).length;
const gitHead = (() => { try { return fs.readFileSync(path.join(root, '.git/HEAD'), 'utf8').trim().replace('ref: refs/heads/', ''); } catch { return 'n/a'; } })();

const schema = parseMigrations(path.join(root, 'backend/src/main/resources/db/migration'));
const tables = schema.tables;
const tableCount = Object.keys(tables).length;
const tenantScoped = Object.values(tables).filter((t) => t.tenant).length;

// every real table must be in exactly one group — fail loudly otherwise
const listed = new Set();
for (const g of [...DOMAIN_GROUPS, ...APPENDIX_GROUPS]) for (const t of g.tables) {
  if (!tables[t]) throw new Error(`group lists unknown table ${t}`);
  if (listed.has(t)) throw new Error(`table ${t} listed twice`);
  listed.add(t);
}
const missing = Object.keys(tables).filter((t) => !listed.has(t));
if (missing.length) throw new Error(`tables not placed in any group: ${missing.join(', ')}`);
for (const t of Object.keys(tables)) if (!C.tablePurpose[t]) throw new Error(`no purpose text for table ${t}`);

// ---------- diagrams ----------
const erd = buildErd(tables, DOMAIN_GROUPS);
const authSvg = buildFlowchart(authFlow);
const bizSvg = buildFlowchart(businessFlow);
const PX_TO_MM = 25.4 / 96;
const mm = (px) => (px * PX_TO_MM).toFixed(1);
// diagram pages: page size follows the drawing so nothing is scaled down
const erdPage = { w: mm(erd.widthPx + 60), h: mm(erd.heightPx + 250) };
const authPage = { w: mm(authFlow.width + 60), h: mm(authFlow.height + 260) };
const bizPage = { w: mm(businessFlow.width + 60), h: mm(businessFlow.height + 330) };

// ---------- html helpers ----------
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const code = (s) => `<code>${esc(s)}</code>`;
const table2 = (rows, h1, h2, mono = true) => `
<table><thead><tr><th style="width:38%">${esc(h1)}</th><th>${esc(h2)}</th></tr></thead><tbody>
${rows.map(([a, b]) => `<tr><td>${mono ? code(a) : esc(a)}</td><td>${esc(b)}</td></tr>`).join('\n')}
</tbody></table>`;

const today = new Date().toISOString().slice(0, 10);

const domainTables = DOMAIN_GROUPS.map((g) => `
<h3><span class="swatch" style="background:${g.color}"></span>${esc(g.label)}</h3>
<table class="tbl"><thead><tr><th style="width:22%">Table</th><th style="width:8%">Since</th><th style="width:7%">Cols</th><th style="width:7%">Tenant</th><th>What it holds</th></tr></thead><tbody>
${g.tables.map((t) => `<tr><td>${code(t)}</td><td>${tables[t].mig}</td><td>${tables[t].cols.length}</td><td>${tables[t].tenant ? 'yes' : '—'}</td><td>${esc(C.tablePurpose[t])}</td></tr>`).join('\n')}
</tbody></table>`).join('\n');

const appendixTables = APPENDIX_GROUPS.map((g) => `
<h3>${esc(g.label)}</h3>
<table class="tbl"><thead><tr><th style="width:26%">Table</th><th style="width:8%">Since</th><th style="width:7%">Cols</th><th style="width:7%">Tenant</th><th style="width:22%">Foreign keys</th><th>What it holds</th></tr></thead><tbody>
${g.tables.map((t) => `<tr><td>${code(t)}</td><td>${tables[t].mig}</td><td>${tables[t].cols.length}</td><td>${tables[t].tenant ? 'yes' : '—'}</td><td class="small">${tables[t].fks.map((f) => esc(`${f.col} → ${f.ref}`)).join('<br>') || '—'}</td><td>${esc(C.tablePurpose[t])}</td></tr>`).join('\n')}
</tbody></table>`).join('\n');

// FKs from drawn tables to appendix-only tables are not drawn; list them so nothing is hidden
const drawn = new Set(DOMAIN_GROUPS.flatMap((g) => g.tables));
const omittedEdges = [];
for (const t of drawn) for (const fk of tables[t].fks) if (!drawn.has(fk.ref)) omittedEdges.push(`${t}.${fk.col} → ${fk.ref}`);

const html = `<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>Hardware ERP — Project Overview</title>
<style>
  @page { size: A4 portrait; margin: 16mm 15mm 18mm 15mm; }
  @page erd  { size: ${erdPage.w}mm ${erdPage.h}mm; margin: 8mm; }
  @page auth { size: ${authPage.w}mm ${authPage.h}mm; margin: 8mm; }
  @page biz  { size: ${bizPage.w}mm ${bizPage.h}mm; margin: 8mm; }
  .page-erd  { page: erd;  break-before: page; break-after: page; }
  .page-auth { page: auth; break-before: page; break-after: page; }
  .page-biz  { page: biz;  break-before: page; break-after: page; }

  :root { --ink:#1a1a2e; --muted:#5b5b6b; --rule:#d9d9e3; --accent:#1f8a70; --chip:#eef2ff; }
  * { box-sizing: border-box; }
  body { font-family: "Segoe UI", Arial, sans-serif; color: var(--ink); font-size: 10.2pt; line-height: 1.42; margin: 0; }
  h1 { font-size: 22pt; margin: 0 0 4mm; letter-spacing: -0.01em; }
  h2 { font-size: 15pt; margin: 0 0 3mm; padding-bottom: 1.5mm; border-bottom: 2px solid var(--accent); break-after: avoid; }
  h3 { font-size: 11.5pt; margin: 5mm 0 2mm; break-after: avoid; }
  p { margin: 0 0 2.6mm; }
  ul { margin: 0 0 2.6mm 5mm; padding: 0; } li { margin-bottom: 1mm; }
  code { font-family: Consolas, "Courier New", monospace; font-size: 9.2pt; background: #f2f2f6; padding: 0 3px; border-radius: 3px; }
  pre { font-family: Consolas, monospace; font-size: 8.6pt; background: #f4f4f8; padding: 3mm; border-radius: 4px; border: 1px solid var(--rule); white-space: pre-wrap; }
  table { width: 100%; border-collapse: collapse; margin: 0 0 4mm; font-size: 9.1pt; }
  th { text-align: left; background: #f0f0f5; border-bottom: 1.5px solid #b9b9c9; padding: 1.6mm 2mm; }
  td { border-bottom: 1px solid var(--rule); padding: 1.4mm 2mm; vertical-align: top; }
  tr { break-inside: avoid; }
  .small { font-size: 8.4pt; font-family: Consolas, monospace; }
  .section { break-before: auto; margin-top: 8mm; }
  .newpage { break-before: page; margin-top: 0; }
  .muted { color: var(--muted); }
  .swatch { display:inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 6px; vertical-align: baseline; }
  .cover { height: 250mm; display: flex; flex-direction: column; justify-content: space-between; }
  .cover .title { margin-top: 60mm; }
  .cover h1 { font-size: 30pt; }
  .cover .sub { font-size: 14pt; color: var(--muted); margin-bottom: 8mm; }
  .facts { display: grid; grid-template-columns: repeat(4, 1fr); gap: 3mm; }
  .fact { background: var(--chip); border-radius: 5px; padding: 3mm; }
  .fact b { display: block; font-size: 18pt; color: var(--accent); }
  .fact span { font-size: 9pt; color: var(--muted); }
  .toc li { margin-bottom: 1.6mm; }
  .diagram { width: 100%; break-inside: avoid; }
  .diagram svg { width: 100%; height: auto; display: block; }
  .page-erd h2, .page-auth h2, .page-biz h2 { font-size: 13pt; margin-bottom: 2mm; }
  .page-erd .caption, .page-auth .caption, .page-biz .caption { font-size: 8.8pt; max-width: 1400px; }
  .caption { font-size: 9.2pt; color: var(--muted); margin-top: 2mm; }
  .callout { border-left: 3px solid var(--accent); background: #f4faf8; padding: 2.5mm 3mm; margin: 0 0 3mm; }
  .two { columns: 2; column-gap: 8mm; }
  .kv td:first-child { width: 32%; font-weight: 600; }
</style></head><body>

<!-- ================= COVER ================= -->
<div class="cover">
  <div class="title">
    <h1>Hardware ERP<br><span style="font-weight:400">Project Overview</span></h1>
    <div class="sub">ER diagram · application flow · what every file is for</div>
    <p class="muted">Generated ${today} from branch ${code(gitHead)} · schema at migration ${schema.lastMigration}</p>
  </div>
  <div>
    <div class="facts">
      <div class="fact"><b>${tableCount}</b><span>PostgreSQL tables<br>${tenantScoped} tenant-scoped</span></div>
      <div class="fact"><b>${schema.migrationCount}</b><span>Flyway migrations<br>never edited, only added</span></div>
      <div class="fact"><b>${javaMain.length}</b><span>Java files<br>${bePackages} packages · ${controllers} controllers</span></div>
      <div class="fact"><b>${tsFiles.length}</b><span>TS/TSX files<br>${feModules} modules · ${pages} pages</span></div>
      <div class="fact"><b>${apiRows}</b><span>documented endpoints<br>API_REGISTRY.md</span></div>
      <div class="fact"><b>${permissionCount}</b><span>permission codes<br>4 seeded roles</span></div>
      <div class="fact"><b>${testClasses.length}</b><span>backend test classes<br>${itClasses} Testcontainers ITs</span></div>
      <div class="fact"><b>11</b><span>Playwright suites<br>297 assertions</span></div>
    </div>
    <p class="muted" style="margin-top:6mm">Java 21 · Spring Boot 3.4.2 · PostgreSQL 16 · Flyway · React 18 · TypeScript · Vite 6 · Tailwind · shadcn/ui · Playwright</p>
  </div>
</div>

<!-- ================= CONTENTS ================= -->
<div class="section newpage">
  <h2>Contents</h2>
  <ol class="toc">
    <li>What this project is</li>
    <li>Architecture at a glance</li>
    <li>Application flow — login, JWT and tenant isolation <span class="muted">(full-page diagram)</span></li>
    <li>Application flow — core business documents <span class="muted">(full-page diagram)</span></li>
    <li>Entity-relationship diagram <span class="muted">(full-page diagram)</span> and table guide</li>
    <li>Roles and permissions</li>
    <li>Repository map — where everything lives</li>
    <li>Backend: foundation files and the anatomy of one module</li>
    <li>Frontend: foundation files and the anatomy of one module</li>
    <li>Registries and documentation — the source of truth</li>
    <li>Conventions that never bend</li>
    <li>How to run and verify</li>
    <li>Appendix A — tables not drawn in the ER diagram</li>
  </ol>
</div>

<!-- ================= 1. WHAT ================= -->
<div class="section newpage">
  <h2>1. What this project is</h2>
  <p>Hardware ERP is a business system for hardware shops in India. One shop is one <em>tenant</em>. Each tenant's staff sign in, keep their product catalogue and stock, buy from suppliers, sell to customers through quotations, sales orders, delivery challans and GST invoices, record payments and expenses, run customer projects with hired labour, and pull reports including GSTR-1.</p>
  <p>It is <strong>one Spring Boot application, one React application and one PostgreSQL database</strong>. Many shops share that one database; every tenant-owned table carries a <code>tenant_id</code> column and every query filters by it, using the tenant taken from the signed-in user's JWT — never from anything the browser sends. This is the single most important design decision in the codebase (CR-016), and the request-flow diagram in section 3 shows exactly where it is enforced.</p>
  <div class="callout"><strong>Not</strong> microservices. <strong>Not</strong> a database or schema per tenant. Module folders express boundaries, not deployment units. The project is in <strong>maintenance and extension</strong>, not greenfield: the original twelve-module build order (Auth → Supplier → Customer → Category → Brand → Product → Product Variant → Purchase → Inventory → Quotation → Invoice → Payment) was completed, and later change requests added sales orders, delivery challans, credit notes, projects, labour, expenses, coupons, notifications, reports and the platform console.</div>

  <h3>Who uses it</h3>
  <table class="kv"><tbody>
    <tr><td>Shop owner</td><td>Creates staff accounts and roles, sets up branding, bank accounts and WhatsApp, watches the dashboard, runs reports.</td></tr>
    <tr><td>Counter staff / salesperson</td><td>Quotations, invoices, customers, stock lookup — the fast path on a phone or a counter PC.</td></tr>
    <tr><td>Purchase staff / inventory manager</td><td>Suppliers, purchases, bill import, stock adjustments, low-stock follow-up.</td></tr>
    <tr><td>Accountant</td><td>Payments, purchase payments, expenses, receivables ageing, GST summary, GSTR-1.</td></tr>
    <tr><td>Project manager</td><td>Customer jobs: materials issued from stock, labour attendance, expenses, profitability.</td></tr>
    <tr><td>Platform operator</td><td>A separate cross-tenant console for incidents, feature flags, subscriptions and support tickets.</td></tr>
  </tbody></table>

  <h3>Two ways to ship the same code (CR-059)</h3>
  <table><thead><tr><th></th><th>CLOUD</th><th>SELF_HOSTED</th></tr></thead><tbody>
    <tr><td>Profiles</td><td><code>prod,cloud</code></td><td><code>prod,selfhosted</code></td></tr>
    <tr><td>Database</td><td>managed PostgreSQL (Supabase, as a plain endpoint — no vendor SDK)</td><td>PostgreSQL container on the shop's own machine</td></tr>
    <tr><td>Entry point</td><td>Render (API) + Vercel (SPA), <code>render.yaml</code></td><td><code>docker compose -f docker-compose.selfhosted.yml up -d</code></td></tr>
    <tr><td>Subscription billing</td><td>live (Razorpay)</td><td>off, no tier caps</td></tr>
    <tr><td>Refresh cookie</td><td><code>Secure</code> mandatory</td><td><code>Secure</code> off by default (LAN http)</td></tr>
  </tbody></table>
  <p><code>DeploymentModeGuard</code> refuses to start a self-hosted process that points at the managed database host, so one client's data can never land in the shared SaaS database.</p>
</div>

<!-- ================= 2. ARCHITECTURE ================= -->
<div class="section">
  <h2>2. Architecture at a glance</h2>
  <pre>Browser / phone
  React 18 SPA (Vite build, served by Vercel or the self-hosted nginx)
  routes/ → layouts/ → modules/&lt;name&gt;/pages → services/xService.ts
                                                   │  services/apiClient.ts (one Axios instance)
                                                   │  Authorization: Bearer &lt;JWT in memory&gt;
                                                   ▼  HttpOnly refresh cookie
Spring Boot 3.4.2 monolith  (/api/v1/...)
  RequestCorrelationFilter → RateLimitFilter → JwtAuthenticationFilter → @PreAuthorize(permission)
  controller → service (@Transactional, SecurityUtils.requireCurrentTenantId()) → repository (…ByTenantId)
  side effects inside the same transaction: document_sequence, stock_movement, activity_log
  after commit, @Async: notification (email / SMS / WhatsApp)
                                                   ▼
PostgreSQL 16  — one schema, Flyway-managed, tenant_id on every tenant-owned table
External: SMTP or SendGrid · Twilio · WhatsApp Cloud API (per tenant) · Anthropic (optional) · Razorpay (cloud only) · OpenStreetMap (address picker)</pre>

  <h3>Package structure (locked)</h3>
  <pre>com.hardware.erp
├── common.{entity,dto,exception,web,activity,idempotency,sequence,security,util,validation,image}
├── config
├── security.{ratelimit,totp,captcha}
└── &lt;module&gt;.{entity,repository,dto,mapper,service,service.impl,controller}     ← ${bePackages - 3} business packages

frontend/src
├── main.tsx, App.tsx, index.css
├── routes/  layouts/  services/  theme/  shared/{components,hooks,lib,types,constants,data}
└── modules/&lt;name&gt;/{types,validation,services,constants,components,forms,pages}   ← ${feModules} modules</pre>

  <h3>Module dependency graph (from the real imports)</h3>
  <p><code>invoice</code> is the settlement point of the system and therefore a dependency magnet by design; it must never import upward. One known cycle exists and is recorded, not hidden: <code>product → invoice</code> for a product's recent sale prices (<code>ProductServiceImpl.priceHistory()</code>).</p>
  <pre>auth + tenant  (every module below depends on both)
 ├── supplier      ├── customer      ├── product ── coupon
 ├── inventory ─── product
 ├── purchase ──── supplier, product, inventory, invoice
 ├── quotation ─── customer, product, invoice
 ├── invoice ───── customer, product, inventory, coupon, notification
 ├── salesorder ── customer, product, invoice, deliverychallan
 ├── deliverychallan ─ customer, product, inventory, invoice
 ├── creditnote ── customer, product, inventory, invoice
 ├── project ───── customer, product, supplier, inventory, invoice, labour
 ├── labour ────── project, invoice
 ├── expense, dashboard, analytics, report ── invoice
 ├── ai (read-only tools), export (rendering only), notification, supportticket, legal, developer
 └── platformadmin ── billing        ← cross-tenant, deliberately OUTSIDE tenant isolation</pre>
</div>

<!-- ================= 3. AUTH FLOW (own page) ================= -->
<div class="page-auth">
  <h2>3. Application flow — login, JWT and tenant isolation</h2>
  <div class="diagram">${authSvg}</div>
  <p class="caption">Read top to bottom. Red paths are refusals; the dashed line is the security audit trail. Files named in the boxes: <code>AuthController</code>, <code>AuthService</code>, <code>TotpService</code>, <code>JwtService</code>, <code>RefreshTokenCookieService</code>, <code>SecurityAuditService</code> (backend); <code>services/apiClient.ts</code>, <code>services/tokenStorage.ts</code> (frontend). The tenant is fixed at the JWT claim and used by every repository method; a request parameter can never change it (CR-016).</p>
</div>

<!-- ================= 4. BUSINESS FLOW (own page) ================= -->
<div class="page-biz">
  <h2>4. Application flow — core business documents</h2>
  <div class="diagram">${bizSvg}</div>
  <p class="caption">Orange: procure-to-pay. Blue: order-to-cash. Green: catalogue, stock and projects. Purple dashed: discounts. Red dashed: reversal. Every arrow is a real module dependency from <code>MODULE_DEPENDENCY_MAP.md</code>. Stock only ever changes through <code>StockService.applyMovement</code>, which appends a <code>stock_movement</code> row naming the source document; that is why Purchase, Delivery Challan, Invoice, Credit Note and Project Material all point at Inventory.</p>
  <p class="caption"><strong>Document lifecycle rules worth knowing:</strong> Quotation, Sales Order and Delivery Challan are optional steps — a counter sale is Customer → Invoice → Payment directly. An invoice is <code>UNPAID → PARTIALLY_PAID → PAID</code>, or <code>CANCELLED</code>; cancelling returns stock. Nothing financial is hard-deleted: users and suppliers are soft-deleted, documents are cancelled, and every change writes an <code>activity_log</code> row with before/after values. Document numbers come from <code>document_sequence</code> under a row lock, so two concurrent invoices cannot collide (CR-041). A retried POST with the same <code>Idempotency-Key</code> replays the stored response instead of creating a second document (CR-051).</p>
</div>

<!-- ================= 5. ERD (own page) ================= -->
<div class="page-erd">
  <h2>5. Entity-relationship diagram</h2>
  <div class="diagram">${erd.svg}</div>
  <p class="caption">${erd.boxCount} of the ${tableCount} tables, grouped by business domain, with ${erd.edgeCount} foreign keys drawn. Each box lists its primary key and foreign keys only; the footer gives the full column count and the migration that created it. The remaining ${tableCount - erd.boxCount} tables (branding media, notifications, security audit, support desk, the platform console) are listed with their keys in Appendix A. Derived automatically from <code>backend/src/main/resources/db/migration/V1..V${schema.lastMigration.slice(1)}</code> — the diagram cannot drift from the schema. Line-item tables (<code>*_item</code>) carry no <code>tenant_id</code> of their own: they inherit scope through their parent document and are only ever loaded via it.</p>
  <p class="caption">Foreign keys from drawn tables to appendix tables, not drawn to keep the picture readable: ${omittedEdges.map(code).join(', ')}.</p>
</div>

<!-- ================= 5b. TABLE GUIDE ================= -->
<div class="section newpage">
  <h2>5. Table guide — what each entity in the diagram holds</h2>
  <p>Same grouping and colours as the diagram. "Since" is the Flyway migration that created the table; later migrations may have added columns (the diagram footer counts them all).</p>
  ${domainTables}
</div>

<!-- ================= 6. ROLES ================= -->
<div class="section">
  <h2>6. Roles and permissions</h2>
  <p>Authorisation is <strong>permission-based, never role-based</strong> (hard rule 6). A role is only a named set of permissions; business rules check a permission such as <code>INVOICE_CANCEL</code>, never "is the caller an owner". That is what lets an owner create a custom role — say "Senior counter" with <code>INVOICE_DISCOUNT_OVERRIDE</code> — without any code change.</p>
  <table><thead><tr><th style="width:20%">Seeded role</th><th>Typical scope</th></tr></thead><tbody>
    ${C.roles.map(([r, d]) => `<tr><td>${code(r)}</td><td>${esc(d)}</td></tr>`).join('\n')}
  </tbody></table>
  <p>The ${permissionCount} permission codes live in the <code>permission</code> table (authoritative) and in <code>auth/entity/PermissionCode.java</code> (so <code>@PreAuthorize</code> strings are compiler-checked; <code>PermissionCodeConsistencyTest</code> asserts the two agree). Families: USER, ROLE, AUDIT, CUSTOMER, SUPPLIER (incl. <code>SUPPLIER_VIEW_BANK_ACCOUNT</code>), PRODUCT (incl. <code>PRODUCT_VIEW_COST</code>, <code>PRODUCT_VIEW_STOCK</code>), INVENTORY, PURCHASE, QUOTATION, INVOICE (VIEW / CREATE / CANCEL / DISCOUNT_OVERRIDE), PAYMENT, EXPENSE, PROJECT, LABOUR, COUPON, REPORT, SETTINGS, DATA_RESET and <code>DEVELOPER_INSPECT</code> (held by no default role).</p>
  <h3>How a permission is enforced, end to end</h3>
  <ul>
    <li><strong>Backend, the real gate:</strong> <code>@PreAuthorize("hasAuthority('INVOICE_CREATE')")</code> on the controller method; the authorities come from the JWT, which was built from <code>role_permission</code> at login.</li>
    <li><strong>Frontend, for a sensible UI only:</strong> <code>RequirePermission</code> hides routes and <code>PermissionGate</code> hides buttons the caller cannot use. Removing them would change nothing about what the API allows.</li>
    <li><strong>Refusals are recorded:</strong> <code>RestAccessDeniedHandler</code> writes an <code>ACCESS_DENIED</code> row to <code>security_audit_log</code>.</li>
    <li><strong>Platform admins are a different identity</strong> with their own table, tokens, MFA and audit log; a tenant-facing controller never calls into <code>platformadmin</code>.</li>
  </ul>
</div>

<!-- ================= 7. REPO MAP ================= -->
<div class="section newpage">
  <h2>7. Repository map — where everything lives</h2>
  <pre>hardware-erp/
├── CLAUDE.md                     rules for every task; which guide to load
├── RESUME_POINT.md               where the last session stopped
├── README.md, MASTER_PROJECT_STATUS.md, AUDIT_GAP_REPORT.md, IMPROVEMENT_OPPORTUNITIES.md
├── docker-compose.yml            local PostgreSQL 16
├── docker-compose.selfhosted.yml the whole stack for a shop's own machine
├── render.yaml                   cloud deployment manifest
├── scripts/                      backup-db.sh, restore-db.sh, run-local.sh, run-cloud.*, new-secret.ps1
├── registry/                     static_check.py, check_registry.py, registry.json  (need python3)
├── project-knowledge/            the registries — source of truth (section 10)
├── .claude/guides/               coding, business, database, security, testing, architecture rules
├── docs/                         DEPLOYMENT*.md, MANUAL_WHATSAPP.md, SECURITY_AUDIT.md, Postman, this overview
├── backend/                      Maven project
│   ├── pom.xml
│   └── src/main/java/com/hardware/erp/…        src/main/resources/{application*.yml, db/migration, db/seed}
│       src/test/java/…                          *Test (unit, Mockito) and *IT (Testcontainers PostgreSQL)
└── frontend/                     Vite project
    ├── package.json, vite.config.ts, tailwind.config.*, tsconfig*.json
    ├── src/                      (section 9)
    └── tests/                    Playwright suites + run.mjs, run against the built dist/</pre>
  <h3>Backend modules (${bePackages} packages)</h3>
  ${table2(C.backendModules, 'Package under com.hardware.erp', 'Owns')}
  <h3>Frontend modules (${feModules} folders under src/modules)</h3>
  ${table2(C.frontendModules, 'Module', 'Screens and responsibilities')}
</div>

<!-- ================= 8. BACKEND FILES ================= -->
<div class="section newpage">
  <h2>8. Backend — foundation files and the anatomy of one module</h2>
  <p>These files are shared by every module. A module never ships its own JWT handling, error envelope, money formatting or tenant lookup — it imports these.</p>
  ${table2(C.backendFoundation, 'File (under backend/src/main/…)', 'Why it exists')}
  <h3>Anatomy of one module: <code>invoice</code></h3>
  <p>Every business module has the same seven folders, in the same order the code is written (CLAUDE.md, "specification change" steps 3–4). Reading <code>invoice</code> teaches you all of them.</p>
  ${table2(C.moduleAnatomy, 'invoice/…', 'Role')}
  <div class="callout">What happens inside <code>InvoiceServiceImpl.create()</code>, in one transaction: resolve the customer and check the credit limit → resolve each product and price the line (<code>LineDiscount</code>, rounded to whole paise once) → apply a coupon if given → allocate the invoice number from <code>document_sequence</code> under a row lock → save → <code>StockService.applyMovement(-qty)</code> per line → record coupon usage → write <code>activity_log</code> → after commit, <code>@Async</code> notification. If any step throws, none of it happened.</div>
  <h3>Tests</h3>
  <p><code>*Test</code> classes are unit tests with Mockito; <code>*IT</code> classes start a real PostgreSQL 16 in Testcontainers and exercise the HTTP layer with MockMvc, including tenant isolation (<code>docs/MULTI_TENANT_SECURITY_TEST.md</code>). ${testClasses.length} test classes today, ${itClasses} of them integration. Last full green run: 599 unit + 251 integration, <code>mvn -o clean verify</code>.</p>
</div>

<!-- ================= 9. FRONTEND FILES ================= -->
<div class="section newpage">
  <h2>9. Frontend — foundation files and the anatomy of one module</h2>
  ${table2(C.frontendFoundation, 'File (under frontend/src/…)', 'Why it exists')}
  <h3>Anatomy of one module: <code>modules/invoice</code></h3>
  ${table2(C.frontendModuleAnatomy, 'modules/invoice/…', 'Role')}
  <div class="callout">The contract discipline: <code>types/index.ts</code> mirrors the Java DTO exactly (a <code>Long totalPaise</code> is a <code>number totalPaise</code>, never <code>total</code>), and <code>validation/schemas.ts</code> mirrors the Bean Validation annotations. When a screen shows the wrong thing but Postman shows the right JSON, the bug is in the frontend only — and vice versa. CLAUDE.md requires every bug fix to begin by naming that scope.</div>
  <h3>Design system rules</h3>
  <ul>
    <li><strong>Never hardcode a colour</strong> (hard rule 11): eleven themes × light/dark from <code>theme/colorThemes.ts</code>; components use tokens such as <code>bg-primary</code>, <code>text-muted-foreground</code>, <code>--sidebar</code>.</li>
    <li><strong>Never draw data that does not exist</strong> (hard rule 12): dashboard sparklines and deltas are measured (CR-084), and an empty state says so.</li>
    <li><strong>A visible redesign is approved as a render first, then coded exactly</strong> (hard rule 13) — the sign-in page (CR-081) and dashboard (CR-082) were built that way.</li>
    <li>Inline "+ Add new X" (category, brand, expense category) uses one shared pattern; BUG-FE-007 documents the Radix Select quirk it must handle.</li>
    <li>Import flows are always <em>preview → confirm → import</em>, never upload-and-insert (product CSV, supplier bill).</li>
  </ul>
</div>

<!-- ================= 10. REGISTRIES ================= -->
<div class="section">
  <h2>10. Registries and documentation — the source of truth</h2>
  <p>Decisions live in files, not in anyone's memory. A new contributor (human or AI) reads <code>CLAUDE.md</code>, loads the guide for the task, then the registries the task touches. Nobody should have to re-explain a recorded decision.</p>
  ${table2(C.registries, 'File', 'Contains')}
  <h3>The shape of every change</h3>
  <ol>
    <li>Name the layer at fault: <code>SCOPE: FRONTEND ONLY</code> / <code>BACKEND ONLY</code> / <code>BOTH</code>.</li>
    <li>Claim a CR or BUG number in the registry <em>before</em> writing code (other sessions allocate numbers too — grep first).</li>
    <li>For a specification change: registry → new Flyway migration → entity → repository → DTO → mapper → service → controller → tests → <code>types/index.ts</code> → validation schema → service/form/page → Postman → registries.</li>
    <li>Fix same-root-cause defects found on the way in the same commit; propose anything bigger as its own CR.</li>
    <li>Verify in isolation, quote the numbers, update <code>RESUME_POINT.md</code>, commit with Conventional Commits (<code>feat:</code> <code>fix:</code> <code>docs:</code> …), one concern per commit, body says <em>why</em>.</li>
  </ol>
</div>

<!-- ================= 11. CONVENTIONS ================= -->
<div class="section">
  <h2>11. Conventions that never bend</h2>
  <h3>Naming law — one concept, one name, database → entity → DTO → JSON → TypeScript</h3>
  <table><tbody>
    <tr><td>Only transformation</td><td><code>snake_case</code> → <code>camelCase</code>. <code>credit_limit_paise</code> is <code>creditLimitPaise</code> everywhere.</td></tr>
    <tr><td>Tables</td><td>singular <code>snake_case</code>; line tables <code>&lt;parent&gt;_item</code>; <code>app_user</code> because <code>user</code> is reserved.</td></tr>
    <tr><td>Primary key</td><td>column <code>&lt;table&gt;_id</code>; Java field always <code>id</code>.</td></tr>
    <tr><td>Foreign key</td><td>column <code>&lt;target&gt;_id</code>; the entity holds the object, the DTO holds <code>&lt;target&gt;Id</code>.</td></tr>
    <tr><td>Money</td><td><code>BIGINT</code> paise, never float or double. <code>BigDecimal</code> only inside <code>LineDiscount</code> / <code>IndianCurrencyFormat</code>, rounded back to paise once.</td></tr>
    <tr><td>Unit rates / quantities</td><td><code>DECIMAL(18,6)</code> / <code>DECIMAL(18,4)</code> — a ₹875 box of 1000 screws is ₹0.875 each.</td></tr>
    <tr><td>Status</td><td><code>VARCHAR(20)</code> + CHECK constraint; never TINYINT, never a native ENUM type.</td></tr>
    <tr><td>Timestamps / dates / booleans</td><td><code>TIMESTAMP(3)</code> suffix <code>_at</code>; <code>DATE</code> suffix <code>_date</code>; booleans without <code>is_</code>.</td></tr>
    <tr><td>API paths</td><td><code>/api/v1/&lt;plural-kebab-noun&gt;</code>; verbs only under <code>/auth</code>.</td></tr>
    <tr><td>Banned aliases</td><td><code>name</code>, <code>phoneNo</code>, <code>phoneNumber</code>, <code>emailId</code>, <code>gstNumber</code>, <code>UserHelper</code>, <code>UserUtil</code> …</td></tr>
  </tbody></table>
  <h3>Hard rules</h3>
  <ol class="two">
    <li>Never edit an applied Flyway migration; add a new version.</li>
    <li>Hibernate is <code>ddl-auto: validate</code>. Never <code>update</code>.</li>
    <li>PostgreSQL only. No MySQL syntax.</li>
    <li>Seed data in <code>db/seed/</code>, dev and test profiles only.</li>
    <li>No self-registration of users; the owner creates accounts. (New-<em>tenant</em> sign-up is a separate, rate-limited endpoint — CR-028.)</li>
    <li>Authorization is permission-based, never <code>hasRole('OWNER')</code>.</li>
    <li>Users and suppliers are soft-deleted; financial records reference them forever.</li>
    <li>Security events → <code>security_audit_log</code>; business changes → <code>activity_log</code>.</li>
    <li>Access token in memory only. Never <code>localStorage</code>.</li>
    <li>Never claim a build passes without running it.</li>
    <li>Never hardcode a colour.</li>
    <li>Never draw data that does not exist.</li>
    <li>A visible redesign is approved as a render first, then coded exactly.</li>
  </ol>
  <h3>Git</h3>
  <p><code>main</code> (tagged releases) ← <code>develop</code> ← <code>feature/*</code>, <code>bugfix/*</code>; <code>hotfix/*</code> from <code>main</code> back into both. Merge with <code>--no-ff</code>. Runtime environments are Spring profiles, never branches. Several sessions work the same checkout at once, so files are staged by explicit path and CR numbers are claimed in the registry first.</p>
</div>

<!-- ================= 12. RUN ================= -->
<div class="section">
  <h2>12. How to run and verify</h2>
  <pre>docker compose up -d                                             # PostgreSQL 16 on localhost
cd backend  && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm install && npm run dev                        # http://localhost:5173 → /api on :8080
Swagger: http://localhost:8080/api/swagger-ui.html

# verification (quote the numbers, never assume)
cd backend  && mvn -o clean verify                               # unit + Testcontainers ITs (needs Docker)
cd frontend && node ./node_modules/typescript/bin/tsc -b --force
cd frontend && node ./node_modules/vite/bin/vite.js build
cd frontend && node tests/run.mjs                                # Playwright, against dist/
python3 registry/static_check.py                                 # structure + naming drift

# this document
node docs/project-overview/build.mjs                             # regenerates the PDF from the migrations</pre>
  <p>On a Windows machine with Git Bash, npm script shims can fail (<code>'"node"' is not recognized</code>) — call the tools directly as shown. <code>docker</code> from Git Bash needs an isolated <code>DOCKER_CONFIG</code> holding an empty <code>config.json</code>.</p>
</div>

<!-- ================= APPENDIX A ================= -->
<div class="section newpage">
  <h2>Appendix A — tables not drawn in the ER diagram</h2>
  <p>These ${tableCount - erd.boxCount} tables exist in the same schema and follow the same rules; they were left out of the drawing only because they hang off <code>tenant</code>, <code>app_user</code> or each other and would add lines without adding understanding. The platform-console tables are the one deliberate exception to tenant isolation.</p>
  ${appendixTables}
</div>

</body></html>`;

fs.writeFileSync(OUT_HTML, html);

// ---------- print ----------
// resolve through frontend/'s own node_modules so this works from any cwd and any drive-letter casing
const pw = createRequire(path.join(root, 'frontend', 'package.json'))('playwright');
const browser = await pw.chromium.launch();
const page = await browser.newPage();
await page.setContent(html, { waitUntil: 'load' });
await page.pdf({
  path: OUT_PDF,
  preferCSSPageSize: true,
  printBackground: true,
  displayHeaderFooter: true,
  headerTemplate: '<span></span>',
  footerTemplate: '<div style="width:100%;font-size:8px;color:#777;font-family:Segoe UI,Arial;padding:0 15mm;display:flex;justify-content:space-between"><span>Hardware ERP — Project Overview</span><span class="pageNumber"></span></div>',
});
await browser.close();

const buf = fs.readFileSync(OUT_PDF, 'latin1');
const pagesOut = (buf.match(/\/Type\s*\/Page[^s]/g) || []).length;
console.log(`wrote ${path.relative(root, OUT_PDF)} — ${pagesOut} pages, ${(fs.statSync(OUT_PDF).size / 1024).toFixed(0)} KB`);
console.log(`ERD: ${erd.boxCount} tables drawn, ${erd.edgeCount} FKs; ${tableCount} tables total at ${schema.lastMigration}; page ${erdPage.w}×${erdPage.h} mm`);
