// Renders the curated domain groups (domains.mjs) against the parsed schema
// (schema.mjs) as one SVG: column-per-domain, box-per-table, PK/FK rows only
// (full column lists live in Appendix A as text), curved connectors between
// real foreign keys. Nothing here is laid out by hand — every box height,
// every line endpoint is computed from the parsed migrations, so the
// diagram cannot silently drift from the schema the way a hand-drawn one
// would.

const BOX_W_MIN = 230;
const BOX_W_MAX = 330;
const CHAR_W = 6.05; // approx advance width of Consolas at font-size 10.3
const HEADER_H = 26;
const ROW_H = 16;
const FOOTER_H = 18;
const COL_GAP = 92;
const ROW_GAP = 34;
const TOP_MARGIN = 74;
const SIDE_MARGIN = 40;
const BOTTOM_MARGIN = 40;

function esc(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function boxRows(table) {
  const pkCols = table.cols.filter((c) => c.pk);
  const fkCols = table.fks.filter((fk) => !pkCols.some((p) => p.name === fk.col));
  const rows = [];
  for (const c of pkCols) rows.push({ text: `🔑 ${c.name}`, kind: 'pk' });
  for (const fk of fkCols) rows.push({ text: `↳ ${fk.col} → ${fk.ref}`, kind: 'fk' });
  return rows;
}

export function buildErd(tables, domainGroups) {
  const layout = {}; // name -> { x, y, w, h, rows, group, headerY }
  let x = SIDE_MARGIN;
  const columns = [];

  for (const group of domainGroups) {
    // Column width adapts to its widest row/name so long FK labels
    // (e.g. "converted_delivery_challan_id -> delivery_challan") never
    // overflow the box instead of forcing one fixed width on every table.
    let colW = BOX_W_MIN;
    const rowsByTable = {};
    for (const name of group.tables) {
      const t = tables[name];
      if (!t) throw new Error(`domain group "${group.id}" references unknown table "${name}"`);
      const rows = boxRows(t);
      rowsByTable[name] = rows;
      const longest = Math.max(name.length + 2, ...rows.map((r) => r.text.length), 26);
      colW = Math.min(BOX_W_MAX, Math.max(colW, longest * CHAR_W + 24));
    }
    let y = TOP_MARGIN;
    const boxes = [];
    for (const name of group.tables) {
      const t = tables[name];
      const rows = rowsByTable[name];
      const h = HEADER_H + rows.length * ROW_H + FOOTER_H;
      layout[name] = { x, y, w: colW, h, rows, group, table: t, headerCenterY: y + HEADER_H / 2 };
      boxes.push(name);
      y += h + ROW_GAP;
    }
    columns.push({ group, x, w: colW, bottom: y - ROW_GAP, boxes });
    x += colW + COL_GAP;
  }

  const totalW = x - COL_GAP + SIDE_MARGIN;
  const totalH = Math.max(...columns.map((c) => c.bottom)) + BOTTOM_MARGIN;
  const colIndex = new Map(domainGroups.map((g, i) => [g.id, i]));

  // ---- edges: only between two tables that are both drawn in the diagram
  const edges = [];
  let sameColCounter = {};
  for (const [name, box] of Object.entries(layout)) {
    for (const fk of box.table.fks) {
      const target = layout[fk.ref];
      if (!target) continue; // appendix-only table; see footnote under the diagram
      edges.push({ from: name, to: fk.ref, col: fk.col, color: box.group.color });
    }
  }

  const defs = `
    <defs>
      ${domainGroups.map((g) => `
      <marker id="arrow-${g.id}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
        <path d="M0,0 L10,5 L0,10 z" fill="${g.color}"/>
      </marker>`).join('')}
    </defs>`;

  const edgeSvg = edges.map((e) => {
    const a = layout[e.from];
    const b = layout[e.to];
    const aCol = colIndex.get(a.group.id);
    const bCol = colIndex.get(b.group.id);
    const aRowY = a.y + HEADER_H + (a.rows.findIndex((r) => r.text.includes(`↳ ${e.col}`)) + 0.5) * ROW_H;
    let path;
    if (aCol === bCol) {
      // same column: bow out to the right of both boxes
      const key = a.group.id;
      sameColCounter[key] = (sameColCounter[key] || 0) + 1;
      const bow = 26 + (sameColCounter[key] % 5) * 14;
      const sx = a.x + a.w, sy = aRowY;
      const tx = b.x + b.w, ty = b.headerCenterY;
      path = `M ${sx} ${sy} C ${sx + bow} ${sy}, ${tx + bow} ${ty}, ${tx} ${ty}`;
    } else if (aCol < bCol) {
      const sx = a.x, sy = aRowY; // target is to the left
      const tx = b.x + b.w, ty = b.headerCenterY;
      const midX = (sx + tx) / 2;
      path = `M ${sx} ${sy} C ${midX} ${sy}, ${midX} ${ty}, ${tx} ${ty}`;
    } else {
      const sx = a.x + a.w, sy = aRowY; // target is to the right
      const tx = b.x, ty = b.headerCenterY;
      const midX = (sx + tx) / 2;
      path = `M ${sx} ${sy} C ${midX} ${sy}, ${midX} ${ty}, ${tx} ${ty}`;
    }
    return `<path d="${path}" fill="none" stroke="${e.color}" stroke-width="1.4" opacity="0.55" marker-end="url(#arrow-${a.group.id})"/>`;
  }).join('\n');

  const columnHeaders = columns.map((c) => `
    <rect x="${c.x}" y="${TOP_MARGIN - 46}" width="${c.w}" height="30" rx="5" fill="${c.group.color}"/>
    <text x="${c.x + c.w / 2}" y="${TOP_MARGIN - 26}" text-anchor="middle" font-size="13" font-weight="700" fill="#ffffff" font-family="Segoe UI, Arial, sans-serif">${esc(c.group.label)}</text>`).join('');

  const boxesSvg = Object.entries(layout).map(([name, b]) => {
    const rowsSvg = b.rows.map((r, i) => `
      <text x="${b.x + 10}" y="${b.y + HEADER_H + i * ROW_H + ROW_H - 5}" font-size="10.3" font-family="Consolas, monospace" fill="${r.kind === 'pk' ? '#1a1a2e' : '#3a3a3a'}" font-weight="${r.kind === 'pk' ? '700' : '400'}">${esc(r.text)}</text>`).join('');
    const tenantBadge = b.table.tenant ? `<circle cx="${b.x + b.w - 12}" cy="${b.y + HEADER_H / 2}" r="4" fill="#ffffff" opacity="0.9"/>` : '';
    const footerY = b.y + b.h - 5;
    return `
      <g>
        <rect x="${b.x}" y="${b.y}" width="${b.w}" height="${b.h}" rx="4" fill="#ffffff" stroke="${b.group.color}" stroke-width="1.3"/>
        <rect x="${b.x}" y="${b.y}" width="${b.w}" height="${HEADER_H}" rx="4" fill="${b.group.color}"/>
        <rect x="${b.x}" y="${b.y + HEADER_H - 4}" width="${b.w}" height="4" fill="${b.group.color}"/>
        <text x="${b.x + 10}" y="${b.y + HEADER_H - 8}" font-size="12.5" font-weight="700" fill="#ffffff" font-family="Segoe UI, Arial, sans-serif">${esc(name)}</text>
        ${tenantBadge}
        ${rowsSvg}
        <line x1="${b.x + 6}" y1="${footerY - 12}" x2="${b.x + b.w - 6}" y2="${footerY - 12}" stroke="#e2e2e2"/>
        <text x="${b.x + 10}" y="${footerY}" font-size="9" fill="#8a8a8a" font-family="Segoe UI, Arial, sans-serif">${b.table.cols.length} columns total · since ${b.table.mig}</text>
      </g>`;
  }).join('\n');

  const legend = `
    <g font-family="Segoe UI, Arial, sans-serif">
      <circle cx="${SIDE_MARGIN + 5}" cy="14" r="4" fill="#ffffff" stroke="#333"/>
      <text x="${SIDE_MARGIN + 16}" y="18" font-size="11.5" fill="#333">= tenant-scoped table (carries <tspan font-family="Consolas, monospace">tenant_id</tspan>, every query filters by <tspan font-family="Consolas, monospace">SecurityUtils.currentTenantId()</tspan>)</text>
      <text x="${SIDE_MARGIN + 640}" y="18" font-size="11.5" fill="#333">🔑 = primary key   ↳ = foreign key, arrow points to the referenced (parent) table</text>
    </g>`;

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${totalW} ${totalH}" width="${totalW}" height="${totalH}">
    <rect x="0" y="0" width="${totalW}" height="${totalH}" fill="#fbfbfb"/>
    ${legend}
    ${defs}
    ${edgeSvg}
    ${columnHeaders}
    ${boxesSvg}
  </svg>`;

  return { svg, widthPx: totalW, heightPx: totalH, edgeCount: edges.length, boxCount: Object.keys(layout).length };
}
