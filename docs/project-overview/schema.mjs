// Parses the Flyway migrations into { table: { cols, fks, mig } } so the ER
// diagram is drawn from the real schema, never from a hand-typed copy.
import fs from 'node:fs';
import path from 'node:path';

export function parseMigrations(dir) {
  const files = fs.readdirSync(dir).filter((f) => /^V\d+__/.test(f))
    .sort((a, b) => parseInt(a.slice(1)) - parseInt(b.slice(1)));
  const tables = {};
  const ensure = (t, f) => (tables[t] ||= { cols: [], fks: [], mig: f.split('__')[0] });
  for (const f of files) {
    const sql = fs.readFileSync(path.join(dir, f), 'utf8').replace(/--.*$/gm, '');
    let m;
    const create = /CREATE TABLE(?: IF NOT EXISTS)?\s+(\w+)\s*\(([\s\S]*?)\);/gi;
    while ((m = create.exec(sql))) {
      const t = ensure(m[1], f);
      for (const raw of m[2].split(/,\n/)) {
        const l = raw.trim();
        const cm = l.match(/^(\w+)\s+([A-Z]+[^\s,]*)/i);
        const isCol = cm && !/^(CONSTRAINT|PRIMARY|UNIQUE|CHECK|FOREIGN)$/i.test(cm[1]);
        if (isCol) t.cols.push({ name: cm[1], type: cm[2].toUpperCase(), pk: /PRIMARY KEY/i.test(l) });
        const fk = l.match(/(?:FOREIGN KEY\s*\((\w+)\)\s*)?REFERENCES\s+(\w+)/i);
        if (fk) { const col = fk[1] || (isCol ? cm[1] : null); if (col) t.fks.push({ col, ref: fk[2] }); }
      }
      // composite primary keys declared as a constraint line
      const pk = m[2].match(/PRIMARY KEY\s*\(([^)]+)\)/i);
      if (pk) for (const c of pk[1].split(',').map((s) => s.trim())) { const col = t.cols.find((x) => x.name === c); if (col) col.pk = true; }
    }
    const addCol = /ALTER TABLE(?: IF EXISTS)?\s+(\w+)\s+ADD (?:COLUMN )?(?:IF NOT EXISTS )?(\w+)\s+([A-Z]+[^\s,;]*)([^;]*)/gi;
    while ((m = addCol.exec(sql))) {
      const t = ensure(m[1], f);
      if (!t.cols.find((c) => c.name === m[2])) t.cols.push({ name: m[2], type: m[3].toUpperCase(), pk: false, added: f.split('__')[0] });
      const r = m[4].match(/REFERENCES\s+(\w+)/i); if (r) t.fks.push({ col: m[2], ref: r[1] });
    }
    const addFk = /ALTER TABLE(?: IF EXISTS)?\s+(\w+)\s+ADD CONSTRAINT \w+\s+FOREIGN KEY\s*\((\w+)\)\s*REFERENCES\s+(\w+)/gi;
    while ((m = addFk.exec(sql))) ensure(m[1], f).fks.push({ col: m[2], ref: m[3] });
    const drop = /ALTER TABLE(?: IF EXISTS)?\s+(\w+)\s+DROP COLUMN(?: IF EXISTS)?\s+(\w+)/gi;
    while ((m = drop.exec(sql))) { const t = tables[m[1]]; if (t) { t.cols = t.cols.filter((c) => c.name !== m[2]); t.fks = t.fks.filter((k) => k.col !== m[2]); } }
  }
  for (const t of Object.values(tables)) {
    const seen = new Set();
    t.fks = t.fks.filter((k) => k.col && k.col !== 'CONSTRAINT' && t.cols.some((c) => c.name === k.col));
    t.fks = t.fks.filter((k) => { const key = k.col + '>' + k.ref; if (seen.has(key)) return false; seen.add(key); return true; });
    t.tenant = t.cols.some((c) => c.name === 'tenant_id');
  }
  return { tables, migrationCount: files.length, lastMigration: files.at(-1).split('__')[0] };
}
