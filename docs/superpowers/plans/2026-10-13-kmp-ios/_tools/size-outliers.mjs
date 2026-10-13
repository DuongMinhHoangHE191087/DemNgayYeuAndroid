// node size-outliers.mjs -> task rows that look mis-sized (read-only): >8 owned files, `change` >2200 chars, size-S with >4 files or >1200 chars.
// Entry format: id(<files>f,<change chars>c,<size>,<host>). A pure move/rename of many files is fine as one task; logic spread over many files is not.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const clean = (s) => s.replace(/[`*]/g, '').replace(/<br\s*\/?>/gi, ' ').trim();
const cells = (l) => l.replace(/\\\|/g, '\u0001').replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map((c) => c.replace(/\u0001/g, '|').trim());
const rows = [];
for (const f of fs.readdirSync(root).filter((n) => /^p\d-.*\.md$/.test(n)).sort()) {
  const L = fs.readFileSync(path.join(root, f), 'utf8').split(/\r?\n/);
  for (let i = 0; i < L.length; i++) {
    if (!L[i].trim().startsWith('|')) continue;
    const h = cells(L[i]).map((c) => clean(c).toLowerCase());
    if (h[0] !== 'id' || !h.some((x) => /^depend/.test(x)) || !h.some((x) => /^size/.test(x))) continue;
    const ix = (re) => h.findIndex((x) => re.test(x));
    const I = { files: ix(/^files/), change: ix(/^change/), size: ix(/^size/), host: ix(/^host/) };
    for (i += 2; i < L.length && L[i].trim().startsWith('|'); i++) {
      const c = cells(L[i]);
      if (c.length < h.length) continue;
      const files = c[I.files].replace(/shared-file:/gi, ' ').split(/[,;]|<br\s*\/?>/i).map((s) => s.replace(/`/g, '').trim()).filter(Boolean);
      rows.push({ id: clean(c[0]), nfiles: files.length, chg: c[I.change].length, size: clean(c[I.size]).split(/[\s(]/)[0], host: clean(c[I.host]) });
    }
    i--;
  }
}
const fmt = (r) => `${r.id}(${r.nfiles}f,${r.chg}c,${r.size},${r.host})`;
const show = (label, arr, key) => console.log(`${label} (${arr.length}): ${arr.sort((a, b) => b[key] - a[key]).map(fmt).join(' ')}`);
console.log(`rows=${rows.length}`);
show('files>8', rows.filter((r) => r.nfiles > 8), 'nfiles');
show('change>2200 chars', rows.filter((r) => r.chg > 2200), 'chg');
show('size-S with >4 files or >1200 chars', rows.filter((r) => r.size === 'S' && (r.nfiles > 4 || r.chg > 1200)), 'nfiles');
