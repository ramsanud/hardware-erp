// Small generic flow-chart renderer: fixed-position nodes (box / decision)
// declared by hand (these describe a *process*, not the schema, so there is
// nothing to derive automatically — the shapes are curated from
// architecture.md, security-rules.md and MODULE_DEPENDENCY_MAP.md) and
// arrows between them, laid out on an explicit grid so wrapping is
// predictable at print size.

function esc(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function wrapLines(text, maxCharsPerLine) {
  const words = text.split(' ');
  const lines = [];
  let cur = '';
  for (const w of words) {
    if ((cur + ' ' + w).trim().length > maxCharsPerLine) { lines.push(cur.trim()); cur = w; }
    else cur = (cur + ' ' + w).trim();
  }
  if (cur) lines.push(cur.trim());
  return lines;
}

const SHAPES = {
  start: { fill: '#2e7d32', text: '#fff' },
  step: { fill: '#ffffff', stroke: '#1a6fb0', text: '#1a1a2e' },
  decision: { fill: '#fff7e0', stroke: '#b8860b', text: '#1a1a2e' },
  data: { fill: '#eef2ff', stroke: '#6d5ce8', text: '#1a1a2e' },
  end: { fill: '#c1440e', text: '#fff' },
  note: { fill: 'none', stroke: 'none', text: '#6a6a6a', italic: true },
};

/**
 * nodes: { id, x, y, w, h, label, kind, sub? }
 * edges: { from, to, label?, fromSide?, toSide?, dashed? }
 * lanes: { label, x, w, color } — optional vertical swimlane bands drawn behind everything
 */
export function buildFlowchart({ nodes, edges, lanes = [], width, height, title }) {
  const byId = Object.fromEntries(nodes.map((n) => [n.id, n]));

  const anchor = (n, side) => {
    switch (side) {
      case 'top': return { x: n.x + n.w / 2, y: n.y };
      case 'bottom': return { x: n.x + n.w / 2, y: n.y + n.h };
      case 'left': return { x: n.x, y: n.y + n.h / 2 };
      case 'right': return { x: n.x + n.w, y: n.y + n.h / 2 };
      default: return { x: n.x + n.w / 2, y: n.y + n.h / 2 };
    }
  };

  const laneSvg = lanes.map((l) => `
    <rect x="${l.x}" y="${title ? 56 : 16}" width="${l.w}" height="${height - (title ? 70 : 30)}" fill="${l.color}" opacity="0.35"/>
    <text x="${l.x + l.w / 2}" y="${title ? 46 : 30}" text-anchor="middle" font-size="13" font-weight="700" fill="#555" font-family="Segoe UI, Arial, sans-serif">${esc(l.label)}</text>`).join('');

  const defs = `<defs>
    <marker id="fc-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
      <path d="M0,0 L10,5 L0,10 z" fill="#444"/>
    </marker>
    <marker id="fc-arrow-red" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
      <path d="M0,0 L10,5 L0,10 z" fill="#c1440e"/>
    </marker>
  </defs>`;

  const edgeSvg = edges.map((e) => {
    const a = byId[e.from], b = byId[e.to];
    if (!a || !b) throw new Error(`flowchart edge references unknown node ${e.from} -> ${e.to}`);
    const fromSide = e.fromSide || (b.y >= a.y + a.h ? 'bottom' : b.x >= a.x + a.w ? 'right' : b.x < a.x ? 'left' : 'bottom');
    const toSide = e.toSide || (fromSide === 'bottom' ? 'top' : fromSide === 'right' ? 'left' : fromSide === 'left' ? 'right' : 'bottom');
    const p1 = anchor(a, fromSide), p2 = anchor(b, toSide);
    const color = e.color || '#444';
    const marker = color === '#c1440e' ? 'fc-arrow-red' : 'fc-arrow';
    let path;
    if (fromSide === toSide && (fromSide === 'left' || fromSide === 'right')) {
      const bow = (e.bow || 70) * (fromSide === 'left' ? -1 : 1);
      path = 'M ' + p1.x + ' ' + p1.y + ' C ' + (p1.x + bow) + ' ' + p1.y + ', ' + (p2.x + bow) + ' ' + p2.y + ', ' + p2.x + ' ' + p2.y;
    } else if (fromSide === toSide && (fromSide === 'top' || fromSide === 'bottom')) {
      // same-side hop (e.g. Sales Order -> Invoice over the top of Delivery Challan):
      // arc outward so the line does not run flat along the boxes' edges
      const bow = (e.bow || 70) * (fromSide === 'top' ? -1 : 1);
      path = `M ${p1.x} ${p1.y} C ${p1.x} ${p1.y + bow}, ${p2.x} ${p2.y + bow}, ${p2.x} ${p2.y}`;
    } else if (fromSide === 'bottom' || fromSide === 'top') {
      const midY = (p1.y + p2.y) / 2;
      path = `M ${p1.x} ${p1.y} C ${p1.x} ${midY}, ${p2.x} ${midY}, ${p2.x} ${p2.y}`;
    } else {
      const midX = (p1.x + p2.x) / 2;
      path = `M ${p1.x} ${p1.y} C ${midX} ${p1.y}, ${midX} ${p2.y}, ${p2.x} ${p2.y}`;
    }
    const bowedV = fromSide === toSide && (fromSide === 'top' || fromSide === 'bottom');
    const bowedH = fromSide === toSide && (fromSide === 'left' || fromSide === 'right');
    const lx = (bowedH ? p1.x + (e.bow || 70) * 0.75 * (fromSide === 'left' ? -1 : 1) : (p1.x + p2.x) / 2) + (e.ldx || 0);
    const ly = (bowedV ? p1.y + (e.bow || 70) * 0.75 * (fromSide === 'top' ? -1 : 1) : (p1.y + p2.y) / 2) + (e.ldy || 0);
    const lbl = e.label ? `<rect x="${lx - e.label.length * 3.1 - 4}" y="${ly - 9}" width="${e.label.length * 6.2 + 8}" height="14" fill="#fbfbfb" opacity="0.9"/>
      <text x="${lx}" y="${ly + 2}" text-anchor="middle" font-size="10" fill="${color}" font-family="Segoe UI, Arial, sans-serif">${esc(e.label)}</text>` : '';
    return `<path d="${path}" fill="none" stroke="${color}" stroke-width="1.6" ${e.dashed ? 'stroke-dasharray="5,4"' : ''} marker-end="url(#${marker})"/>${lbl}`;
  }).join('\n');

  const nodeSvg = nodes.map((n) => {
    const s = SHAPES[n.kind] || SHAPES.step;
    const fontSize = n.kind === 'note' ? 10.5 : 11.5;
    const lines = wrapLines(n.label, Math.floor(n.w / (fontSize * 0.56)));
    const subLines = n.sub ? wrapLines(n.sub, Math.floor(n.w / 5.4)) : [];
    const totalTextH = lines.length * (fontSize + 3) + (subLines.length ? subLines.length * 11 + 4 : 0);
    let ty = n.y + n.h / 2 - totalTextH / 2 + fontSize;
    const mainLines = lines.map((l) => {
      const t = `<text x="${n.x + n.w / 2}" y="${ty}" text-anchor="middle" font-size="${fontSize}" font-weight="${n.kind === 'start' || n.kind === 'end' ? '700' : '600'}" fill="${s.text}" font-family="Segoe UI, Arial, sans-serif" ${s.italic ? 'font-style="italic"' : ''}>${esc(l)}</text>`;
      ty += fontSize + 3;
      return t;
    }).join('');
    ty += 3;
    const subSvg = subLines.map((l) => {
      const t = `<text x="${n.x + n.w / 2}" y="${ty}" text-anchor="middle" font-size="9.5" fill="${n.kind === 'start' || n.kind === 'end' ? '#ffe8dc' : '#666'}" font-family="Consolas, monospace">${esc(l)}</text>`;
      ty += 11;
      return t;
    }).join('');

    if (n.kind === 'note') {
      return `<g>${mainLines}${subSvg}</g>`;
    }
    if (n.kind === 'decision') {
      const cx = n.x + n.w / 2, cy = n.y + n.h / 2;
      const points = `${cx},${n.y} ${n.x + n.w},${cy} ${cx},${n.y + n.h} ${n.x},${cy}`;
      return `<g><polygon points="${points}" fill="${s.fill}" stroke="${s.stroke}" stroke-width="1.4"/>${mainLines}${subSvg}</g>`;
    }
    const rx = n.kind === 'start' || n.kind === 'end' ? n.h / 2 : 6;
    return `<g>
      <rect x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}" rx="${rx}" fill="${s.fill}" stroke="${s.stroke || 'none'}" stroke-width="1.4"/>
      ${mainLines}${subSvg}
    </g>`;
  }).join('\n');

  const titleSvg = title ? `<text x="${width / 2}" y="28" text-anchor="middle" font-size="16" font-weight="700" fill="#1a1a2e" font-family="Segoe UI, Arial, sans-serif">${esc(title)}</text>` : '';

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">
    <rect x="0" y="0" width="${width}" height="${height}" fill="#fbfbfb"/>
    ${titleSvg}
    ${laneSvg}
    ${defs}
    ${edgeSvg}
    ${nodeSvg}
  </svg>`;
}
